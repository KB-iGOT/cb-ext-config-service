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
            Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
            String designation = resolveDesignation(criteria);
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
            configurationEntity.setName(requestData.get(Constants.NAME).toString());
            configurationEntity.setType(requestData.get(Constants.TYPE).toString());
            configurationEntity.setPortal(requestData.get(Constants.PORTAL).toString());
            configurationEntity.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            configurationEntity.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            configurationEntity.setCriteria(criteriaNode);

            // Save to database
            formConfigurationRepository.save(configurationEntity);
            Map<String, Object> result = new HashMap<>();

            result.put(Constants.NAME, configurationEntity.getName());
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
            String criteriaOrg = criteria != null && criteria.get(Constants.ROOTORG) != null ? criteria.get(Constants.ROOTORG).toString() : "*";
            String criteriaRole = criteria != null && criteria.get(Constants.ROLE) != null ? criteria.get(Constants.ROLE).toString() : "public";
            String escapedOrg = criteriaOrg.contains("*") ? criteriaOrg.replace("*", "\\*") : criteriaOrg;
            String escapedDesignation = designation != null && designation.contains("*") ? designation.replace("*", "\\*") : designation;
            String pattern = Constants.FORM_CONFIG_RESULT + Constants.DOT_SEPARATOR + configurationEntity.getType() + Constants.DOT_SEPARATOR + configurationEntity.getSubtype() + Constants.DOT_SEPARATOR + configurationEntity.getPortal() + Constants.DOT_SEPARATOR + escapedOrg + "*";
            if (escapedDesignation != null) {
                pattern = pattern + Constants.DOT_SEPARATOR + escapedDesignation + "*";
            }
            cacheService.deleteCacheByPattern(pattern);

            // Cache result for future request under partitioned key
            String cacheKey = getCacheKey(configurationEntity.getType(), configurationEntity.getSubtype(), configurationEntity.getPortal(), criteriaOrg, criteriaRole, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
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
            Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
            String designation = resolveDesignation(criteria);
            Optional<FormConfigurationEntity> formConfigurationEntity = Optional.empty();

            if (isAdmin) {
                log.info("FormsConfigurationServiceImpl::readFormConfig: AdminUser");
                String criteriaOrg = criteria != null && criteria.get(Constants.ROOTORG) != null ? criteria.get(Constants.ROOTORG).toString() : "*";
                String criteriaRole = criteria != null && criteria.get(Constants.ROLE) != null ? criteria.get(Constants.ROLE).toString() : "public";
                
                String cacheKey = getCacheKey(type, subtype, portal, criteriaOrg, criteriaRole, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
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

                formConfigurationEntity = fetchFormConfigByCriteria(
                        type,
                        subtype,
                        portal,
                        criteriaOrg,
                        Collections.singletonList(criteriaRole),
                        Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()),
                        designation
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

                // Step 1: Check cache for userRole + userOrgId (Volunteer case).
                if (ObjectUtils.isNotEmpty(userRoles)) {
                    for (String role : userRoles) {
                        String roleOrgCacheKey = getCacheKey(type, subtype, portal, userOrg, role, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
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
                formConfigurationEntity = fetchFormConfigByCriteria(type, subtype, portal, userOrg, userRoles, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()), designation);
                if (formConfigurationEntity.isPresent()) {
                    Map<String, Object> result = buildResult(formConfigurationEntity.get());

                    response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
                    response.setResult(result);
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    if (ObjectUtils.isNotEmpty(userRoles)) {
                        for (String role : userRoles) {
                            cacheService.putCache(getCacheKey(type, subtype, portal, userOrg, role, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString())), result);
                        }
                    }
                    return response;
                }

                // Step 3: Check cache for userRole + '*' (Public case - fallback).
                if (ObjectUtils.isNotEmpty(userRoles)) {
                    for (String role : userRoles) {
                        String fallbackCacheKey = getCacheKey(type, subtype, portal, "*", role, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
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
                formConfigurationEntity = fetchFormConfigByCriteria(type, subtype, portal, "*", userRoles, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()), designation);
                if (formConfigurationEntity.isPresent()) {
                    Map<String, Object> result = buildResult(formConfigurationEntity.get());

                    response.put(Constants.CREATED_ON, formConfigurationEntity.get().getCreatedAt());
                    response.setResult(result);
                    response.setResponseCode(HttpStatus.OK);
                    response.getParams().setStatus(Constants.SUCCESSFUL);
                    if (ObjectUtils.isNotEmpty(userRoles)) {
                        for (String role : userRoles) {
                            cacheService.putCache(getCacheKey(type, subtype, portal, "*", role, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString())), result);
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
            String designation = resolveDesignation(criteria);
            // Check if field exists and is active
            Optional<FormConfigurationEntity> formConfigurationEntity = fetchFormConfigByCriteria(
                    type,
                    subtype,
                    portal,
                    criteriaOrg,
                    Collections.singletonList(criteriaRole),
                    Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()),
                    designation
            );
            if (formConfigurationEntity.isEmpty()) {
                ProjectUtil.returnErrorMsg("FormConfig Data not exist: " + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }
            // Get old criteria org before updating
            JsonNode oldCriteriaNode = formConfigurationEntity.get().getCriteria();
            String oldOrg = "*";
            String oldDesignation = null;
            if (oldCriteriaNode != null && oldCriteriaNode.has(Constants.ROOTORG)) {
                oldOrg = oldCriteriaNode.get(Constants.ROOTORG).asText();
            }
            if (oldCriteriaNode != null && oldCriteriaNode.has(Constants.DESIGNATION)) {
                oldDesignation = resolveDesignation(oldCriteriaNode);
            }

            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            FormConfigurationEntity originalData = formConfigurationEntity.get();
            JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
            JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));

            originalData.setData(dataNode);
            originalData.setName(requestData.get(Constants.NAME).toString());
            originalData.setType(type);
            originalData.setPortal(portal);
            originalData.setSubtype(subtype);
            originalData.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            originalData.setCriteria(criteriaNode);
            originalData.setUpdatedBy(userDetails.getUserId());
            originalData.setUpdatedAt(formattedCurrentTime);
            formConfigurationRepository.save(originalData);

            Map<String, Object> result = new HashMap<>();
            result.put(Constants.NAME, originalData.getName());
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
            if (oldDesignation != null) {
                oldPattern = oldPattern + Constants.DOT_SEPARATOR + oldDesignation + "*";
            }
            cacheService.deleteCacheByPattern(oldPattern);

            if (!oldOrg.equals(criteriaOrg)) {
                String escapedNewOrg = criteriaOrg.contains("*") ? criteriaOrg.replace("*", "\\*") : criteriaOrg;
                String newPattern = Constants.FORM_CONFIG_RESULT + Constants.DOT_SEPARATOR + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal + Constants.DOT_SEPARATOR + escapedNewOrg + "*";
                if (designation != null) {
                    newPattern = newPattern + Constants.DOT_SEPARATOR + designation + "*";
                }
                cacheService.deleteCacheByPattern(newPattern);
            }

            // Cache result for future request under the partitioned key
            String cacheKey = getCacheKey(type, subtype, portal, criteriaOrg, criteriaRole, designation, Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            cacheService.putCache(cacheKey, result);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to updateFormConfig: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update updateFormConfig: {}", e.getMessage());
            return response;
        }
        return response;
    }


    private String getCacheKey(String type, String subtype, String portal, String userOrg, String userRole, String designation, Double clientVersion) {
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
        if (designation != null) {
            sb.append(Constants.DOT_SEPARATOR).append(Constants.DESIGNATION)
                    .append(Constants.DOT_SEPARATOR).append(designation);
        }
        if (clientVersion != null) sb.append(Constants.DOT_SEPARATOR).append(clientVersion);
        return sb.toString();
    }

    private Optional<FormConfigurationEntity> fetchFormConfigByCriteria(String type, String subtype, String portal, String rootOrg, List<String> roles, Double clientVersion, String designation) {
        if (designation != null && !designation.isBlank()) {
            return formConfigurationRepository.getFormConfigDataByCriteriaByDesignation(
                    type,
                    subtype,
                    portal,
                    rootOrg,
                    roles,
                    clientVersion,
                    buildDesignationJson(designation)
            );
        }
        return formConfigurationRepository.getFormConfigDataByCriteria(
                type,
                subtype,
                portal,
                rootOrg,
                roles,
                clientVersion
        );
    }

    private String buildDesignationJson(String designation) {
        if (designation == null || designation.isBlank()) {
            return "[]";
        }
        return "[\"" + designation.replace("\"", "\\\"") + "\"]";
    }

    private String resolveDesignation(Map<String, Object> criteria) {
        if (criteria == null) {
            return null;
        }
        Object designationObj = criteria.get(Constants.DESIGNATION);
        if (designationObj instanceof String) {
            return ((String) designationObj).trim();
        }
        if (designationObj instanceof Collection<?>) {
            return ((Collection<?>) designationObj).stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .sorted()
                    .reduce((first, second) -> first + "|" + second)
                    .orElse(null);
        }
        return null;
    }

    private String resolveDesignation(JsonNode criteriaNode) {
        if (criteriaNode == null || !criteriaNode.has(Constants.DESIGNATION)) {
            return null;
        }
        JsonNode designationNode = criteriaNode.get(Constants.DESIGNATION);
        if (designationNode.isArray()) {
            StringBuilder designationBuilder = new StringBuilder();
            for (JsonNode element : designationNode) {
                if (designationBuilder.length() > 0) {
                    designationBuilder.append("|");
                }
                designationBuilder.append(element.asText());
            }
            return designationBuilder.length() > 0 ? designationBuilder.toString() : null;
        }
        return designationNode.isTextual() ? designationNode.asText() : null;
    }

    private Map<String, Object> buildResult(FormConfigurationEntity formConfigurationEntity) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, formConfigurationEntity.getName());
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

    @Override
    public ApiResponse createFormConfigV2(Map<String, Object> request, String token) {
        log.info("FormsConfigurationServiceImpl::createFormConfigV2: creating form config v2");
        ApiResponse response = ProjectUtil.createDefaultResponse("api.form.create.v2");
        try {
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            String validationMsg = validationService.validateV2CreateForm(request);
            if (!Constants.SUCCESSFUL.equals(validationMsg)) {
                ProjectUtil.returnErrorMsg(validationMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.Parameters.REQUEST);
            
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            FormConfigurationEntity configurationEntity = new FormConfigurationEntity();
            configurationEntity.setCreatedAt(formattedCurrentTime);
            configurationEntity.setUpdatedAt(formattedCurrentTime);
            configurationEntity.setCreatedBy(userDetails.getUserId());
            configurationEntity.setUpdatedBy(userDetails.getUserId());
            configurationEntity.setName(requestData.get(Constants.NAME).toString());
            configurationEntity.setType(requestData.get(Constants.TYPE).toString());
            configurationEntity.setPortal(requestData.get(Constants.PORTAL).toString());
            configurationEntity.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            configurationEntity.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));

            // Save to database
            formConfigurationRepository.save(configurationEntity);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", configurationEntity.getId());
            result.put(Constants.NAME, configurationEntity.getName());
            result.put(Constants.TYPE, configurationEntity.getType());
            result.put(Constants.SUBTYPE, configurationEntity.getSubtype());
            result.put(Constants.PORTAL, configurationEntity.getPortal());
            result.put(Constants.CLIENT_VERSION, configurationEntity.getClientVersion());

            // Set success response
            response.put(Constants.CREATED_ON, configurationEntity.getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);

        } catch (Exception e) {
            log.error("Failed to create createFormConfigV2: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readFormConfigById(Long formId, String token) {
        log.info("FormsConfigurationServiceImpl::readFormConfigById: reading form config by id: {}", formId);
        ApiResponse response = ProjectUtil.createDefaultResponse("api.form.read.v2");
        try {
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            Optional<FormConfigurationEntity> entityOpt = formConfigurationRepository.findById(formId);
            if (entityOpt.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("Form configuration not found for id: " + formId);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                return response;
            }

            Map<String, Object> result = buildResult(entityOpt.get());
            response.put(Constants.CREATED_ON, entityOpt.get().getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
        } catch (Exception e) {
            log.error("Failed to read form by id: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_READ_FORM");
            response.getParams().setStatus(Constants.FAILED);
        }
        return response;
    }

    @Override
    public ApiResponse updateFormConfigV2(Map<String, Object> request, String token) {
        log.info("FormsConfigurationServiceImpl::updateFormConfigV2: updating form config v2");
        ApiResponse response = ProjectUtil.createDefaultResponse("api.form.update.v2");
        try {
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String validationMsg = validationService.validateForm(request, Constants.Parameters.UPDATE);
            if (!Constants.SUCCESSFUL.equals(validationMsg)) {
                ProjectUtil.returnErrorMsg(validationMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Map<String, Object> requestData = (Map<String, Object>) request.get(Constants.Parameters.REQUEST);
            Long formId = null;
            if (requestData.containsKey("formId") && requestData.get("formId") != null) {
                formId = Long.valueOf(requestData.get("formId").toString());
            } else if (requestData.containsKey("id") && requestData.get("id") != null) {
                formId = Long.valueOf(requestData.get("id").toString());
            }

            if (formId == null) {
                ProjectUtil.returnErrorMsg("Field formId/id is missing", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Optional<FormConfigurationEntity> existingOpt = formConfigurationRepository.findById(formId);
            if (existingOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("FormConfig Data not exist for id: " + formId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            FormConfigurationEntity originalData = existingOpt.get();

            if (requestData.containsKey(Constants.NAME)) {
                originalData.setName(requestData.get(Constants.NAME).toString());
            }
            if (requestData.containsKey(Constants.TYPE)) {
                originalData.setType(requestData.get(Constants.TYPE).toString());
            }
            if (requestData.containsKey(Constants.SUBTYPE)) {
                originalData.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            }
            if (requestData.containsKey(Constants.PORTAL)) {
                originalData.setPortal(requestData.get(Constants.PORTAL).toString());
            }
            if (requestData.containsKey(Constants.CLIENT_VERSION)) {
                originalData.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            }
            if (requestData.containsKey(Constants.CRITERIA)) {
                JsonNode criteriaNode = objectMapper.valueToTree(requestData.get(Constants.CRITERIA));
                originalData.setCriteria(criteriaNode);
            }
            if (requestData.containsKey(Constants.DATA)) {
                JsonNode dataNode = objectMapper.valueToTree(requestData.get(Constants.DATA));
                originalData.setData(dataNode);
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            originalData.setUpdatedBy(userDetails.getUserId());
            originalData.setUpdatedAt(formattedCurrentTime);

            formConfigurationRepository.save(originalData);

            Map<String, Object> result = new HashMap<>();
            result.put("id", originalData.getId());
            result.put(Constants.NAME, originalData.getName());
            result.put(Constants.TYPE, originalData.getType());
            result.put(Constants.SUBTYPE, originalData.getSubtype());
            result.put(Constants.PORTAL, originalData.getPortal());
            result.put(Constants.CLIENT_VERSION, originalData.getClientVersion());
            if (originalData.getCriteria() != null) {
                result.put(Constants.CRITERIA, objectMapper.convertValue(originalData.getCriteria(), Map.class));
            }
            if (originalData.getData() != null) {
                result.put(Constants.DATA, objectMapper.convertValue(originalData.getData(), Map.class));
            }

            response.put(Constants.CREATED_ON, originalData.getCreatedAt());
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
            response.setResult(result);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to updateFormConfigV2: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to updateFormConfigV2: {}", e.getMessage(), e);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse listFormConfigs(String token) {
        log.info("FormsConfigurationServiceImpl::listFormConfigs: listing form configurations");
        ApiResponse response = ProjectUtil.createDefaultResponse("api.form.list.v2");
        try {
            UserDetails userDetails = accessTokenValidator.fetchUserDetailsFromToken(token);
            if (ObjectUtils.isEmpty(userDetails)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            List<FormConfigurationEntity> allConfigs = formConfigurationRepository.findAll();
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (FormConfigurationEntity entity : allConfigs) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", entity.getId());
                map.put(Constants.NAME, entity.getName());
                map.put(Constants.TYPE, entity.getType());
                map.put(Constants.SUBTYPE, entity.getSubtype());
                map.put(Constants.PORTAL, entity.getPortal());
                map.put(Constants.CLIENT_VERSION, entity.getClientVersion());
                resultList.add(map);
            }
            response.put("formConfigurations", resultList);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
        } catch (Exception e) {
            log.error("Failed to list forms: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_LIST_FORMS");
            response.getParams().setStatus(Constants.FAILED);
        }
        return response;
    }

}
