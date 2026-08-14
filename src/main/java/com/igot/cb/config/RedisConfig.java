package com.igot.cb.config;

import java.time.Duration;

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

    /**
     * Creates a JedisPool bean for Redis connection pooling.
     * It sets the pool configurations and connects to the Redis server using host and port from properties.
     *
     * @return JedisPool instance configured with Redis settings.
     */
    @Bean(name = "jedisPool", destroyMethod = "close")
    public JedisPool jedisPool() {
        System.setProperty("org.apache.commons.pool2.registerMbeans", "false");
        log.info("Initialising JedisPool for redis {}:{}", redisHost, redisPort);
        return new JedisPool(buildPoolConfig(), redisHost, redisPort, redisTimeoutMillis);
    }

    private JedisPoolConfig buildPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(64);
        poolConfig.setMinIdle(16);
        // Redis is off the read hot path (see FormConfigCache), so validate lazily
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
