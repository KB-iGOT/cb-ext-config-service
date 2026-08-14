package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.formConfiguration.service.Validation.ValidationService;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.formConfiguration.service.cache.FormConfigCache;
import com.igot.cb.formConfiguration.service.cache.FormConfigCache.CachedFormConfig;
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
    private FormConfigCache formConfigCache;

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

            // Refresh this pod's snapshot immediately, then tell the other pods to reload.
            formConfigCache.reload();
            cacheService.publishInvalidate();

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
            Double clientVersion = Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString());

            List<String> orgs;
            List<String> roles;
            if (isAdmin) {
                log.info("FormsConfigurationServiceImpl::readFormConfig: AdminUser");
                Map<String, Object> criteria = (Map<String, Object>) requestData.get(Constants.CRITERIA);
                orgs = Collections.singletonList(criteria.get(Constants.ROOTORG).toString());
                roles = Collections.singletonList(criteria.get(Constants.ROLE).toString());
            } else {
                log.info("FormsConfigurationServiceImpl::readFormConfig: Public/volunteer user");
                // Prefer a config scoped to the user's own org, then fall back to the wildcard org.
                orgs = Arrays.asList(userDetails.getOrg(), Constants.WILDCARD);
                roles = userDetails.getUserRoles();
            }

            CachedFormConfig formConfig = lookup(type, subtype, portal, orgs, roles, clientVersion);
            if (formConfig != null) {
                response.put(Constants.CREATED_ON, formConfig.createdAt());
                // Defensive copy: the snapshot entry is shared across all request threads.
                response.setResult(new HashMap<>(formConfig.result()));
                response.setResponseCode(HttpStatus.OK);
                response.getParams().setStatus(Constants.SUCCESSFUL);
                return response;
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
                    Collections.singletonList(criteriaRole),
                    Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString())
            );
            if (formConfigurationEntity.isEmpty()) {
                ProjectUtil.returnErrorMsg("FormConfig Data not exist: " + type + Constants.DOT_SEPARATOR + subtype + Constants.DOT_SEPARATOR + portal, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
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

            // Refresh this pod's snapshot immediately, then tell the other pods to reload.
            formConfigCache.reload();
            cacheService.publishInvalidate();

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to updateFormConfig: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update updateFormConfig: {}", e.getMessage());
            return response;
        }
        return response;
    }


    /**
     * Resolves a form configuration from the in-JVM snapshot, trying each org in order
     * and, within an org, each role in order. The snapshot holds the whole table, so a
     * miss is conclusive and no database call is made.
     */
    private CachedFormConfig lookup(String type, String subtype, String portal, List<String> orgs,
                                    List<String> roles, Double clientVersion) {
        if (ObjectUtils.isEmpty(roles)) {
            return null;
        }
        for (String org : orgs) {
            if (org == null || org.isBlank()) {
                continue;
            }
            for (String role : roles) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                CachedFormConfig hit = formConfigCache.isLoaded()
                        ? formConfigCache.get(FormConfigCache.cacheKey(type, subtype, portal, org, role, clientVersion))
                        : loadFromDb(type, subtype, portal, org, role, clientVersion);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * Fallback used only while the snapshot has never loaded successfully, so that a
     * database outage at startup degrades to the previous behaviour instead of 404s.
     */
    private CachedFormConfig loadFromDb(String type, String subtype, String portal, String org, String role,
                                        Double clientVersion) {
        return formConfigurationRepository
                .getFormConfigDataByCriteria(type, subtype, portal, org, Collections.singletonList(role), clientVersion)
                .map(entity -> new CachedFormConfig(buildResult(entity), entity.getCreatedAt()))
                .orElse(null);
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

            formConfigCache.reload();
            cacheService.publishInvalidate();

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

            formConfigCache.reload();
            cacheService.publishInvalidate();

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
