package com.igot.cb.formConfiguration.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Tier-1 invalidation across pods. After a create/update the writing pod clears Redis (tier 2) and
 * publishes an eviction on a Redis channel; every pod — the publisher included — subscribes and
 * evicts its local Caffeine entries. Evict-only: pods lazily reload from Redis/DB on the next read,
 * so message ordering and payload size never matter.
 *
 * Wire format: {@code CATALOG} (a form was created — only the catalogue is stale) or
 * {@code FORM:<id>} (a form was updated — that form and the catalogue are stale).
 */
@Component
@Slf4j
public class FormConfigEvictionBroadcaster {

    private static final String MSG_CATALOG = "CATALOG";
    private static final String MSG_FORM_PREFIX = "FORM:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private FormConfigReadCache readCache;

    @Value("${form.config.local-cache.evict-channel:form-config-evict}")
    private String channel;

    /** A form was created: the catalogue is stale on every pod. Call after the DB write and the Redis delete. */
    public void formCreated() {
        readCache.evictCatalog();
        publish(MSG_CATALOG);
    }

    /** A form was updated: that form and the catalogue are stale on every pod. Call after the DB write and the Redis delete. */
    public void formUpdated(long formId) {
        readCache.evictCatalog();
        readCache.evictForm(formId);
        publish(MSG_FORM_PREFIX + formId);
    }

    private void publish(String message) {
        try {
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            log.error("FormConfigEvictionBroadcaster: failed to publish '{}' (other pods rely on TTL): {}", message, e.getMessage());
        }
    }

    private void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        if (MSG_CATALOG.equals(body)) {
            readCache.evictCatalogFromMemory();
            log.info("FormConfigEvictionBroadcaster: evicted local catalogue");
            return;
        }
        if (body.startsWith(MSG_FORM_PREFIX)) {
            try {
                long formId = Long.parseLong(body.substring(MSG_FORM_PREFIX.length()));
                readCache.evictCatalogFromMemory();
                readCache.evictForm(formId);
                log.info("FormConfigEvictionBroadcaster: evicted local catalogue and form {}", formId);
                return;
            } catch (NumberFormatException ignored) {
                // falls through to the warning below
            }
        }
        log.warn("FormConfigEvictionBroadcaster: ignoring malformed eviction message '{}'", body);
    }

    @Bean
    public RedisMessageListenerContainer formConfigEvictionListener(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this::onMessage, new ChannelTopic(channel));
        return container;
    }
}
