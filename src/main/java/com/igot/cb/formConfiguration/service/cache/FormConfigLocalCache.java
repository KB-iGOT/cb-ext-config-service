package com.igot.cb.formConfiguration.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * In-JVM L1 sitting in front of the Redis cache-aside in
 * {@link com.igot.cb.formConfiguration.rule.FormConfigRuleEngine}.
 * <p>
 * Holds the same serialized value Redis holds, under the same per-rule cache key, so an L1 hit is
 * byte-identical to an L2 hit and simply saves the network round trip. Entries are dropped on a
 * write (via the Redis pub/sub invalidation message — see {@link FormConfigCacheSubscriber}) and
 * expire on their own TTL as a backstop, so a missed message self-heals rather than serving stale
 * configuration indefinitely.
 */
@Component
@Slf4j
public class FormConfigLocalCache {

    private final Cache<String, String> cache;

    public FormConfigLocalCache(@Value("${formconfig.localCache.maxSize:2000}") long maxSize,
                                @Value("${formconfig.localCache.ttlSeconds:300}") long ttlSeconds) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .build();
        log.info("FormConfigLocalCache initialised (maxSize={}, ttlSeconds={})", maxSize, ttlSeconds);
    }

    public String get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public void invalidateAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        log.info("FormConfigLocalCache invalidated ({} entries dropped)", size);
    }
}
