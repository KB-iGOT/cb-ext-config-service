package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
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
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


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
    public ApiResponse createFormConfig(Map<String, Object> request, String token) {
        log.info("FormsConfigurationServiceImpl::createFormConfig:creating createFormConfig");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_FORMS_CONFIG_API);
        try {
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            String validationMsg = validationService.validateForm(request, Constants.Parameters.CREATE);
            if (!Constants.SUCCESSFUL.equals(validationMsg)) {
                ProjectUtil.returnErrorMsg(validationMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }
            Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.Parameters.REQUEST);
            FormConfigurationEntity existingFormConfig = validationService.validateFormData(requestData);
            if (Objects.nonNull(existingFormConfig)) {
                response = updateFormConfig(request, token);
                response.setMessage(Constants.FORM_CONFIG_EXIST);
                return response;
            }
            // Create a mutable copy of the JSON data as ObjectNode
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
            JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));

            FormConfigurationEntity configurationEntity = new FormConfigurationEntity();
            configurationEntity.setData(dataNode);
            configurationEntity.setCreatedAt(formattedCurrentTime);
            configurationEntity.setUpdatedAt(formattedCurrentTime);
            configurationEntity.setCreatedBy(userDetails.getUserId());
            configurationEntity.setUpdatedBy(userDetails.getUserId());
            configurationEntity.setType(requestData.get(Constants.TYPE).toString());
            configurationEntity.setPortal(requestData.get(Constants.PORTAL).toString());
            configurationEntity.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            configurationEntity.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            configurationEntity.setCriteria(criteriaNode);

            // Save to database
            formConfigurationRepository.save(configurationEntity);
            Map<String, Object> result = new HashMap<>();

            result.put(Constants.TYPE, configurationEntity.getType());
            result.put(Constants.SUBTYPE, configurationEntity.getSubtype());
            result.put(Constants.PORTAL, configurationEntity.getPortal());
            Map<String, Object> dataMap = objectMapper.convertValue(configurationEntity.getData(), Map.class);
            result.put(Constants.DATA, dataMap);

            // Set success response
            response.put(Constants.CREATED_ON, configurationEntity.getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);

            // Invalidate existing cache pattern for this org
            Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
            String criteriaOrg = criteria != null && criteria.get(Constants.ROOTORG) != null ? criteria.get(Constants.ROOTORG).toString() : "*";
            String criteriaRole = criteria != null && criteria.get(Constants.ROLE) != null ? criteria.get(Constants.ROLE).toString() : "public";
            String escapedOrg = criteriaOrg.contains("*") ? criteriaOrg.replace("*", "\\*") : criteriaOrg;
            String pattern = Constants.FORM_CONFIG_RESULT + Constants.DOT_SEPARATOR + configurationEntity.getType() + Constants.DOT_SEPARATOR + configurationEntity.getSubtype() + Constants.DOT_SEPARATOR + configurationEntity.getPortal() + Constants.DOT_SEPARATOR + escapedOrg + "*";
            cacheService.deleteCacheByPattern(pattern);

            // Cache result for future request under partitioned key
            String cacheKey = getCacheKey(configurationEntity.getType(), configurationEntity.getSubtype(), configurationEntity.getPortal(), criteriaOrg, criteriaRole);
            cacheService.putCache(cacheKey, result);

        } catch (Exception e) {
            log.error("Failed to create createFormConfig: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readFormConfig(Map<String, Object> formConfigData, String authTokenOrUserId,String userOrg,List<String> userRoles,boolean isAdmin) {

        log.info("FormsConfigurationServiceImpl::readFormConfig: Getting forms {}", formConfigData);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.READ_FORMS_CONFIG_API);
        try {
            UserDetails userDetails = new UserDetails();
            if (isAdmin) {
                userDetails.setUserId(authTokenOrUserId);
                userDetails.setUserRoles(userRoles);
                userDetails.setOrg(userOrg);
            }else{
                // Validate user token
                userDetails =  accessTokenValidator.fetchUserDetailsFromToken(authTokenOrUserId);
                if (ObjectUtils.isEmpty(userDetails)) {
                    response.getParams().setStatus(Constants.FAILED);
                    response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                    response.setResponseCode(HttpStatus.UNAUTHORIZED);
                    return response;
                }
            }

            String validationMsg = validationService.validateForm(formConfigData, Constants.Parameters.READ);
            if (!Constants.SUCCESSFUL.equals(validationMsg)) {
                ProjectUtil.returnErrorMsg(validationMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Map<String, Object> requestData = (Map<String, Object>) formConfigData.get(Constants.Parameters.REQUEST);
            String type = requestData.get(Constants.TYPE).toString();
            String subtype = requestData.get(Constants.SUBTYPE).toString();
            String portal = requestData.get(Constants.PORTAL).toString();

            Optional<FormConfigurationEntity> formConfigurationEntity = Optional.empty();

            if (isAdmin) {
                log.info("FormsConfigurationServiceImpl::readFormConfig: AdminUser");
                Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
                if (ObjectUtils.isEmpty(criteria)
                        || ObjectUtils.isEmpty(criteria.get(Constants.ROOTORG))
                        || ObjectUtils.isEmpty(criteria.get(Constants.ROLE))) {
                    ProjectUtil.returnErrorMsg(Constants.ResponseMessages.FIELD_CRIETERIA_MISSING, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
                String criteriaOrg = criteria.get(Constants.ROOTORG).toString();
                String criteriaRole = criteria.get(Constants.ROLE).toString();

                String cacheKey = getCacheKey(type, subtype, portal, criteriaOrg, criteriaRole);
                String cachedData = cacheService.getCache(cacheKey);
                if (cachedData != null) {
                    Map<String, Object> formConfig = objectMapper.readValue(
                            cachedData,
                            new TypeReference<Map<String, Object>>() {
                            }
                    );
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    response.setResult(formConfig);
                    return response;
                }

                formConfigurationEntity = formConfigurationRepository.getFormConfigDataByCriteria(
                        type,
                        subtype,
                        portal,
                        criteriaOrg,
                        Collections.singletonList(criteriaRole)
                );
                if (formConfigurationEntity.isPresent()) {
                    Map<String, Object> result = buildResult(formConfigurationEntity.get());

                    response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
                    response.setResult(result);
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    cacheService.putCache(cacheKey, result);
                    return response;
                }
            } else {

                log.info("FormsConfigurationServiceImpl::readFormConfig: Public/volunteer user");
                userRoles = userDetails.getUserRoles();
                userOrg = userDetails.getOrg();

                log.info("FormsConfigurationServiceImpl::readFormConfig: Public/volunteer user");
                userRoles = userDetails.getUserRoles();
                userOrg = userDetails.getOrg();

                // Step 1: Check cache for userRole + userOrgId (Volunteer case).
                if (ObjectUtils.isNotEmpty(userRoles)) {
                    for (String role : userRoles) {
                        String roleOrgCacheKey = getCacheKey(type, subtype, portal, userOrg, role);
                        String roleOrgCachedData = cacheService.getCache(roleOrgCacheKey);
                        if (roleOrgCachedData != null) {
                            Map<String, Object> formConfig = objectMapper.readValue(
                                    roleOrgCachedData,
                                    new TypeReference<Map<String, Object>>() {
                                    }
                            );
                            response.setResponseCode(HttpStatus.OK);
                            response.getParams().setStatus(Constants.SUCCESSFUL);
                            response.setResult(formConfig);
                            return response;
                        }
                    }
                }

                // Step 2: Query DB using userRole + userOrgId (Volunteer case).
                formConfigurationEntity = formConfigurationRepository.getFormConfigDataByCriteria(type, subtype, portal, userOrg, userRoles);
                if (formConfigurationEntity.isPresent()) {
                    Map<String, Object> result = buildResult(formConfigurationEntity.get());

                    response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
                    response.setResult(result);
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    if (ObjectUtils.isNotEmpty(userRoles)) {
                        for (String role : userRoles) {
                            cacheService.putCache(getCacheKey(type, subtype, portal, userOrg, role), result);
                        }
                    }
                    return response;
                }

                // Step 3: Check cache for userRole + '*' (Public case - fallback).
                if (ObjectUtils.isNotEmpty(userRoles)) {
                    for (String role : userRoles) {
                        String fallbackCacheKey = getCacheKey(type, subtype, portal, "*", role);
                        String fallbackCachedData = cacheService.getCache(fallbackCacheKey);
                        if (fallbackCachedData != null) {
                            Map<String, Object> formConfig = objectMapper.readValue(
                                    fallbackCachedData,
                                    new TypeReference<Map<String, Object>>() {
                                    }
                            );
                            response.setResponseCode(HttpStatus.OK);
                            response.getParams().setStatus(Constants.SUCCESSFUL);
                            response.setResult(formConfig);
                            return response;
                        }
                    }
                }

                // Step 4: Query DB with userRole + '*' (Public case - fallback).
                formConfigurationEntity = formConfigurationRepository.getFormConfigDataByCriteria(type, subtype, portal, "*", userRoles);
                if (formConfigurationEntity.isPresent()) {
                    Map<String, Object> result = buildResult(formConfigurationEntity.get());

                    response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
                    response.setResult(result);
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    if (ObjectUtils.isNotEmpty(userRoles)) {
                        for (String role : userRoles) {
                            cacheService.putCache(getCacheKey(type, subtype, portal, "*", role), result);
                        }
                    }
                    return response;
                }
            }

            // If not found in any condition
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("form data not found for " + type + " " + subtype + " and " + portal);
            response.setResponseCode(HttpStatus.NOT_FOUND);
            return response;

        } catch (Exception e) {
            log.error("Failed to read form Read: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_READ_FORM");
            response.getParams().setStatus(Constants.FAILED);
        }

        return response;
    }

    @Override
    public ApiResponse updateFormConfig(Map<String, Object> formConfigData, String token) {
        log.info("FormsConfigurationServiceImpl::updateFormConfig: Updating forms : {}", formConfigData);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.UPDATE_FORMS_CONFIG_API);
        try {
            // Validate user token
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String validationMsg = validationService.validateForm(formConfigData, Constants.Parameters.UPDATE);
            if (!Constants.SUCCESSFUL.equals(validationMsg)) {
                ProjectUtil.returnErrorMsg(validationMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }
            Map<String, Object> requestData = (Map<String, Object>) formConfigData.get(Constants.Parameters.REQUEST);
            String type = requestData.get(Constants.TYPE).toString();
            String subtype = requestData.get(Constants.SUBTYPE).toString();
            String portal = requestData.get(Constants.PORTAL).toString();
            Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
            String criteriaOrg = criteria != null && criteria.get(Constants.ROOTORG) != null ? criteria.get(Constants.ROOTORG).toString() : null;
            String criteriaRole = criteria != null && criteria.get(Constants.ROLE) != null ? criteria.get(Constants.ROLE).toString() : null;
            // Check if field exists and is active
            Optional<FormConfigurationEntity> formConfigurationEntity = formConfigurationRepository.getFormConfigDataByCriteria(
                    type,
                    subtype,
                    portal,
                    criteriaOrg,
                    Collections.singletonList(criteriaRole)
            );
            if (formConfigurationEntity.isEmpty()) {
                ProjectUtil.returnErrorMsg("FormConfig Data not exist: " + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }
            // Get old criteria org before updating
            JsonNode oldCriteriaNode = formConfigurationEntity.get().getCriteria();
            String oldOrg = "*";
            if (oldCriteriaNode != null && oldCriteriaNode.has(Constants.ROOTORG)) {
                oldOrg = oldCriteriaNode.get(Constants.ROOTORG).asText();
            }

            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            FormConfigurationEntity originalData = formConfigurationEntity.get();
            JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
            JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));

            originalData.setData(dataNode);
            originalData.setType(type);
            originalData.setPortal(portal);
            originalData.setSubtype(subtype);
            originalData.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            originalData.setCriteria(criteriaNode);
            originalData.setUpdatedBy(userDetails.getUserId());
            originalData.setUpdatedAt(formattedCurrentTime);
            formConfigurationRepository.save(originalData);

            Map<String, Object> result = new HashMap<>();
            result.put(Constants.TYPE, originalData.getType());
            result.put(Constants.SUBTYPE, originalData.getSubtype());
            result.put(Constants.PORTAL, originalData.getPortal());
            Map<String, Object> dataMap = objectMapper.convertValue(originalData.getData(), Map.class);
            result.put(Constants.DATA, dataMap);

            response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
            response.setResult(result);

            // Extract new criteria details for invalidation
            criteriaOrg = criteria != null && criteria.get(Constants.ROOTORG) != null ? criteria.get(Constants.ROOTORG).toString() : "*";
            criteriaRole = criteria != null && criteria.get(Constants.ROLE) != null ? criteria.get(Constants.ROLE).toString() : "public";

            // Invalidate cache for both old and new org configurations
            String escapedOldOrg = oldOrg.contains("*") ? oldOrg.replace("*", "\\*") : oldOrg;
            String oldPattern = Constants.FORM_CONFIG_RESULT + Constants.DOT_SEPARATOR + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal + Constants.DOT_SEPARATOR + escapedOldOrg + "*";
            cacheService.deleteCacheByPattern(oldPattern);

            if (!oldOrg.equals(criteriaOrg)) {
                String escapedNewOrg = criteriaOrg.contains("*") ? criteriaOrg.replace("*", "\\*") : criteriaOrg;
                String newPattern = Constants.FORM_CONFIG_RESULT + Constants.DOT_SEPARATOR + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal + Constants.DOT_SEPARATOR + escapedNewOrg + "*";
                cacheService.deleteCacheByPattern(newPattern);
            }

            // Cache result for future request under the partitioned key
            String cacheKey = getCacheKey(type, subtype, portal, criteriaOrg, criteriaRole);
            cacheService.putCache(cacheKey, result);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to updateFormConfig: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update updateFormConfig: {}", e.getMessage());
            return response;
        }
        return response;
    }


    private String getCacheKey(String type, String subtype, String portal, String userOrg, String userRole) {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.FORM_CONFIG_RESULT)
                .append(Constants.DOT_SEPARATOR).append(type)
                .append(Constants.DOT_SEPARATOR).append(subtype)
                .append(Constants.DOT_SEPARATOR).append(portal);
        if (userOrg != null) {
            sb.append(Constants.DOT_SEPARATOR).append(userOrg);
        }
        if (userRole != null) {
            sb.append(Constants.DOT_SEPARATOR).append(userRole);
        }
        return sb.toString();
    }

    private Map<String, Object> buildResult(FormConfigurationEntity formConfigurationEntity) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.TYPE, formConfigurationEntity.getType());
        result.put(Constants.SUBTYPE, formConfigurationEntity.getSubtype());
        result.put(Constants.PORTAL, formConfigurationEntity.getPortal());
        Map<String, Object> dataMap = objectMapper.convertValue(formConfigurationEntity.getData(), Map.class);
        result.put(Constants.DATA, dataMap);
        return result;
    }

    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }


}
