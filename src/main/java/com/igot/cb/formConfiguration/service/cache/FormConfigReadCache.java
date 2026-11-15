package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.repository.FormConfigurationSummary;
import com.igot.cb.formConfiguration.rule.FormConfigCatalogProperties;
import com.igot.cb.util.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Two-tier read cache for the user-independent reads.
 *
 * Catalogue: in-memory (Caffeine) → Redis ({@link Constants#FORM_CONFIG_CATALOG_CACHE_KEY}) → DB.
 * Redis is consulted only on an in-memory miss, so steady-state reads never leave the JVM.
 * Read-by-id (non-admin result only): in-memory → Redis ({@link Constants#FORM_CONFIG_READ_CACHE_KEY}.id) → DB.
 * The finished result map is cached, so a hit does no entity mapping. Admin reads bypass this cache.
 *
 * Eviction ({@link #evictCatalog()} / {@link #evictForm(long)}) clears memory AND Redis, so the
 * next read is served fresh from the DB. The TTL bounds staleness on a pod that misses the
 * pub/sub eviction (restart, dropped subscriber connection).
 */
@Component
@Slf4j
public class FormConfigReadCache {

    private static final String CATALOG_KEY = "catalog";

    @Autowired
    private FormConfigurationRepository formConfigurationRepository;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FormConfigCatalogProperties catalogProperties;

    @Value("${form.config.local-cache.ttl-seconds:300}")
    private long ttlSeconds;

    @Value("${form.config.local-cache.max-forms:500}")
    private long maxForms;

    @Value("${form.config.read-cache.redis-ttl-seconds:7200}")
    private long formRedisTtlSeconds;

    private Cache<String, Map<String, Object>> catalogCache;
    private Cache<Long, Map<String, Object>> formCache;

    @PostConstruct
    void init() {
        catalogCache = Caffeine.newBuilder().maximumSize(1).expireAfterWrite(ttlSeconds, TimeUnit.SECONDS).build();
        formCache = Caffeine.newBuilder().maximumSize(maxForms).expireAfterWrite(ttlSeconds, TimeUnit.SECONDS).build();
    }

    public Map<String, Object> getCatalog() {
        return catalogCache.get(CATALOG_KEY, k -> loadCatalog());
    }

    /**
     * The catalogue narrowed to one role bucket and one client: {@code forms} becomes the flat list
     * for (role, portal) — empty when there is none — and the matching contract is carried over.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCatalog(String role, String portal) {
        Map<String, Object> full = getCatalog();
        List<Map<String, Object>> forms = List.of();
        Object buckets = full.get(Constants.FORMS);
        if (buckets instanceof Map<?, ?> byRole) {
            Object byPortal = byRole.get(role);
            if (byPortal instanceof Map<?, ?> portals) {
                Object list = portals.get(portal);
                if (list instanceof List<?> l) {
                    forms = (List<Map<String, Object>>) l;
                }
            }
        }
        Map<String, Object> slice = new LinkedHashMap<>();
        slice.put(Constants.RULE_PRIORITY, full.get(Constants.RULE_PRIORITY));
        slice.put(Constants.USER_FIELDS, full.get(Constants.USER_FIELDS));
        slice.put(Constants.VALUE_PRIORITY, full.get(Constants.VALUE_PRIORITY));
        slice.put(Constants.FORMS, forms);
        return slice;
    }

    /**
     * The non-admin read-by-id result for a form, or {@code null} when the form does not exist
     * (a miss is not cached). {@code resultBuilder} runs only when the row is read from the DB.
     * The returned map is shared across requests: copy it, do not modify it.
     */
    public Map<String, Object> getFormResult(long formId, Function<FormConfigurationEntity, Map<String, Object>> resultBuilder) {
        return formCache.get(formId, id -> loadFormResult(id, resultBuilder));
    }

    /** Writer side: drops the catalogue from memory and Redis; the next {@link #getCatalog()} rebuilds it from the DB. */
    public void evictCatalog() {
        catalogCache.invalidate(CATALOG_KEY);
        cacheService.deleteCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY);
        log.debug("FormConfigReadCache: catalogue evicted from memory and Redis");
    }

    /** Receiver side (pub/sub): the writer already cleared Redis, so only this pod's memory is dropped. */
    public void evictCatalogFromMemory() {
        catalogCache.invalidate(CATALOG_KEY);
        log.debug("FormConfigReadCache: catalogue evicted from memory");
    }

    /** Writer side: drops one form from memory and Redis; the next {@link #getFormResult} reads it from the DB. */
    public void evictForm(long formId) {
        formCache.invalidate(formId);
        cacheService.deleteCache(formKey(formId));
        log.debug("FormConfigReadCache: form {} evicted from memory and Redis", formId);
    }

    /** Receiver side (pub/sub): the writer already cleared Redis, so only this pod's memory is dropped. */
    public void evictFormFromMemory(long formId) {
        formCache.invalidate(formId);
        log.debug("FormConfigReadCache: form {} evicted from memory", formId);
    }

    private static String formKey(long formId) {
        return Constants.FORM_CONFIG_READ_CACHE_KEY + Constants.DOT_SEPARATOR + formId;
    }

    /** In-memory miss: try Redis (another pod may have built it), else build from the DB and share via Redis. */
    private Map<String, Object> loadFormResult(long formId, Function<FormConfigurationEntity, Map<String, Object>> resultBuilder) {
        String key = formKey(formId);
        String cached = cacheService.getCache(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("FormConfigReadCache: failed to deserialize cached form {}, rebuilding: {}", formId, e.getMessage());
            }
        }
        Optional<FormConfigurationEntity> entity = formConfigurationRepository.findById(formId);
        if (entity.isEmpty()) {
            return null;
        }
        Map<String, Object> result = resultBuilder.apply(entity.get());
        cacheService.putCache(key, result, formRedisTtlSeconds);
        return result;
    }

    /** In-memory miss: try Redis (another pod may have built it), else build from the DB and share via Redis. */
    private Map<String, Object> loadCatalog() {
        String cached = cacheService.getCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("FormConfigReadCache: failed to deserialize cached catalogue, rebuilding: {}", e.getMessage());
            }
        }
        Map<String, Object> catalog = buildFormCatalog();
        cacheService.putCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY, catalog);
        return catalog;
    }

    /**
     * Every row (minus {@code data}) grouped by {@code criteria.role} → {@code portal}, plus the
     * matching contract from {@link FormConfigCatalogProperties}. Rows with no role (no criteria)
     * are the defaults and appear in both the public and volunteer buckets; rows with any other
     * role are not part of the learner portal catalogue and are skipped.
     */
    private Map<String, Object> buildFormCatalog() {
        Map<String, Map<String, List<Map<String, Object>>>> forms = new LinkedHashMap<>();
        forms.put(Constants.ROLE_PUBLIC, new LinkedHashMap<>());
        forms.put(Constants.ROLE_VOLUNTEER, new LinkedHashMap<>());

        List<FormConfigurationSummary> rows = new ArrayList<>(formConfigurationRepository.findAllProjectedBy());
        rows.sort(Comparator.comparing(FormConfigurationSummary::getSubtype, Comparator.nullsLast(String::compareTo))
                .thenComparing(FormConfigurationSummary::getClientVersion, Comparator.nullsLast(Double::compareTo))
                .thenComparing(FormConfigurationSummary::getId));

        for (FormConfigurationSummary row : rows) {
            JsonNode criteria = row.getCriteria();
            String role = criteria == null || criteria.isNull() ? null : criteria.path(Constants.ROLE).asText(null);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.getId());
            entry.put(Constants.NAME, row.getName());
            entry.put(Constants.TYPE, row.getType());
            entry.put(Constants.SUBTYPE, row.getSubtype());
            entry.put(Constants.CLIENT_VERSION, row.getClientVersion());
            Map<String, Object> criteriaMap = criteria == null || criteria.isNull()
                    ? new LinkedHashMap<>()
                    : objectMapper.convertValue(criteria, new TypeReference<LinkedHashMap<String, Object>>() {});
            criteriaMap.remove(Constants.ROLE);
            entry.put(Constants.CRITERIA, criteriaMap);

            List<String> buckets;
            if (StringUtils.isBlank(role)) {
                buckets = List.of(Constants.ROLE_PUBLIC, Constants.ROLE_VOLUNTEER);
            } else if (Constants.ROLE_PUBLIC.equalsIgnoreCase(role) || Constants.ROLE_VOLUNTEER.equalsIgnoreCase(role)) {
                buckets = List.of(role.toLowerCase());
            } else {
                continue;
            }
            for (String bucket : buckets) {
                forms.get(bucket).computeIfAbsent(row.getPortal(), k -> new ArrayList<>()).add(entry);
            }
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put(Constants.RULE_PRIORITY, catalogProperties.getRulePriority());
        catalog.put(Constants.USER_FIELDS, catalogProperties.getUserFields());
        catalog.put(Constants.VALUE_PRIORITY, catalogProperties.getValuePriority());
        catalog.put(Constants.FORMS, forms);
        return catalog;
    }
}
