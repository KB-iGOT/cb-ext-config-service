package com.igot.cb.formConfiguration.service.cache;

import com.igot.cb.util.Constants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Listens for form configuration invalidation messages and refreshes the in-JVM
 * {@link FormConfigCache}. This is what keeps every pod consistent after a write
 * on any single pod.
 */
@Component
@Slf4j
public class FormConfigCacheSubscriber {

    private static final long RETRY_DELAY_MILLIS = 5000L;

    private final JedisPool jedisPool;
    private final FormConfigCache formConfigCache;

    private volatile boolean running = true;
    private volatile JedisPubSub subscriber;
    private Thread listenerThread;

    public FormConfigCacheSubscriber(JedisPool jedisPool, FormConfigCache formConfigCache) {
        this.jedisPool = jedisPool;
        this.formConfigCache = formConfigCache;
    }

    @PostConstruct
    public void start() {
        listenerThread = new Thread(this::listen, "form-config-cache-subscriber");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listen() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                log.info("Form config invalidation received on channel {}: {}", channel, message);
                formConfigCache.reload();
            }
        };
        while (running) {
            try (Jedis jedis = jedisPool.getResource()) {
                // Blocks until unsubscribed or the connection drops.
                jedis.subscribe(subscriber, Constants.FORM_CONFIG_INVALIDATE_CHANNEL);
            } catch (Exception e) {
                if (running) {
                    log.error("Form config invalidation subscriber disconnected, retrying in {}ms",
                            RETRY_DELAY_MILLIS, e);
                }
            }
            if (running) {
                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (subscriber != null && subscriber.isSubscribed()) {
                subscriber.unsubscribe();
            }
        } catch (Exception e) {
            log.warn("Error while unsubscribing form config invalidation listener: {}", e.getMessage());
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}
