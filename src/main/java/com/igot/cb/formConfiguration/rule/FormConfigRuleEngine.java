package com.igot.cb.formConfiguration.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.formConfiguration.service.cache.FormConfigLocalCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks which {@link FormConfigLookupRule} answers a request (Strategy pattern — one strategy per
 * matching dimension) and applies a cache-aside read through Redis for whichever rule ends up
 * answering (Chain-of-Responsibility-style short circuit: highest priority first, first match
 * wins). Priority is looked up dynamically from {@link FormConfigRuleProperties} by
 * {@link FormConfigLookupRule#ruleName()} rather than hardcoded per rule — a rule missing from
 * config defaults to priority 0 (tried last) with a warning, instead of failing startup.
 */
@Service
@Slf4j
public class FormConfigRuleEngine {

    private static final int DEFAULT_SCORE = 0;

    private final List<FormConfigLookupRule> orderedRules;
    private final CacheService cacheService;
    private final FormConfigLocalCache localCache;
    private final ObjectMapper objectMapper;

    @Autowired
    public FormConfigRuleEngine(List<FormConfigLookupRule> rules, FormConfigRuleProperties ruleProperties,
                                 CacheService cacheService, FormConfigLocalCache localCache,
                                 ObjectMapper objectMapper) {
        this.orderedRules = rules.stream()
                .sorted(Comparator.comparingInt((FormConfigLookupRule rule) -> scoreOf(rule, ruleProperties)).reversed())
                .toList();
        this.cacheService = cacheService;
        this.localCache = localCache;
        this.objectMapper = objectMapper;
    }

    private static int scoreOf(FormConfigLookupRule rule, FormConfigRuleProperties ruleProperties) {
        Integer score = ruleProperties.getScores().get(rule.ruleName());
        if (score == null) {
            log.warn("FormConfigRuleEngine: no configured score for rule '{}' (form.config.rule.scores.{}) — defaulting to {}",
                    rule.ruleName(), rule.ruleName(), DEFAULT_SCORE);
            return DEFAULT_SCORE;
        }
        return score;
    }

    public Optional<FormConfigurationEntity> resolve(FormConfigResolutionContext ctx) {
        for (FormConfigLookupRule rule : orderedRules) {
            if (!rule.supports(ctx)) {
                continue;
            }

            String cacheKey = rule.buildCacheKey(ctx);
            log.info("cacheKey {}: ", cacheKey);
            // L1 (in-JVM) first, then L2 (Redis). Both hold the same serialized value under the same
            // key, so an L1 hit is byte-identical to an L2 hit and just skips the network round trip.
            boolean fromRedis = false;
            String cached = localCache.get(cacheKey);
            if (cached == null) {
                cached = cacheService.getCache(cacheKey);
                fromRedis = cached != null;
            }
            if (cached != null) {
                try {
                    FormConfigurationEntity entity = objectMapper.readValue(cached, FormConfigurationEntity.class);
                    if (fromRedis) {
                        localCache.put(cacheKey, cached);
                    }
                    return Optional.of(entity);
                } catch (Exception e) {
                    log.error("FormConfigRuleEngine: failed to deserialize cached entity for key {}: {}", cacheKey, e.getMessage());
                }
            }

            Optional<FormConfigurationEntity> found = rule.find(ctx);
            if (found.isPresent()) {
                cacheService.putCache(cacheKey, found.get());
                try {
                    localCache.put(cacheKey, objectMapper.writeValueAsString(found.get()));
                } catch (Exception e) {
                    log.warn("FormConfigRuleEngine: failed to populate local cache for key {}: {}", cacheKey, e.getMessage());
                }
                return found;
            }
        }
        return Optional.empty();
    }
}
