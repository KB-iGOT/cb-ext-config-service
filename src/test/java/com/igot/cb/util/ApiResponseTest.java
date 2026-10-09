package com.igot.cb.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void defaultConstructor_setsVersionTimestampAndParams() {
        ApiResponse response = new ApiResponse();

        assertEquals("v1", response.getVer());
        assertNotNull(response.getTs());
        assertNotNull(response.getParams());
        assertNotNull(response.getParams().getResMsgId());
    }

    @Test
    void idConstructor_setsIdAndDefaults() {
        ApiResponse response = new ApiResponse("resp-id");

        assertEquals("resp-id", response.getId());
        assertEquals("v1", response.getVer());
    }

    @Test
    void putAndGet_storesAndRetrievesValue() {
        ApiResponse response = new ApiResponse();

        response.put("key1", "value1");

        assertEquals("value1", response.get("key1"));
        assertTrue(response.containsKey("key1"));
        assertFalse(response.containsKey("missingKey"));
    }

    @Test
    void putAll_mergesMapIntoResult() {
        ApiResponse response = new ApiResponse();

        response.putAll(Map.of("a", 1, "b", 2));

        assertEquals(1, response.get("a"));
        assertEquals(2, response.get("b"));
    }

    @Test
    void settersAndGetters_roundTrip() {
        ApiResponse response = new ApiResponse();

        response.setMessage("a message");
        response.setResponseCode(HttpStatus.OK);
        ApiRespParam param = new ApiRespParam("id1");
        response.setParams(param);

        assertEquals("a message", response.getMessage());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(param, response.getParams());
    }
}
