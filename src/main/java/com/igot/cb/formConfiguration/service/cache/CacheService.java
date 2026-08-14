package com.igot.cb.formConfiguration.service.cache;

import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;

@Service
@Slf4j
public class CacheService {

    @Autowired
    private JedisPool jedisPool;

    /**
     * Publishes a form configuration invalidation message so that every pod
     * (including this one) reloads its in-JVM {@link FormConfigCache}.
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
     * Deletes keys matching a glob pattern using SCAN. KEYS is deliberately avoided
     * because it blocks the Redis single thread for the whole keyspace.
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
