package com.igot.cb.formConfiguration.controller;

import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/formsConfig")
public class ConfigurationController {

  @Autowired
  private FormsConfigurationService formsConfigurationService;

  @PostMapping("/create")
  public ResponseEntity<ApiResponse> createFormConfig(@RequestBody Map<String, Object> request,@RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.createFormConfig(request, token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PostMapping("/read")
  public ResponseEntity<ApiResponse> readFormConfig(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormConfig(request,token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PutMapping("/update")
  public ResponseEntity<ApiResponse> updateFormConfig(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.updateFormConfig(request,token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }
}
