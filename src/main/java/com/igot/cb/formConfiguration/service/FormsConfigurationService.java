package com.igot.cb.formConfiguration.service;

import com.igot.cb.util.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface FormsConfigurationService {

    ApiResponse readFormConfig(Map<String, Object> request, String token, String org, List<String> userRoles,boolean isAdmin);

    ApiResponse createFormConfigV2(Map<String, Object> request, String token);

    /**
     * Create by criteria: resolves whether a matching row already exists via
     * {@link com.igot.cb.formConfiguration.rule.FormConfigRuleEngine} (the same designation+ministry /
     * role+rootOrg matching used by reads) and updates that row instead of inserting a duplicate.
     */
    ApiResponse createFormConfig(Map<String, Object> request, String token);

    /**
     * Update by criteria, resolved the same way as {@link #createFormConfig(Map, String)}.
     * Returns 404 if no matching row exists.
     */
    ApiResponse updateFormConfig(Map<String, Object> request, String token);

    ApiResponse readFormConfigById(Long formId, String token);

    ApiResponse updateFormConfigV2(Map<String, Object> request, String token);

    ApiResponse listFormConfigs(String token);
}
