package com.igot.cb.formConfiguration.service;

import com.igot.cb.util.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface FormsConfigurationService {

    ApiResponse readFormConfig(Map<String, Object> request, String token, String org, List<String> userRoles,boolean isAdmin);

    ApiResponse createFormConfigV2(Map<String, Object> request, String token);

    ApiResponse readFormConfigById(Long formId, String token, boolean isAdmin);

    ApiResponse updateFormConfigV2(Map<String, Object> request, String token);

    ApiResponse listFormConfigs(String token);
}
