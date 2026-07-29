package com.igot.cb.formConfiguration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Assumed contract: GET {@code {baseUrl}/{userId}}, response carrying the user's designations at
 * {@code result.response.profileDetails.professionalDetails[].designation}. Adjust the parsing in
 * {@link #extractDesignations(JsonNode)} if the real response shape differs.
 */
@Service
@Slf4j
public class UserDesignationServiceImpl implements UserDesignationService {

    @Autowired
    private RestTemplate restTemplate;

    private static PropertiesCache cache = PropertiesCache.getInstance();
    private static final String userReadEndpoint = cache.getProperty(Constants.LMS_SER_HOST)+Constants.USER_READ_BASE_URL;


    @Override
    public List<String> getDesignations(String userId, String token) {
        if (StringUtils.isBlank(userId)) {
            return List.of();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(token)) {
                headers.set(Constants.Parameters.X_AUTH_TOKEN, token);
            }
            String url = userReadEndpoint + "/" + userId;
            JsonNode response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            return extractDesignations(response);
        } catch (Exception e) {
            log.warn("UserDesignationServiceImpl: failed to resolve designations for userId {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private List<String> extractDesignations(JsonNode response) {
        List<String> designations = new ArrayList<>();
        if (response == null) {
            return designations;
        }
        JsonNode professionalDetails = response.path("result").path("response")
                .path("profileDetails").path("professionalDetails");
        if (professionalDetails.isArray()) {
            for (JsonNode detail : professionalDetails) {
                String designation = detail.path(Constants.DESIGNATION).asText(null);
                if (StringUtils.isNotBlank(designation)) {
                    designations.add(designation);
                }
            }
        }
        return designations;
    }
}
