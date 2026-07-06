package com.igot.cb.formConfiguration.service;

import com.igot.cb.util.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface FormsConfigurationService {
    ApiResponse createFormConfig(Map<String, Object> request, String token);

    ApiResponse readFormConfig(Map<String, Object> request, String token, String org, List<String> userRoles,boolean isAdmin);

    ApiResponse updateFormConfig(Map<String, Object> formConfigData, String token);
}
