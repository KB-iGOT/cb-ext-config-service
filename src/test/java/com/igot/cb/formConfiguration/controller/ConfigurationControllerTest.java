package com.igot.cb.formConfiguration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.service.FormsConfigurationService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigurationController.class)
class ConfigurationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FormsConfigurationService formsConfigurationService;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private RedisTemplate<String, String> redisTemplate;

  @MockBean
  FormConfigurationRepository formConfigurationRepository;

  @Test
  void readFormConfigForUser_shouldReturnOk() throws Exception {
    Map<String, Object> request = getRequest();

    ApiResponse response = new ApiResponse();
    response.setResponseCode(HttpStatus.OK);

    Mockito.when(formsConfigurationService.readFormConfig(
                    anyMap(),
                    anyString(),
                    isNull(),
                    isNull(),
                    eq(false)))
            .thenReturn(response);

    mockMvc.perform(post("/formsConfig/read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(Constants.Parameters.X_AUTH_TOKEN, "token")
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
  }

  private Map<String, Object> getRequest() {
    Map<String, Object> data = new HashMap<>();
    data.put("field", "value");
    data.put("name", "test");

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("rootOrg", "org1");
    criteria.put("roles", List.of("MDO_ADMIN"));

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put(Constants.TYPE, "page-web");
    requestBody.put(Constants.SUBTYPE, "player");
    requestBody.put(Constants.PORTAL, "mobile");
    requestBody.put(Constants.DATA, data);
    requestBody.put(Constants.CRITERIA, criteria);
    requestBody.put(Constants.CLIENT_VERSION, 1.0);

    Map<String, Object> finalRequest = new HashMap<>();
    finalRequest.put(Constants.Parameters.REQUEST, requestBody);

    return finalRequest;
  }
}
