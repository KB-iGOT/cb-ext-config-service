package com.igot.cb.formConfiguration.service;

import com.igot.cb.util.ApiResponse;

import java.util.Map;

public interface FormsConfigurationService {
    ApiResponse createFormConfig(Map<String, Object> request, String token);

    ApiResponse readFormConfig(Map<String, Object> request, String token);

    ApiResponse updateFormConfig(Map<String, Object> formConfigData, String token);
}
