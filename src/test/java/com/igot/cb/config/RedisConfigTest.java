package com.igot.cb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConfigTest {

    private RedisConfig redisConfig;

    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        // The host/port/timeout fields are @Value-injected, so set them the way the rest of this
        // module's tests set @Value fields.
        ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);
        ReflectionTestUtils.setField(redisConfig, "redisTimeoutMillis", 2000);
    }

    /**
     * With the flag on, a missing username must fail at bean creation rather than on the first Redis
     * call. An unset property reads as "" rather than null, and Jedis sends the two-argument AUTH
     * whenever the username is non-null - so without this guard the server answers WRONGPASS on every
     * command and CacheService swallows it as a miss.
     */
    @Test
    void testJedisPoolFailsWhenUsernameRequiredButMissing() {
        ReflectionTestUtils.setField(redisConfig, "redisPasswordRequired", true);
        ReflectionTestUtils.setField(redisConfig, "redisUsername", "");
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "cache-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> redisConfig.jedisPool());
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
        assertTrue(ex.getMessage().contains("localhost:6379"), ex.getMessage());
    }

    @Test
    void testJedisPoolFailsWhenPasswordRequiredButMissing() {
        ReflectionTestUtils.setField(redisConfig, "redisPasswordRequired", true);
        ReflectionTestUtils.setField(redisConfig, "redisUsername", "cache-user");
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "  ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> redisConfig.jedisPool());
        assertTrue(ex.getMessage().contains("password"), ex.getMessage());
        assertTrue(ex.getMessage().contains("localhost:6379"), ex.getMessage());
    }

    /**
     * An absent key reads as null rather than "". Both spellings of "not configured" are rejected.
     */
    @Test
    void testJedisPoolFailsWhenUsernameIsNull() {
        ReflectionTestUtils.setField(redisConfig, "redisPasswordRequired", true);
        ReflectionTestUtils.setField(redisConfig, "redisUsername", null);
        ReflectionTestUtils.setField(redisConfig, "redisPassword", "cache-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> redisConfig.jedisPool());
        assertTrue(ex.getMessage().contains("username"), ex.getMessage());
    }
}
