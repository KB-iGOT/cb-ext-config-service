package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;

@Component
@Slf4j
public class CacheService {

    @Autowired
    private JedisPool jedisPool;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${formconfig.redisCache.ttlSeconds}")
    private long cacheTtl;

    public void putCache(String key, Object object) {
        putCache(key, object, cacheTtl);
    }

    /**
     * Stores a value with an explicit TTL. Used for entries that must not inherit the long default
     * (e.g. per-user profile data, which goes stale far sooner than form configuration does).
     */
    public void putCache(String key, Object object, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, ttlSeconds, objectMapper.writeValueAsString(object));
        } catch (Exception e) {
            log.error("Error while putting data in Redis cache: {} ", e.getMessage());
        }
    }

    public String getCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            log.error("Error while getting data from Redis cache: {} ", e.getMessage());
            return null;
        }
    }

    /**
     * Publishes a form configuration invalidation message so every pod (including this one) drops
     * its in-JVM {@link FormConfigLocalCache} entries.
     */
    public void publishInvalidate() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(Constants.FORM_CONFIG_INVALIDATE_CHANNEL, Constants.RELOAD);
        } catch (Exception e) {
            log.error("Error while publishing form config invalidation: {}", e.getMessage(), e);
        }
    }

    public ApiResponse deleteCache() {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_REDIS_DELETE);
        long deleted = deleteCacheByPattern(Constants.FORM_CONFIG_RESULT + "*");
        publishInvalidate();
        log.info("Deleted {} form config keys and published invalidation.", deleted);
        response.getParams().setStatus(Constants.SUCCESSFUL);
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    public boolean deleteCache(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.del(key) > 0;
        } catch (Exception e) {
            log.error("Error while deleting key {} from Redis: {}", key, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Deletes keys matching a glob pattern using SCAN. KEYS is deliberately avoided because it
     * blocks the Redis single thread for the duration of a full keyspace walk.
     *
     * @return number of keys deleted.
     */
    public long deleteCacheByPattern(String pattern) {
        long deleted = 0;
        try (Jedis jedis = jedisPool.getResource()) {
            ScanParams params = new ScanParams().match(pattern).count(500);
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                List<String> keys = scan.getResult();
                if (!keys.isEmpty()) {
                    deleted += jedis.del(keys.toArray(new String[0]));
                }
                cursor = scan.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            log.info("Deleted {} keys matching pattern: {}", deleted, pattern);
        } catch (Exception e) {
            log.error("Error while deleting keys by pattern {}: {}", pattern, e.getMessage(), e);
        }
        return deleted;
    }
}
