
package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.redis.cacheTtl}")
    private long cacheTtl;


    public void putCache(String key, Object object) {
        try {
            String data = objectMapper.writeValueAsString(object);
            redisTemplate.opsForValue().set(key, data, cacheTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error while putting data in Redis cache: {} ", e.getMessage());
        }
    }

    public String getCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error while getting data from Redis cache: {} ", e.getMessage());
            return null;
        }
    }

    public ApiResponse deleteCache() {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_REDIS_DELETE);
        boolean result = deleteCache(Constants.FORM_CONFIG_RESULT);
        if (result) {
            log.info("Field deleted successfully from key {}.", Constants.FORM_CONFIG_RESULT);
            response.getParams().setStatus(Constants.SUCCESSFUL);
            response.setResponseCode(HttpStatus.OK);
            return response;
        } else {
            log.warn("Field not found in key {}.", Constants.FORM_CONFIG_RESULT);
            response.getParams().setErrMsg(Constants.ERROR_REDIS_KEY_NOTFOUND);
            response.setResponseCode(HttpStatus.NOT_FOUND);
        }
        return null;
    }


    public boolean deleteCache(String key) {
        return redisTemplate.delete(key);
    }
}
