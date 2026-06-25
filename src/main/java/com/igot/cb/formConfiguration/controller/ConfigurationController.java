package com.igot.cb.formConfiguration.controller;

import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
  public ResponseEntity<ApiResponse> readFormConfigForUser(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormConfig(request,token,null,null,false);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PutMapping("/update")
  public ResponseEntity<ApiResponse> updateFormConfig(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.updateFormConfig(request,token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PostMapping("/admin/read")
  public ResponseEntity<ApiResponse> readFormConfigForAdmin(
          @RequestBody Map<String, Object> request,
          @PathVariable("userId") String  authTokenOrUserId,
          @RequestHeader(Constants.X_AUTH_USER_ORG_ID) String userOrgId,
          @RequestHeader(Constants.X_AUTH_USER_ROLES) List<String> userRoles) {
    ApiResponse response = formsConfigurationService.readFormConfig(request,authTokenOrUserId,userOrgId,userRoles,true);
    return new ResponseEntity<>(response, response.getResponseCode());
  }
}
