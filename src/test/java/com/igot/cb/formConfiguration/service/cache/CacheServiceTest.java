package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "cacheTtl", 60000L);
    }

    @Test
    void putCache_shouldSaveDataInRedis() throws Exception {
        String key = "testKey";
        Map<String, Object> data = Map.of("name", "test");

        when(objectMapper.writeValueAsString(data)).thenReturn("{\"name\":\"test\"}");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.putCache(key, data);

        verify(valueOperations).set(
                eq(key),
                eq("{\"name\":\"test\"}"),
                eq(60000L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void putCache_shouldHandleException() throws Exception {
        String key = "testKey";
        Map<String, Object> data = Map.of("name", "test");

        when(objectMapper.writeValueAsString(data))
                .thenThrow(new JsonProcessingException("JSON error") {});

        assertDoesNotThrow(() -> cacheService.putCache(key, data));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void getCache_shouldReturnCachedData() {
        String key = "testKey";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("{\"name\":\"test\"}");

        String result = cacheService.getCache(key);

        assertEquals("{\"name\":\"test\"}", result);
    }

    @Test
    void getCache_shouldReturnNullWhenExceptionOccurs() {
        String key = "testKey";

        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis error"));

        String result = cacheService.getCache(key);

        assertNull(result);
    }

    @Test
    void deleteCacheByKey_shouldReturnTrue() {
        String key = "testKey";

        when(redisTemplate.delete(key)).thenReturn(true);

        boolean result = cacheService.deleteCache(key);

        assertTrue(result);
        verify(redisTemplate).delete(key);
    }

    @Test
    void deleteCache_shouldReturnOkWhenKeyDeleted() {
        when(redisTemplate.delete(Constants.FORM_CONFIG_RESULT)).thenReturn(true);

        ApiResponse response = cacheService.deleteCache();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFUL, response.getParams().getStatus());
    }

    @Test
    void deleteCache_shouldReturnNullWhenKeyNotFound() {
        when(redisTemplate.delete(Constants.FORM_CONFIG_RESULT)).thenReturn(false);

        ApiResponse response = cacheService.deleteCache();

        assertNull(response);
    }

    @Test
    void deleteCacheByPattern_shouldDeleteMatchingKeys() {
        String pattern = "testPattern*";
        java.util.Set<String> mockKeys = java.util.Set.of("testPattern1", "testPattern2");

        when(redisTemplate.keys(pattern)).thenReturn(mockKeys);

        cacheService.deleteCacheByPattern(pattern);

        verify(redisTemplate).keys(pattern);
        verify(redisTemplate).delete(mockKeys);
    }
}
