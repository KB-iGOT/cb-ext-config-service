package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.service.Validation.ValidationService;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormsConfigurationServiceImplTest {

    @InjectMocks
    private FormsConfigurationServiceImpl service;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private FormConfigurationRepository repository;

    @Mock
    private ValidationService validationService;

    @Mock
    private CacheService cacheService;

    @Mock
    private ObjectMapper objectMapper;

    private UserDetails userDetails;

    @BeforeEach
    void setup() {
        userDetails = new UserDetails();
        userDetails.setUserId("user1");
        userDetails.setOrg("org1");
        userDetails.setUserRoles(List.of("PUBLIC"));
    }

    private Map<String, Object> getRequest() {
        Map<String, Object> data = new HashMap<>();
        data.put("field", "value");

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("rootOrg", "org1");
        criteria.put("role", "PUBLIC");

        Map<String, Object> req = new HashMap<>();
        req.put(Constants.TYPE, "page");
        req.put(Constants.SUBTYPE, "player test");
        req.put(Constants.PORTAL, "mobile");
        req.put(Constants.CLIENT_VERSION, 1.0);
        req.put(Constants.DATA, data);
        req.put(Constants.CRITERIA, criteria);

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put(Constants.Parameters.REQUEST, req);
        return wrapper;
    }

    private FormConfigurationEntity entity() {
        FormConfigurationEntity entity = new FormConfigurationEntity();
        entity.setType("page");
        entity.setSubtype("player test");
        entity.setPortal("mobile");
        entity.setData(mock(com.fasterxml.jackson.databind.JsonNode.class));
        entity.setCreatedAt("now");
        return entity;
    }

    @Test
    void createFormConfig_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.CREATE))).thenReturn(Constants.SUCCESSFUL);
        when(validationService.validateFormData(anyMap())).thenReturn(null);
        when(objectMapper.valueToTree(any())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(Map.of("field", "value"));

        ApiResponse response = service.createFormConfig(getRequest(), "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService).putCache(anyString(), any());
    }

    @Test
    void readFormConfig_roleOrgCacheHit_volunteer() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(cacheService.getCache(anyString())).thenReturn("{}");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository, never()).getFormConfigDataByCriteria(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readFormConfig_roleOrgDbHit_volunteer() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.of(entity()));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService, atLeastOnce()).putCache(anyString(), any());
    }

    @Test
    void readFormConfig_roleWildcardCacheHit_public() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(cacheService.getCache(anyString())).thenReturn(null).thenReturn("{}");
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.empty());
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void readFormConfig_roleWildcardDbHit_public() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("*"), any(), any()))
                .thenReturn(Optional.of(entity()));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService, atLeastOnce()).putCache(anyString(), any());
    }

    @Test
    void readFormConfig_adminUsesPayloadCriteria() {
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.empty());

        ApiResponse response = service.readFormConfig(getRequest(), "admin1", "ignored-org", List.of("IGNORED"), true);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        verify(repository).getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any());
    }

    @Test
    void updateFormConfig_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.UPDATE))).thenReturn(Constants.SUCCESSFUL);
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.of(entity()));
        when(objectMapper.valueToTree(any())).thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.updateFormConfig(getRequest(), "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService).putCache(anyString(), any());
    }
}

