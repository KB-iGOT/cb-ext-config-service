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

import static org.junit.jupiter.api.Assertions.*;
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
        userDetails.setUserRoles(List.of("MDO_ADMIN"));
    }

    private Map<String,Object> getRequest() {

        Map<String,Object> data = new HashMap<>();
        data.put("field","value");

        Map<String,Object> criteria = new HashMap<>();
        criteria.put("rootOrg","org1");
        criteria.put("roles",List.of("MDO_ADMIN"));

        Map<String,Object> req = new HashMap<>();
        req.put(Constants.TYPE,"page-web");
        req.put(Constants.SUBTYPE,"player");
        req.put(Constants.PORTAL,"mobile");
        req.put(Constants.CLIENT_VERSION,1.0);
        req.put(Constants.DATA,data);
        req.put(Constants.CRITERIA,criteria);

        Map<String,Object> wrapper = new HashMap<>();
        wrapper.put(Constants.Parameters.REQUEST,req);

        return wrapper;
    }

    @Test
    void createFormConfig_success() {

        Map<String,Object> request = getRequest();

        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), eq(Constants.Parameters.CREATE)))
                .thenReturn(Constants.SUCCESSFUL);

        when(validationService.validateFormData(anyMap()))
                .thenReturn(null);

        when(objectMapper.valueToTree(any()))
                .thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(Map.of("field","value"));

        ApiResponse response = service.createFormConfig(request,"token");

        assertEquals(HttpStatus.OK,response.getResponseCode());

        verify(repository).save(any(FormConfigurationEntity.class));
        verify(cacheService).putCache(anyString(),any());
    }

    @Test
    void createFormConfig_invalidToken() {

        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(null);

        ApiResponse response = service.createFormConfig(getRequest(),"token");

        assertEquals(HttpStatus.UNAUTHORIZED,response.getResponseCode());

        verify(repository,never()).save(any());
    }

    @Test
    void createFormConfig_validationFailure() {

        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(),anyString()))
                .thenReturn("Invalid Request");

        ApiResponse response = service.createFormConfig(getRequest(),"token");

        assertEquals(HttpStatus.BAD_REQUEST,response.getResponseCode());

        verify(repository,never()).save(any());
    }

    @Test
    void createFormConfig_existingForm() {

        FormConfigurationEntity entity = new FormConfigurationEntity();

        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(),anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(validationService.validateFormData(anyMap()))
                .thenReturn(entity);

        FormsConfigurationServiceImpl spy = spy(service);

        ApiResponse updateResponse = new ApiResponse();
        updateResponse.setResponseCode(HttpStatus.OK);

        doReturn(updateResponse).when(spy)
                .updateFormConfig(anyMap(),eq("token"));

        ApiResponse response = spy.createFormConfig(getRequest(),"token");

        verify(spy).updateFormConfig(anyMap(),eq("token"));
    }

    @Test
    void readFormConfig_cacheHit_specificOrg() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(cacheService.getCache(anyString()))
                .thenReturn("{}");

        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(
                getRequest(),
                "token",
                null,
                null,
                false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService, times(1)).getCache(anyString());
        verify(repository, never()).getFormConfigDataByCriteria(any(), any(), any(), any(), any());
    }

    @Test
    void readFormConfig_cacheMiss_dbHit_specificOrg() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(cacheService.getCache(anyString()))
                .thenReturn(null);

        FormConfigurationEntity entity = new FormConfigurationEntity();
        entity.setType("page-web");
        entity.setSubtype("player");
        entity.setPortal("mobile");
        entity.setData(mock(com.fasterxml.jackson.databind.JsonNode.class));

        when(repository.getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any()))
                .thenReturn(Optional.of(entity));

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(
                getRequest(),
                "token",
                null,
                null,
                false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService).putCache(anyString(), any());
    }

    @Test
    void readFormConfig_cacheMiss_dbMiss_fallbackCacheHit() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(cacheService.getCache(anyString()))
                .thenReturn(null)
                .thenReturn("{}");

        when(repository.getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any()))
                .thenReturn(Optional.empty());

        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(
                getRequest(),
                "token",
                null,
                null,
                false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository, times(1)).getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any());
        verify(repository, never()).getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("*"), any());
    }

    @Test
    void readFormConfig_cacheMiss_dbMiss_fallbackCacheMiss_fallbackDbHit() throws Exception {
        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(cacheService.getCache(anyString()))
                .thenReturn(null)
                .thenReturn(null);

        when(repository.getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any()))
                .thenReturn(Optional.empty());

        FormConfigurationEntity fallbackEntity = new FormConfigurationEntity();
        fallbackEntity.setType("page-web");
        fallbackEntity.setSubtype("player");
        fallbackEntity.setPortal("mobile");
        fallbackEntity.setData(mock(com.fasterxml.jackson.databind.JsonNode.class));

        when(repository.getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("*"), any()))
                .thenReturn(Optional.of(fallbackEntity));

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.readFormConfig(
                getRequest(),
                "token",
                null,
                null,
                false);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(cacheService).putCache(anyString(), any());
    }

    @Test
    void readFormConfig_admin_noFallback() throws Exception {
        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(cacheService.getCache(anyString()))
                .thenReturn(null);

        when(repository.getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any()))
                .thenReturn(Optional.empty());

        ApiResponse response = service.readFormConfig(
                getRequest(),
                "admin1",
                "org1",
                List.of("MDO_ADMIN"),
                true);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        verify(repository, times(1)).getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("org1"), any());
        verify(repository, never()).getFormConfigDataByCriteria(eq("page-web"), eq("player"), eq("mobile"), eq("*"), any());
    }

    @Test
    void updateFormConfig_success() {
        FormConfigurationEntity entity = new FormConfigurationEntity();

        when(accessTokenValidator.fetchUserDetailsFromToken("token"))
                .thenReturn(userDetails);

        when(validationService.validateForm(anyMap(), anyString()))
                .thenReturn(Constants.SUCCESSFUL);

        when(repository.getFormConfigDataByCriteria(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(entity));

        when(objectMapper.valueToTree(any()))
                .thenReturn(mock(com.fasterxml.jackson.databind.JsonNode.class));

        when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.updateFormConfig(getRequest(), "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(repository).save(any(FormConfigurationEntity.class));
        verify(cacheService, times(2)).deleteCacheByPattern(anyString());
    }
}

