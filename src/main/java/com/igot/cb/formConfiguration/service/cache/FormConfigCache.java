package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.util.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-JVM snapshot of the whole {@code form_configuration} table.
 * <p>
 * The table is tiny (tens of rows, well under a megabyte) and read at high TPS,
 * so the entire table is held in heap and reads never touch Postgres or Redis.
 * The snapshot is refreshed on write, on a Redis pub/sub invalidation message
 * from any pod (see {@link FormConfigCacheSubscriber}), and on a periodic
 * backstop in case an invalidation message is ever missed.
 */
@Component
@Slf4j
public class FormConfigCache {

    private final FormConfigurationRepository repository;
    private final ObjectMapper objectMapper;

    private volatile Map<String, CachedFormConfig> snapshot = Collections.emptyMap();
    private volatile boolean loaded = false;

    public FormConfigCache(FormConfigurationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * A pre-built read response for one form configuration row, so that no JSON
     * conversion happens per request.
     */
    public record CachedFormConfig(Map<String, Object> result, String createdAt) {
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Scheduled(fixedDelayString = "${formconfig.cache.refreshIntervalMs:300000}")
    public void scheduledRefresh() {
        reload();
    }

    /**
     * Rebuilds the snapshot from the database. On failure the previous snapshot is
     * retained, so a transient database problem does not empty the cache.
     */
    @Transactional(readOnly = true)
    public void reload() {
        try {
            List<FormConfigurationEntity> rows = repository.findAll(Sort.by(Sort.Direction.ASC, Constants.ID));
            Map<String, CachedFormConfig> next = new HashMap<>();
            int skipped = 0;
            for (FormConfigurationEntity entity : rows) {
                String key = keyOf(entity);
                if (key == null) {
                    skipped++;
                    continue;
                }
                if (next.put(key, toCached(entity)) != null) {
                    log.warn("Duplicate form_configuration rows resolve to key {}; highest id wins.", key);
                }
            }
            snapshot = Collections.unmodifiableMap(next);
            loaded = true;
            log.info("Form config cache loaded: {} keys from {} rows ({} skipped for missing criteria rootOrg/role).",
                    next.size(), rows.size(), skipped);
        } catch (Exception e) {
            log.error("Failed to reload form config cache; retaining previous snapshot of {} entries.",
                    snapshot.size(), e);
        }
    }

    /**
     * @return true once the table has been loaded at least once. When false, callers
     * must fall back to querying the database directly.
     */
    public boolean isLoaded() {
        return loaded;
    }

    public CachedFormConfig get(String key) {
        return snapshot.get(key);
    }

    /**
     * Builds the lookup key. Kept as the single definition used both when populating
     * the snapshot and when reading it.
     */
    public static String cacheKey(String type, String subtype, String portal, String userOrg, String userRole,
                                  Double clientVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.FORM_CONFIG_RESULT)
                .append(Constants.DOT_SEPARATOR).append(type)
                .append(Constants.DOT_SEPARATOR).append(subtype)
                .append(Constants.DOT_SEPARATOR).append(portal);
        if (userOrg != null) {
            sb.append(Constants.DOT_SEPARATOR).append(userOrg);
        }
        if (userRole != null) {
            sb.append(Constants.DOT_SEPARATOR).append(userRole);
        }
        if (clientVersion != null) {
            sb.append(Constants.DOT_SEPARATOR).append(clientVersion);
        }
        return sb.toString();
    }

    private String keyOf(FormConfigurationEntity entity) {
        JsonNode criteria = entity.getCriteria();
        if (criteria == null || !criteria.hasNonNull(Constants.ROOTORG) || !criteria.hasNonNull(Constants.ROLE)) {
            return null;
        }
        return cacheKey(entity.getType(), entity.getSubtype(), entity.getPortal(),
                criteria.get(Constants.ROOTORG).asText(), criteria.get(Constants.ROLE).asText(),
                entity.getClientVersion());
    }

    private CachedFormConfig toCached(FormConfigurationEntity entity) {
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.NAME, entity.getName());
        result.put(Constants.TYPE, entity.getType());
        result.put(Constants.SUBTYPE, entity.getSubtype());
        result.put(Constants.PORTAL, entity.getPortal());
        result.put(Constants.DATA, objectMapper.convertValue(entity.getData(), Map.class));
        return new CachedFormConfig(Collections.unmodifiableMap(result), entity.getCreatedAt());
    }
}
