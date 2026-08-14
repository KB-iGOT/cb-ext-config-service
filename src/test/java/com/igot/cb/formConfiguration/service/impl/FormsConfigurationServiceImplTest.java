package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.service.Validation.ValidationService;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.formConfiguration.service.cache.FormConfigCache;
import com.igot.cb.formConfiguration.service.cache.FormConfigCache.CachedFormConfig;
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
    private FormConfigCache formConfigCache;

    @Mock
    private ObjectMapper objectMapper;

    private UserDetails userDetails;

    private static final String ORG_KEY =
            FormConfigCache.cacheKey("page", "player test", "mobile", "org1", "PUBLIC", 1.0d);
    private static final String WILDCARD_KEY =
            FormConfigCache.cacheKey("page", "player test", "mobile", "*", "PUBLIC", 1.0d);
    private static final CachedFormConfig CACHED =
            new CachedFormConfig(Map.of(Constants.NAME, "testName"), "now");

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
        req.put(Constants.NAME, "testName");
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
        entity.setName("testName");
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
        verify(formConfigCache).reload();
        verify(cacheService).publishInvalidate();
    }

    @Test
    void readFormConfig_orgScopedLocalCacheHit_volunteer() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(formConfigCache.isLoaded()).thenReturn(true);
        when(formConfigCache.get(ORG_KEY)).thenReturn(CACHED);

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(CACHED.result(), response.getResult());
        verify(repository, never()).getFormConfigDataByCriteria(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readFormConfig_wildcardLocalCacheHit_public() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(formConfigCache.isLoaded()).thenReturn(true);
        when(formConfigCache.get(ORG_KEY)).thenReturn(null);
        when(formConfigCache.get(WILDCARD_KEY)).thenReturn(CACHED);

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository, never()).getFormConfigDataByCriteria(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readFormConfig_localCacheMissIsConclusive_noDbCall() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(formConfigCache.isLoaded()).thenReturn(true);
        when(formConfigCache.get(anyString())).thenReturn(null);

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        verify(repository, never()).getFormConfigDataByCriteria(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readFormConfig_fallsBackToDbWhenSnapshotNeverLoaded() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(formConfigCache.isLoaded()).thenReturn(false);
        when(repository.getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any()))
                .thenReturn(Optional.of(entity()));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(getRequest(), "token", null, null, false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository).getFormConfigDataByCriteria(eq("page"), eq("player test"), eq("mobile"), eq("org1"), any(), any());
    }

    @Test
    void readFormConfig_adminUsesPayloadCriteria() {
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.READ))).thenReturn(Constants.SUCCESSFUL);
        when(formConfigCache.isLoaded()).thenReturn(true);
        when(formConfigCache.get(anyString())).thenReturn(null);

        ApiResponse response = service.readFormConfig(getRequest(), "admin1", "ignored-org", List.of("IGNORED"), true);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        // The admin path keys off the payload criteria (org1/PUBLIC), not the passed-in org/roles.
        verify(formConfigCache).get(ORG_KEY);
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
        verify(formConfigCache).reload();
        verify(cacheService).publishInvalidate();
    }

    @Test
    void createFormConfigV2_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateV2CreateForm(anyMap())).thenReturn(Constants.SUCCESSFUL);

        ApiResponse response = service.createFormConfigV2(getRequest(), "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository).save(any(FormConfigurationEntity.class));
    }

    @Test
    void createFormConfigV2_validationFailure() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateV2CreateForm(anyMap())).thenReturn("Validation error");

        ApiResponse response = service.createFormConfigV2(getRequest(), "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void readFormConfigById_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(repository.findById(1L)).thenReturn(Optional.of(entity()));

        ApiResponse response = service.readFormConfigById(1L, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void readFormConfigById_notFound() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(repository.findById(1L)).thenReturn(Optional.empty());

        ApiResponse response = service.readFormConfigById(1L, "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void updateFormConfigV2_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.UPDATE))).thenReturn(Constants.SUCCESSFUL);
        
        FormConfigurationEntity original = entity();
        original.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(original));

        Map<String, Object> req = getRequest();
        ((Map<String, Object>) req.get(Constants.Parameters.REQUEST)).put("id", 1L);

        ApiResponse response = service.updateFormConfigV2(req, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository).save(any(FormConfigurationEntity.class));
    }

    @Test
    void listFormConfigs_success() {
        when(accessTokenValidator.fetchUserDetailsFromToken("token")).thenReturn(userDetails);
        when(repository.findAll()).thenReturn(List.of(entity()));

        ApiResponse response = service.listFormConfigs("token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }
}

