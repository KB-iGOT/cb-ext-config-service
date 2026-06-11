package com.igot.cb.formConfiguration.controller;

import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisCacheController {

    @Autowired
    CacheService redisCacheService;


    @DeleteMapping("/redis")
    public ResponseEntity<ApiResponse> deleteCache() throws Exception {
        ApiResponse response = redisCacheService.deleteCache();
        return new ResponseEntity<>(response, response.getResponseCode());
    }

}
