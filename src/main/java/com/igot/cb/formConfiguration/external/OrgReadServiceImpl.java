package com.igot.cb.formConfiguration.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.service.cache.CacheService;
import com.igot.cb.util.Constants;
import com.igot.cb.util.PropertiesCache;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Calls the org-read service to resolve {@code ministryOrStateType} (e.g. "ministry", "state") for a
 * rootOrgId. Cached (org->ministry mappings are effectively static reference data) so the
 * designation+ministry rule doesn't hit the downstream service on every read.
 *
 * Contract (confirmed against the real service): {@code POST {baseUrl}} with body
 * {@code {"request": {"organisationId": "<id>"}}}; the field lives at
 * {@code result.response.ministryOrStateType} in the response.
 */
@Service
@Slf4j
public class OrgReadServiceImpl implements OrgReadService {

    private static final String CACHE_KEY_PREFIX = "org.ministryOrStateType.";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    private static PropertiesCache cache = PropertiesCache.getInstance();
    private static final String orgServiceEndpoint = cache.getProperty(Constants.LMS_SER_HOST)+Constants.ORG_READ_BASE_URL;

    @Override
    public String getMinistryOrStateType(String rootOrgId, String token) {
        if (StringUtils.isBlank(rootOrgId) || "*".equals(rootOrgId)) {
            return null;
        }
        String cacheKey = CACHE_KEY_PREFIX + rootOrgId;
        String cached = cacheService.getCache(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, String.class);
            } catch (Exception e) {
                log.warn("OrgReadServiceImpl: failed to deserialize cached ministryOrStateType for rootOrgId {}: {}", rootOrgId, e.getMessage());
            }
        }
        try {
           
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.isNotBlank(token)) {
                headers.set(Constants.HEADER_AUTHORIZATION, token);
            }
            Map<String, Object> body = Map.of(Constants.Parameters.REQUEST, Map.of(Constants.ORGANISATION_ID, rootOrgId));

            JsonNode response = restTemplate.postForObject(orgServiceEndpoint, new HttpEntity<>(body, headers), JsonNode.class);
            String ministryOrStateType = extractMinistryOrStateType(response);
            if (StringUtils.isNotBlank(ministryOrStateType)) {
                cacheService.putCache(cacheKey, ministryOrStateType);
            }
            return ministryOrStateType;
        } catch (Exception e) {
            log.warn("OrgReadServiceImpl: failed to resolve ministryOrStateType for rootOrgId {}: {}", rootOrgId, e.getMessage());
            return null;
        }
    }

    private String extractMinistryOrStateType(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode value = response.path("result").path("response").path(Constants.MINISTRY_OR_STATE_TYPE);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }
}