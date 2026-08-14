package com.igot.cb.formConfiguration.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.external.OrgReadService;
import com.igot.cb.formConfiguration.external.UserDesignationService;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.rule.FormConfigResolutionContext;
import com.igot.cb.formConfiguration.rule.FormConfigRuleEngine;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.formConfiguration.service.cache.FormConfigLocalCache;
import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.formConfiguration.service.Validation.ValidationService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import com.igot.cb.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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
    ObjectMapper objectMapper;

    @Autowired
    private FormConfigRuleEngine formConfigRuleEngine;

    @Autowired
    private OrgReadService orgReadService;

    @Autowired
    private UserDesignationService userDesignationService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private FormConfigLocalCache formConfigLocalCache;

    @Override
    public ApiResponse readFormConfig(Map<String, Object> formConfigData, String authTokenOrUserId,String userOrg,List<String> userRoles,boolean isAdmin) {

        log.info("FormsConfigurationServiceImpl::readFormConfig: Getting forms {}", formConfigData);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.READ_FORMS_CONFIG_API);
        try {
            UserDetails userDetails;
            String token = null;
            if (isAdmin) {
                log.info("FormsConfigurationServiceImpl::readFormConfig: AdminUser");
                userDetails = new UserDetails();
                userDetails.setUserId(authTokenOrUserId);
                userDetails.setUserRoles(userRoles);
                userDetails.setOrg(userOrg);
            } else {
                log.info("FormsConfigurationServiceImpl::readFormConfig: Public/volunteer user");
                userDetails = accessTokenValidator.fetchUserDetailsFromToken(authTokenOrUserId);
                if (ObjectUtils.isEmpty(userDetails)) {
                    response.getParams().setStatus(Constants.FAILED);
                    response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                    response.setResponseCode(HttpStatus.UNAUTHORIZED);
                    return response;
                }
                token = authTokenOrUserId;
            }
            String userId = userDetails.getUserId();
            List<String> roles = userDetails.getUserRoles();
            String rootOrg = userDetails.getOrg();

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

            // Rule 1 (designation) rootOrg, priority-resolved:
            // 1st priority ("ministry"): the caller's own rootOrg (from token) is checked first; only
            //               if it's NOT a "ministry" do we fall back to checking the request's
            //               explicit rootOrg for "ministry" (skipping that org-read call entirely
            //               when the token's rootOrg already resolved it).
            // 2nd priority ("state"): the caller's own rootOrg (from token), reusing the same
            //               already-resolved type — no second org-read call needed.
            // Neither -> designationRootOrg stays null, so the designation rule doesn't apply at all.
            String designationRootOrg = null;
            String designationMinistryOrStateType = null;
            String tokenOrgType = orgReadService.getMinistryOrStateType(rootOrg, token);
            if (Constants.MINISTRY.equalsIgnoreCase(tokenOrgType)) {
                designationRootOrg = rootOrg;
                designationMinistryOrStateType = Constants.MINISTRY;
            } else {
                String inputRootOrg = null;
                Object inputRootOrgValue = requestData.get(Constants.ROOTORG);
                if (ObjectUtils.isNotEmpty(inputRootOrgValue) && !"*".equals(inputRootOrgValue.toString())) {
                    inputRootOrg = inputRootOrgValue.toString();
                }
                if (inputRootOrg != null && Constants.MINISTRY.equalsIgnoreCase(orgReadService.getMinistryOrStateType(inputRootOrg, token))) {
                    designationRootOrg = inputRootOrg;
                    designationMinistryOrStateType = Constants.MINISTRY;
                }
            }

            if (designationRootOrg == null && Constants.STATE.equalsIgnoreCase(tokenOrgType)) {
                designationRootOrg = rootOrg;
                designationMinistryOrStateType = Constants.STATE;
            }

            userDesignationService.resolveUserProfile(userDetails, token);
            List<String> designations = userDetails.getDesignations();

            FormConfigResolutionContext ctx = FormConfigResolutionContext.builder()
                    .type(type).subtype(subtype).portal(portal).clientVersion(clientVersion)
                    .roles(roles).rootOrg(rootOrg)
                    .designations(designations).designationRootOrg(designationRootOrg)
                    .designationMinistryOrStateType(designationMinistryOrStateType)
                    .build();

            Optional<FormConfigurationEntity> match = formConfigRuleEngine.resolve(ctx);
            if (match.isPresent()) {
                Map<String, Object> result = buildResult(match.get(), isAdmin);
                response.put(Constants.CREATED_ON, match.get().getCreatedAt());
                response.setResult(result);
                response.setResponseCode(HttpStatus.OK);
                response.getParams().setStatus(Constants.SUCCESSFUL);
                return response;
            }

            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("form data not found for " + type + " " + subtype + " and " + portal);
            response.setResponseCode(HttpStatus.NOT_FOUND);
            return response;

        } catch (Exception e) {
            log.error("Failed to read form Read: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg(Constants.FAILED);
            response.getParams().setStatus(Constants.FAILED);
        }

        return response;
    }



    /**
     * @param isAdmin whether the caller is an admin — only admin callers get "criteria" back in the
     *                result; it's internal scoping metadata that regular callers don't need to see.
     */
    private Map<String, Object> buildResult(FormConfigurationEntity formConfigurationEntity, boolean isAdmin) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, formConfigurationEntity.getName());
        result.put(Constants.TYPE, formConfigurationEntity.getType());
        result.put(Constants.SUBTYPE, formConfigurationEntity.getSubtype());
        result.put(Constants.PORTAL, formConfigurationEntity.getPortal());
        Map<String, Object> dataMap = objectMapper.convertValue(formConfigurationEntity.getData(), Map.class);
        result.put(Constants.CLIENT_VERSION, formConfigurationEntity.getClientVersion());
        result.put(Constants.DATA, dataMap);
        if (isAdmin) {
            Map<String, Object> criteriaMap = objectMapper.convertValue(formConfigurationEntity.getCriteria(), Map.class);
            result.put(Constants.CRITERIA, criteriaMap);
        }
        return result;
    }

    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }

    /**
     * Clears every FormConfigRuleEngine cache entry for a (type, subtype, portal, clientVersion)
     * tuple, regardless of which rule/rootOrg/role/designation matched it — the exact key a given
     * entity was cached under depends on the resolving caller, not the entity itself.
     */
    private void invalidateFormConfigCache(String type, String subtype, String portal, Double clientVersion) {
        String pattern = String.join(Constants.DOT_SEPARATOR,
                Constants.FORM_CONFIG_RESULT, "*", type, subtype, portal, String.valueOf(clientVersion), "*");
        cacheService.deleteCacheByPattern(pattern);
        // Redis is only L2 now: drop this pod's L1 directly and tell the other pods to do the same.
        formConfigLocalCache.invalidateAll();
        cacheService.publishInvalidate();
    }

    /**
     * Two rows for the same (type, subtype, portal, clientVersion) are a "duplicate criteria" if the
     * rule engine (FormConfigRuleEngine / §4.3 of the design doc) could resolve either one to the same
     * incoming request, making the match non-deterministic. The two underlying rules are mutually
     * exclusive on "designation" (getDefaultFormConfigDataByCriteria explicitly requires
     * criteria->'designation' IS NULL), so which check applies depends on whether either row declares one:
     *   - both criteria absent/null (both would fall to NoCriteriaConfigurationRule), or
     *   - EITHER row carries a non-empty "designation" array: only reachable via
     *     DesignationConfigurationRule, so role/rootOrg equality is irrelevant here — flag it only when
     *     both rows' designations overlap AND either leaves "ministryOrStateType" unset (a legacy/unscoped
     *     row matches every caller) or their "ministryOrStateType" arrays overlap. Two rows with the same
     *     designation list but disjoint ministryOrStateType (e.g. one "ministry", one "state") are
     *     intentionally NOT duplicates, or
     *   - NEITHER row carries a designation: both exclusively reachable via DefaultConfigurationRule, so
     *     matching role + rootOrg is a real ambiguity.
     */
    private boolean isDuplicateCriteria(JsonNode newCriteria, JsonNode existingCriteria) {
        boolean newBlank = newCriteria == null || newCriteria.isNull();
        boolean existingBlank = existingCriteria == null || existingCriteria.isNull();
        if (newBlank && existingBlank) {
            return true;
        }
        if (newBlank || existingBlank) {
            return false;
        }

        List<String> newDesignations = extractStringArray(newCriteria, Constants.DESIGNATION);
        List<String> existingDesignations = extractStringArray(existingCriteria, Constants.DESIGNATION);

        if (!newDesignations.isEmpty() || !existingDesignations.isEmpty()) {
            // At least one row is designation-scoped, so it's exclusively handled by
            // DesignationConfigurationRule at read time — role/rootOrg equality with a row that has no
            // (or a non-overlapping) designation is not a real ambiguity, so don't fall through to that
            // check below for this pair.
            if (newDesignations.isEmpty() || existingDesignations.isEmpty()
                    || Collections.disjoint(newDesignations, existingDesignations)) {
                return false;
            }
            List<String> newMinistryOrStateType = extractStringArray(newCriteria, Constants.MINISTRY_OR_STATE_TYPE);
            List<String> existingMinistryOrStateType = extractStringArray(existingCriteria, Constants.MINISTRY_OR_STATE_TYPE);
            return newMinistryOrStateType.isEmpty() || existingMinistryOrStateType.isEmpty()
                    || !Collections.disjoint(newMinistryOrStateType, existingMinistryOrStateType);
        }

        String newRole = criteriaTextValue(newCriteria, Constants.ROLE);
        String newRootOrg = criteriaTextValue(newCriteria, Constants.ROOTORG);
        String existingRole = criteriaTextValue(existingCriteria, Constants.ROLE);
        String existingRootOrg = criteriaTextValue(existingCriteria, Constants.ROOTORG);
        return StringUtils.isNotBlank(newRole) && StringUtils.isNotBlank(newRootOrg)
                && newRole.equalsIgnoreCase(existingRole) && newRootOrg.equalsIgnoreCase(existingRootOrg);
    }

    private List<String> extractStringArray(JsonNode criteria, String field) {
        JsonNode arrayNode = criteria.get(field);
        if (arrayNode == null || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (node != null && !node.isNull() && StringUtils.isNotBlank(node.asText())) {
                values.add(node.asText());
            }
        }
        return values;
    }

    private String criteriaTextValue(JsonNode criteria, String field) {
        JsonNode node = criteria.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    /**
     * Checks the candidate criteria against every existing row sharing the same
     * (type, subtype, portal, clientVersion), excluding the row being updated (if any).
     */
    private boolean hasDuplicateCriteria(String type, String subtype, String portal, Double clientVersion,
                                          JsonNode candidateCriteria, Long excludeId) {
        List<FormConfigurationEntity> sameScopeConfigs = formConfigurationRepository
                .findByTypeAndSubtypeAndPortalAndClientVersion(type, subtype, portal, clientVersion);
        return sameScopeConfigs.stream()
                .filter(existing -> excludeId == null || !excludeId.equals(existing.getId()))
                .anyMatch(existing -> isDuplicateCriteria(candidateCriteria, existing.getCriteria()));
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

            String name = requestData.get(Constants.NAME).toString();
            if (formConfigurationRepository.existsByName(name)) {
                ProjectUtil.returnErrorMsg(Constants.ResponseMessages.FIELD_NAME_ALREADY_EXISTS, HttpStatus.CONFLICT, response, Constants.FAILED);
                return response;
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            FormConfigurationEntity configurationEntity = new FormConfigurationEntity();
            configurationEntity.setCreatedAt(formattedCurrentTime);
            configurationEntity.setUpdatedAt(formattedCurrentTime);
            configurationEntity.setCreatedBy(userDetails.getUserId());
            configurationEntity.setUpdatedBy(userDetails.getUserId());
            configurationEntity.setName(name);
            configurationEntity.setType(requestData.get(Constants.TYPE).toString());
            configurationEntity.setPortal(requestData.get(Constants.PORTAL).toString());
            configurationEntity.setSubtype(requestData.get(Constants.SUBTYPE).toString());
            configurationEntity.setClientVersion(Double.valueOf(requestData.get(Constants.CLIENT_VERSION).toString()));
            if (requestData.containsKey(Constants.CRITERIA)) {
                configurationEntity.setCriteria(objectMapper.valueToTree(requestData.get(Constants.CRITERIA)));
            }
            if (requestData.containsKey(Constants.DATA)) {
                configurationEntity.setData(objectMapper.valueToTree(requestData.get(Constants.DATA)));
            }

            if (hasDuplicateCriteria(configurationEntity.getType(), configurationEntity.getSubtype(),
                    configurationEntity.getPortal(), configurationEntity.getClientVersion(),
                    configurationEntity.getCriteria(), null)) {
                ProjectUtil.returnErrorMsg(Constants.ResponseMessages.FIELD_CRITERIA_ALREADY_EXISTS, HttpStatus.CONFLICT, response, Constants.FAILED);
                return response;
            }

            // Save to database
            formConfigurationRepository.save(configurationEntity);

            Map<String, Object> result = new HashMap<>();
            result.put("id", configurationEntity.getId());
            result.put(Constants.NAME, configurationEntity.getName());
            result.put(Constants.TYPE, configurationEntity.getType());
            result.put(Constants.SUBTYPE, configurationEntity.getSubtype());
            result.put(Constants.PORTAL, configurationEntity.getPortal());
            result.put(Constants.CLIENT_VERSION, configurationEntity.getClientVersion());
            if (configurationEntity.getCriteria() != null) {
                result.put(Constants.CRITERIA, objectMapper.convertValue(configurationEntity.getCriteria(), Map.class));
            }
            if (configurationEntity.getData() != null) {
                result.put(Constants.DATA, objectMapper.convertValue(configurationEntity.getData(), Map.class));
            }

            // Set success response
            response.put(Constants.CREATED_ON, configurationEntity.getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);

        } catch (Exception e) {
            log.error("Failed to create createFormConfigV2: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(Constants.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readFormConfigById(Long formId, String token, boolean isAdmin) {
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

            Map<String, Object> result = buildResult(entityOpt.get(), isAdmin);
            response.put(Constants.CREATED_ON, entityOpt.get().getCreatedAt());
            response.setResult(result);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESSFUL);
        } catch (Exception e) {
            log.error("Failed to read form by id: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg(Constants.FAILED);
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
            String oldType = originalData.getType();
            String oldSubtype = originalData.getSubtype();
            String oldPortal = originalData.getPortal();
            Double oldClientVersion = originalData.getClientVersion();
            JsonNode oldCriteria = originalData.getCriteria();

            if (requestData.containsKey(Constants.NAME)) {
                String newName = requestData.get(Constants.NAME).toString();
                if (formConfigurationRepository.existsByNameAndIdNot(newName, originalData.getId())) {
                    ProjectUtil.returnErrorMsg(Constants.ResponseMessages.FIELD_NAME_ALREADY_EXISTS, HttpStatus.CONFLICT, response, Constants.FAILED);
                    return response;
                }
                originalData.setName(newName);
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

            // Only re-check for a criteria conflict when the (scope, criteria) tuple this row occupies
            // actually changed by this update. A data/name-only update on a row whose criteria already
            // pre-dates the uniqueness constraint (e.g. a legacy duplicate) must still be allowed to
            // succeed — it isn't introducing a new conflict, just leaving the existing one untouched.
            boolean scopeChanged = !Objects.equals(oldType, originalData.getType())
                    || !Objects.equals(oldSubtype, originalData.getSubtype())
                    || !Objects.equals(oldPortal, originalData.getPortal())
                    || !Objects.equals(oldClientVersion, originalData.getClientVersion());
            boolean criteriaChanged = requestData.containsKey(Constants.CRITERIA) && !Objects.equals(oldCriteria, originalData.getCriteria());
            if ((scopeChanged || criteriaChanged) && hasDuplicateCriteria(originalData.getType(), originalData.getSubtype(), originalData.getPortal(),
                    originalData.getClientVersion(), originalData.getCriteria(), originalData.getId())) {
                ProjectUtil.returnErrorMsg(Constants.ResponseMessages.FIELD_CRITERIA_ALREADY_EXISTS, HttpStatus.CONFLICT, response, Constants.FAILED);
                return response;
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            originalData.setUpdatedBy(userDetails.getUserId());
            originalData.setUpdatedAt(formattedCurrentTime);

            formConfigurationRepository.save(originalData);

            // The rule engine caches resolved entities keyed by (type, subtype, portal, clientVersion,
            // ...); invalidate under the old tuple (covers the common case of criteria/data changing
            // in place) and, if any of those four fields changed, also under the new tuple.
            invalidateFormConfigCache(oldType, oldSubtype, oldPortal, oldClientVersion);
            if (!Objects.equals(oldType, originalData.getType()) || !Objects.equals(oldSubtype, originalData.getSubtype())
                    || !Objects.equals(oldPortal, originalData.getPortal()) || !Objects.equals(oldClientVersion, originalData.getClientVersion())) {
                invalidateFormConfigCache(originalData.getType(), originalData.getSubtype(), originalData.getPortal(), originalData.getClientVersion());
            }

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
            log.error("Failed to updateFormConfigV2: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(Constants.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
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
            response.getParams().setErrMsg(Constants.FAILED);
            response.getParams().setStatus(Constants.FAILED);
        }
        return response;
    }

}
