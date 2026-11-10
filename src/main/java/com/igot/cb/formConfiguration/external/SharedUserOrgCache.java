package com.igot.cb.formConfiguration.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read-only client for the caches owned by sunbird-lms-service:
 *
 * <pre>
 *   userprofile:{userId}   hash, field "info" = raw sunbird.user row   (user_profile_redis_*)
 *   org:{orgId}            string, raw sunbird.organisation row        (org_info_redis_*)
 * </pre>
 *
 * This service never writes these keys; lms populates and evicts them. Each cache is disabled
 * when its host is blank, and every failure is a miss so callers fall back to the lms APIs.
 * The Lettuce factories are kept as plain fields (not beans) so the service's own autoconfigured
 * Redis beans are untouched.
 */
@Component
@Slf4j
public class SharedUserOrgCache {

    public static final String USER_KEY_PREFIX = "userprofile:";
    public static final String USER_FIELD_INFO = "info";
    public static final String ORG_KEY_PREFIX = "org:";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${user_profile_redis_host:}") private String userHost;
    @Value("${user_profile_redis_port:6379}") private int userPort;
    @Value("${user_profile_redis_index:0}") private int userIndex;
    @Value("${user_profile_redis_password_enabled:false}") private boolean userPasswordEnabled;
    @Value("${user_profile_redis_username:}") private String userUsername;
    @Value("${user_profile_redis_password:}") private String userPassword;

    @Value("${org_info_redis_host:}") private String orgHost;
    @Value("${org_info_redis_port:6379}") private int orgPort;
    @Value("${org_info_redis_index:0}") private int orgIndex;
    @Value("${org_info_redis_password_enabled:false}") private boolean orgPasswordEnabled;
    @Value("${org_info_redis_username:}") private String orgUsername;
    @Value("${org_info_redis_password:}") private String orgPassword;

    private LettuceConnectionFactory userFactory;
    private LettuceConnectionFactory orgFactory;
    private StringRedisTemplate userTemplate;
    private StringRedisTemplate orgTemplate;

    @PostConstruct
    void init() {
        if (StringUtils.isNotBlank(userHost)) {
            userFactory = factory(userHost, userPort, userIndex, userPasswordEnabled, userUsername, userPassword);
            userTemplate = template(userFactory);
            log.info("SharedUserOrgCache: user cache enabled host={} port={} index={}", userHost, userPort, userIndex);
        } else {
            log.info("SharedUserOrgCache: user_profile_redis_host not set, user cache disabled");
        }
        if (StringUtils.isNotBlank(orgHost)) {
            orgFactory = factory(orgHost, orgPort, orgIndex, orgPasswordEnabled, orgUsername, orgPassword);
            orgTemplate = template(orgFactory);
            log.info("SharedUserOrgCache: org cache enabled host={} port={} index={}", orgHost, orgPort, orgIndex);
        } else {
            log.info("SharedUserOrgCache: org_info_redis_host not set, org cache disabled");
        }
    }

    @PreDestroy
    void destroy() {
        if (userFactory != null) userFactory.destroy();
        if (orgFactory != null) orgFactory.destroy();
    }

    public boolean isUserCacheEnabled() { return userTemplate != null; }
    public boolean isOrgCacheEnabled() { return orgTemplate != null; }

    /** @return the raw sunbird.user row from the shared cache, or {@code null} on miss / disabled / error. */
    public Map<String, Object> getUserInfo(String userId) {
        if (userTemplate == null || StringUtils.isBlank(userId)) {
            return null;
        }
        try {
            Object json = userTemplate.opsForHash().get(USER_KEY_PREFIX + userId, USER_FIELD_INFO);
            return json == null ? null : mapper.readValue(json.toString(), MAP_TYPE);
        } catch (Exception e) {
            log.warn("SharedUserOrgCache: user info read failed for {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Designations from the cached user row: {@code profileDetails.professionalDetails[].designation}.
     * profileDetails is stored as the JSON text column value.
     *
     * @return the list (possibly empty) on a hit, or {@code null} on miss so the caller falls back.
     */
    public List<String> getUserDesignations(String userId) {
        Map<String, Object> user = getUserInfo(userId);
        if (user == null) {
            return null;
        }
        List<String> designations = new ArrayList<>();
        try {
            Object profileDetailsValue = findIgnoreCase(user, "profileDetails");
            Map<String, Object> profileDetails;
            if (profileDetailsValue instanceof Map) {
                profileDetails = (Map<String, Object>) profileDetailsValue;
            } else if (profileDetailsValue instanceof String && StringUtils.isNotBlank((String) profileDetailsValue)) {
                profileDetails = mapper.readValue((String) profileDetailsValue, MAP_TYPE);
            } else {
                return designations;
            }
            Object professionalDetails = profileDetails.get("professionalDetails");
            if (professionalDetails instanceof List) {
                for (Object detail : (List<?>) professionalDetails) {
                    if (detail instanceof Map) {
                        Object designation = ((Map<?, ?>) detail).get("designation");
                        if (designation != null && StringUtils.isNotBlank(designation.toString())) {
                            designations.add(designation.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("SharedUserOrgCache: could not parse profileDetails for {}: {}", userId, e.getMessage());
            return null;
        }
        return designations;
    }

    /** @return the raw sunbird.organisation row from the shared cache, or {@code null} on miss / disabled / error. */
    public Map<String, Object> getOrg(String orgId) {
        if (orgTemplate == null || StringUtils.isBlank(orgId)) {
            return null;
        }
        try {
            String json = orgTemplate.opsForValue().get(ORG_KEY_PREFIX + orgId);
            return json == null ? null : mapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("SharedUserOrgCache: org read failed for {}: {}", orgId, e.getMessage());
            return null;
        }
    }

    /** Case-insensitive lookup; lms caches rows with the Cassandra util's column casing. */
    public static Object findIgnoreCase(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static LettuceConnectionFactory factory(String host, int port, int index, boolean passwordEnabled,
                                                    String username, String password) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host.trim(), port);
        cfg.setDatabase(index);
        if (passwordEnabled) {
            if (StringUtils.isNotBlank(username)) {
                cfg.setUsername(username.trim());
            }
            cfg.setPassword(password);
        }
        LettuceClientConfiguration clientCfg = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        LettuceConnectionFactory f = new LettuceConnectionFactory(cfg, clientCfg);
        f.afterPropertiesSet();
        f.start();
        return f;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory f) {
        StringRedisTemplate t = new StringRedisTemplate(f);
        t.afterPropertiesSet();
        return t;
    }
}
