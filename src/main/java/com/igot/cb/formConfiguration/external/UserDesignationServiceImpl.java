package com.igot.cb.formConfiguration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.authentication.model.UserDetails;
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
 * Confirmed contract: GET {@code {baseUrl}/{userId}}, response carrying the user's designations at
 * {@code result.response.profileDetails.professionalDetails[].designation} and the user's own
 * ministryOrStateId at {@code result.response.rootOrg.ministryOrStateId}.
 */
@Service
@Slf4j
public class UserDesignationServiceImpl implements UserDesignationService {

    @Autowired
    private RestTemplate restTemplate;

    private static PropertiesCache cache = PropertiesCache.getInstance();
    private static final String userReadEndpoint = cache.getProperty(Constants.LMS_SER_HOST)+Constants.USER_READ_BASE_URL;


    @Override
    public void resolveUserProfile(UserDetails userDetails, String token) {
        userDetails.setDesignations(List.of());
        String userId = userDetails.getUserId();
        if (StringUtils.isBlank(userId)) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(token)) {
                headers.set(Constants.Parameters.X_AUTH_TOKEN, token);
            }
            String url = userReadEndpoint + "/" + userId;
            JsonNode response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            userDetails.setDesignations(extractDesignations(response));
            userDetails.setMinistryOrStateType(extractMinistryOrStateType(response));
        } catch (Exception e) {
            log.warn("UserDesignationServiceImpl: failed to resolve user profile for userId {}: {}", userId, e.getMessage());
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

    private String extractMinistryOrStateType(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode value = response.path("result").path("response")
                .path("rootOrg").path(Constants.MINISTRY_OR_STATE_ID);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }
}
