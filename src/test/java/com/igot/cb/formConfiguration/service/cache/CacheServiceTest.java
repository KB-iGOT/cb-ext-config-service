package com.igot.cb.formConfiguration.service.cache;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private void withPooledJedis() {
        when(jedisPool.getResource()).thenReturn(jedis);
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

        cacheService.publishInvalidate();
    }

    @Test
    void deleteCacheByKey_shouldReturnTrueWhenKeyRemoved() {
        withPooledJedis();
        when(jedis.del("testKey")).thenReturn(1L);

        assertTrue(cacheService.deleteCache("testKey"));
        verify(jedis).del("testKey");
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
        ScanResult<String> page = new ScanResult<>(ScanParams.SCAN_POINTER_START,
                List.of("form.config.result.a"));
        when(jedis.scan(anyString(), any(ScanParams.class))).thenReturn(page);
        when(jedis.del(any(String[].class))).thenReturn(1L);

        ApiResponse response = cacheService.deleteCache();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFUL, response.getParams().getStatus());
        verify(jedis).publish(Constants.FORM_CONFIG_INVALIDATE_CHANNEL, Constants.RELOAD);
    }
}
