package com.igot.cb.formConfiguration.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.formConfiguration.service.cache.CacheService;
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
 * {@code result.response.profileDetails.professionalDetails[].designation}.
 */
@Service
@Slf4j
public class UserDesignationServiceImpl implements UserDesignationService {

    private static final String CACHE_KEY_PREFIX = "user.designations.";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Short by design: unlike org->ministry mappings, a user's designations change when they edit
     * their profile, so this must not inherit the long default form-config TTL.
     */
    @Value("${user.designations.cacheTtlSeconds:300}")
    private long designationCacheTtlSeconds;

    private static PropertiesCache cache = PropertiesCache.getInstance();
    private static final String userReadEndpoint = cache.getProperty(Constants.LMS_SER_HOST)+Constants.USER_READ_BASE_URL;


    @Override
    public void resolveUserProfile(UserDetails userDetails, String token) {
        userDetails.setDesignations(List.of());
        String userId = userDetails.getUserId();
        if (StringUtils.isBlank(userId)) {
            return;
        }
        String cacheKey = CACHE_KEY_PREFIX + userId;
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            try {
                userDetails.setDesignations(objectMapper.readValue(cached, new TypeReference<List<String>>() {
                }));
                return;
            } catch (Exception e) {
                log.warn("UserDesignationServiceImpl: failed to deserialize cached designations for userId {}: {}", userId, e.getMessage());
            }
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(token)) {
                headers.set(Constants.Parameters.X_AUTH_TOKEN, token);
            }
            String url = userReadEndpoint + "/" + userId;
            JsonNode response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            List<String> designations = extractDesignations(response);
            userDetails.setDesignations(designations);
            // Only a successful call is cached — a failure must not be memoized as "no designations".
            cacheService.putCache(cacheKey, designations, designationCacheTtlSeconds);
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
}
