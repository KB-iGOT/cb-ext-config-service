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

  @PostMapping("/read")
  public ResponseEntity<ApiResponse> readFormConfigForUser(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormConfig(request,token,null,null,false);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  /**
   * Catalogue of the forms for one client ({@code portal} / {@code mobile}) and the caller's role
   * bucket (public / volunteer, from the JWT): id/name/type/subType/clientVersion/criteria, no data,
   * plus the matching contract (rulePriority/userFields/valuePriority). The client picks the form;
   * see {@link #readFormConfigByIdForUser}.
   */
  @GetMapping("/v1/readFormDetails/{portal}")
  public ResponseEntity<ApiResponse> readFormDetails(
          @PathVariable("portal") String portal,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormDetails(portal, token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  /** Non-admin read by id: same result shape as /read (no criteria), no profile-based resolution. */
  @GetMapping("/v1/read/{formId}")
  public ResponseEntity<ApiResponse> readFormConfigByIdForUser(
          @PathVariable("formId") Long formId,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormConfigById(formId, token, false);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PostMapping("/admin/read/{userId}")
  public ResponseEntity<ApiResponse> readFormConfigForAdmin(
          @RequestBody Map<String, Object> request,
          @PathVariable("userId") String  authTokenOrUserId,
          @RequestHeader(Constants.X_AUTH_USER_ORG_ID) String userOrgId,
          @RequestHeader(Constants.X_AUTH_USER_ROLES) List<String> userRoles) {
    ApiResponse response = formsConfigurationService.readFormConfig(request,authTokenOrUserId,userOrgId,userRoles,true);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PostMapping("/v2/create")
  public ResponseEntity<ApiResponse> createFormConfigV2(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.createFormConfigV2(request, token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @GetMapping("/v2/admin/read/{formId}")
  public ResponseEntity<ApiResponse> readFormConfigById(
          @PathVariable("formId") Long formId,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.readFormConfigById(formId, token, true);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @PutMapping("/v2/update")
  public ResponseEntity<ApiResponse> updateFormConfigV2(
          @RequestBody Map<String, Object> request,
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.updateFormConfigV2(request, token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }

  @GetMapping("/v2/admin/list")
  public ResponseEntity<ApiResponse> listFormConfigs(
          @RequestHeader(Constants.Parameters.X_AUTH_TOKEN) String token) {
    ApiResponse response = formsConfigurationService.listFormConfigs(token);
    return new ResponseEntity<>(response, response.getResponseCode());
  }
}
