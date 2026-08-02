package com.igot.cb.formConfiguration.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.service.cache.CacheService;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public FormConfigRuleEngine(List<FormConfigLookupRule> rules, FormConfigRuleProperties ruleProperties,
                                 CacheService cacheService, ObjectMapper objectMapper) {
        this.orderedRules = rules.stream()
                .sorted(Comparator.comparingInt((FormConfigLookupRule rule) -> scoreOf(rule, ruleProperties)).reversed())
                .toList();
        this.cacheService = cacheService;
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

            String cached = cacheService.getCache(cacheKey);
            if (cached != null) {
                try {
                    return Optional.of(objectMapper.readValue(cached, FormConfigurationEntity.class));
                } catch (Exception e) {
                    log.error("FormConfigRuleEngine: failed to deserialize cached entity for key {}: {}", cacheKey, e.getMessage());
                }
            }

            Optional<FormConfigurationEntity> found = rule.find(ctx);
            if (found.isPresent()) {
                cacheService.putCache(cacheKey, found.get());
                return found;
            }
        }
        return Optional.empty();
    }
}
