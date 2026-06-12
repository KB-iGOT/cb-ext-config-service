package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.formConfiguration.service.Validation.ValidationService;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


@Service
@Slf4j
public class FormsConfigurationServiceImpl implements FormsConfigurationService {

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private FormConfigurationRepository formConfigurationRepository;

    @Autowired
    ValidationService validationService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    ObjectMapper objectMapper;

    @Override
    public ApiResponse createFormConfig(Map<String, Object> request,String token) {
        log.info("FormsConfigurationServiceImpl::createFormConfig:creating createFormConfig");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_FORMS_CONFIG_API);
        validationService.validateForm(request,Constants.Parameters.CREATE);

        Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.Parameters.REQUEST);
        FormConfigurationEntity existingFormConfig = validationService.validateFormData(requestData,response);
        if(Objects.nonNull(existingFormConfig)){
            response.setMessage(Constants.FORM_CONFIG_EXIST);
            response = updateFormConfig(request,token);
            return response;
        }

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }
            // Create a mutable copy of the JSON data as ObjectNode
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
            JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));

            FormConfigurationEntity configurationEntity = new FormConfigurationEntity();
            configurationEntity.setData(dataNode );
            configurationEntity.setCreatedAt(formattedCurrentTime);
            configurationEntity.setUpdatedAt(formattedCurrentTime);
            configurationEntity.setCreatedBy(userId);
            configurationEntity.setUpdatedBy(userId);
            configurationEntity.setType(requestData.get(Constants.TYPE).toString());
            configurationEntity.setPortal(requestData.get(Constants.PORTAL).toString());
            configurationEntity.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            configurationEntity.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            configurationEntity.setCriteria(criteriaNode);

            // Save to database
            formConfigurationRepository.save(configurationEntity);
            Map<String, Object> result = new HashMap<>();

            result.put(Constants.TYPE,configurationEntity.getType());
            result.put(Constants.SUBTYPE,configurationEntity.getSubtype());
            result.put(Constants.PORTAL,configurationEntity.getPortal());
            Map<String, Object> dataMap = objectMapper.convertValue(configurationEntity.getData(), Map.class);
            result.put(Constants.DATA, dataMap);

            // Set success response
            response.put(Constants.CREATED_ON,configurationEntity.getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
            // Cache result for future request
            cacheService.putCache(Constants.FORM_CONFIG_RESULT, result);

        } catch (Exception e) {
            log.error("Failed to create createFormConfig: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readFormConfig(Map<String, Object>  formConfigData,String token) {

        log.info("FormsConfigurationServiceImpl::readFormConfig: Getting forms {}", formConfigData);
        validationService.validateForm(formConfigData,Constants.Parameters.READ);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.READ_FORMS_CONFIG_API);
        Map<String, Object> requestData = (Map<String, Object>) formConfigData.get(Constants.Parameters.REQUEST);
        try {
            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Try to fetch from Redis cache first
            String cachedData = cacheService.getCache(Constants.FORM_CONFIG_RESULT);
            if (cachedData != null) {
                Map<String, Object> formConfig = objectMapper.convertValue(cachedData, Map.class);
                response.setResponseCode(HttpStatus.OK);
                response.getParams().setStatus(Constants.SUCCESSFUL);
                response.setResult(formConfig);
                return response;
            }

            String type = requestData.get(Constants.TYPE).toString();
            String subtype = requestData.get(Constants.SUBTYPE).toString();
            String portal = requestData.get(Constants.PORTAL).toString();
            // If not in cache, fetch from database
            Optional<FormConfigurationEntity> formConfigurationEntity = formConfigurationRepository.getformConfigData(type,subtype,portal);
            if (formConfigurationEntity.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("form data not found for " +type + " " + subtype + " and " + portal );
                response.setResponseCode(HttpStatus.NOT_FOUND);
                return response;
            }


            Map<String, Object> result = new HashMap<>();
            response.put(Constants.CREATED_ON,formConfigurationEntity.get().getCreatedAt());
            response.setResponseCode(HttpStatus.OK);
            result.put(Constants.TYPE,formConfigurationEntity.get().getType());
            result.put(Constants.SUBTYPE,formConfigurationEntity.get().getSubtype());
            result.put(Constants.PORTAL,formConfigurationEntity.get().getPortal());
            Map<String, Object> dataMap = objectMapper.convertValue(formConfigurationEntity.get().getData(), Map.class);
            result.put(Constants.DATA, dataMap);

            response.put(Constants.CREATED_ON,formConfigurationEntity.get().getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);

        } catch (Exception e) {
            log.error("Failed to read form Read: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_READ_FORM");
            response.getParams().setStatus(Constants.FAILED);
        }

        return response;
    }

    @Override
    public ApiResponse updateFormConfig(Map<String, Object> formConfigData,String token) {
        log.info("FormsConfigurationServiceImpl::updateFormConfig: Updating forms : {}", formConfigData);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.UPDATE_FORMS_CONFIG_API);

        try {
            // Validate payload
            validationService.validateForm(formConfigData,Constants.Parameters.UPDATE);
            Map<String, Object> requestData = (Map<String, Object>) formConfigData.get(Constants.Parameters.REQUEST);

            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }
            String type = requestData.get(Constants.TYPE).toString();
            String subtype = requestData.get(Constants.SUBTYPE).toString();
            String portal = requestData.get(Constants.PORTAL).toString();
            // Check if field exists and is active
            Optional<FormConfigurationEntity> formConfigurationEntity = formConfigurationRepository.getformConfigData(type,subtype,portal);
            if (formConfigurationEntity.isEmpty()) {
                return response;
            }
            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            FormConfigurationEntity originalData = formConfigurationEntity.get();
            JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
            JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));

            originalData.setData(dataNode);
            originalData.setPortal(portal);
            originalData.setSubtype(subtype);
            originalData.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            originalData.setCriteria(criteriaNode);
            originalData.setUpdatedBy(userId);
            originalData.setUpdatedAt(formattedCurrentTime);
            formConfigurationRepository.save(originalData);

            Map<String, Object> result = new HashMap<>();
            result.put(Constants.TYPE,originalData.getType());
            result.put(Constants.SUBTYPE,originalData.getSubtype());
            result.put(Constants.PORTAL,originalData.getPortal());
            Map<String, Object> dataMap = objectMapper.convertValue(originalData.getData(), Map.class);
            result.put(Constants.DATA, dataMap);


            response.put(Constants.CREATED_ON,formConfigurationEntity.get().getCreatedAt());
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
            response.setResult(result);
            // Cache result for future request
            cacheService.putCache(Constants.FORM_CONFIG_RESULT, response);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to updateFormConfig: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update updateFormConfig: {}", e.getMessage());
            return response;
        }
        return response;
    }


    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }



}
