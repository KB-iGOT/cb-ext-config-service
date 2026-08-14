package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheServiceTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "cacheTtl", 60000L);
    }

    private void withPooledJedis() {
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    void putCache_shouldWriteWithDefaultTtl() throws Exception {
        withPooledJedis();
        Map<String, Object> data = Map.of("name", "test");
        when(objectMapper.writeValueAsString(data)).thenReturn("{\"name\":\"test\"}");

        cacheService.putCache("testKey", data);

        verify(jedis).setex("testKey", 60000L, "{\"name\":\"test\"}");
    }

    @Test
    void putCache_shouldWriteWithExplicitTtl() throws Exception {
        withPooledJedis();
        List<String> designations = List.of("Officer");
        when(objectMapper.writeValueAsString(designations)).thenReturn("[\"Officer\"]");

        cacheService.putCache("user.designations.u1", designations, 300L);

        verify(jedis).setex("user.designations.u1", 300L, "[\"Officer\"]");
    }

    @Test
    void putCache_shouldHandleSerializationFailure() throws Exception {
        withPooledJedis();
        Map<String, Object> data = Map.of("name", "test");
        when(objectMapper.writeValueAsString(data)).thenThrow(new JsonProcessingException("JSON error") {
        });

        assertDoesNotThrow(() -> cacheService.putCache("testKey", data));
        verify(jedis, never()).setex(anyString(), any(Long.class), anyString());
    }

    @Test
    void getCache_shouldReturnCachedData() {
        withPooledJedis();
        when(jedis.get("testKey")).thenReturn("{\"name\":\"test\"}");

        assertEquals("{\"name\":\"test\"}", cacheService.getCache("testKey"));
    }

    @Test
    void getCache_shouldReturnNullWhenRedisFails() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis error"));

        assertNull(cacheService.getCache("testKey"));
    }

    @Test
    void publishInvalidate_shouldPublishOnInvalidationChannel() {
        withPooledJedis();

        cacheService.publishInvalidate();

        verify(jedis).publish(Constants.FORM_CONFIG_INVALIDATE_CHANNEL, Constants.RELOAD);
    }

    @Test
    void publishInvalidate_shouldSwallowRedisFailure() {
        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> cacheService.publishInvalidate());
    }

    @Test
    void deleteCacheByKey_shouldReturnTrueWhenKeyRemoved() {
        withPooledJedis();
        when(jedis.del("testKey")).thenReturn(1L);

        assertTrue(cacheService.deleteCache("testKey"));
    }

    @Test
    void deleteCacheByKey_shouldReturnFalseWhenKeyAbsent() {
        withPooledJedis();
        when(jedis.del("testKey")).thenReturn(0L);

        assertFalse(cacheService.deleteCache("testKey"));
    }

    @Test
    void deleteCacheByPattern_shouldScanAndDeleteMatchingKeys() {
        withPooledJedis();
        ScanResult<String> page = new ScanResult<>(ScanParams.SCAN_POINTER_START,
                List.of("form.config.result.a", "form.config.result.b"));
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(page);
        when(jedis.del(any(String[].class))).thenReturn(2L);

        assertEquals(2L, cacheService.deleteCacheByPattern("form.config.result*"));
        verify(jedis).del(new String[]{"form.config.result.a", "form.config.result.b"});
    }

    @Test
    void deleteCacheByPattern_shouldNotCallDeleteWhenNoMatches() {
        withPooledJedis();
        ScanResult<String> page = new ScanResult<>(ScanParams.SCAN_POINTER_START, Collections.emptyList());
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(page);

        assertEquals(0L, cacheService.deleteCacheByPattern("form.config.result*"));
        verify(jedis, never()).del(any(String[].class));
    }

    @Test
    void deleteCache_shouldClearFormConfigKeysAndPublishInvalidation() {
        withPooledJedis();
        ScanResult<String> page = new ScanResult<>(ScanParams.SCAN_POINTER_START, List.of("form.config.result.a"));
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(page);
        when(jedis.del(any(String[].class))).thenReturn(1L);

        ApiResponse response = cacheService.deleteCache();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFUL, response.getParams().getStatus());
        verify(jedis).publish(eq(Constants.FORM_CONFIG_INVALIDATE_CHANNEL), eq(Constants.RELOAD));
    }
}
