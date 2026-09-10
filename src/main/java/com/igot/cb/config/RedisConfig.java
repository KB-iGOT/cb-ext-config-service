package com.igot.cb.config;

import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Configuration class for Redis connection pool.
 * It sets up the JedisPool with specified configurations and properties.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    @Value("${IGOT_REDIS_HOST:localhost}")
    private String redisHost;

    @Value("${IGOT_REDIS_PORT:6379}")
    private int redisPort;

    @Value("${redis.timeout:2000}")
    private int redisTimeoutMillis;

    @Value("${redis.password.required:false}")
    private boolean redisPasswordRequired;

    @Value("${redis.username:}")
    private String redisUsername;

    @Value("${redis.password:}")
    private String redisPassword;

    /**
     * Creates a JedisPool bean for Redis connection pooling.
     * It sets the pool configurations and connects to the Redis server using host and port from properties.
     *
     * <p>Everything on this service reaches Redis through this one pool - {@code CacheService} and the
     * pub/sub {@code FormConfigCacheSubscriber} both take it by injection - so authenticating here
     * authenticates the whole service.
     *
     * @return JedisPool instance configured with Redis settings.
     */
    @Bean(name = "jedisPool", destroyMethod = "close")
    public JedisPool jedisPool() {
        System.setProperty("org.apache.commons.pool2.registerMbeans", "false");

        if (!redisPasswordRequired) {
            log.warn("Initialising JedisPool for redis {}:{} WITHOUT authentication - if that server has requirepass set, every operation will fail with NOAUTH and be swallowed as a cache miss", redisHost, redisPort);
            return new JedisPool(buildPoolConfig(), redisHost, redisPort, redisTimeoutMillis);
        }
        if (StringUtils.isBlank(redisUsername)) {
            throw new IllegalStateException("A username is required for the Redis instance at " + redisHost + ":" + redisPort + " but not configured");
        }
        if (StringUtils.isBlank(redisPassword)) {
            throw new IllegalStateException("A password is required for the Redis instance at " + redisHost + ":" + redisPort + " but not configured");
        }
        // The username is safe to log and is what makes an ACL misconfiguration diagnosable from the
        // startup line alone; the password must never appear here.
        log.info("Initialising JedisPool for redis {}:{} with authentication enabled for user '{}'", redisHost, redisPort, redisUsername);
        return new JedisPool(buildPoolConfig(), redisHost, redisPort, redisTimeoutMillis, redisUsername, redisPassword);
    }

    private JedisPoolConfig buildPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(64);
        poolConfig.setMinIdle(16);
        // An in-JVM L1 sits in front of Redis (see FormConfigLocalCache), so validate lazily
        // rather than paying a PING round trip on every borrow.
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(2));
        return poolConfig;
    }
}
