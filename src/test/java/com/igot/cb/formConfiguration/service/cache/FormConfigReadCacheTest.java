package com.igot.cb.formConfiguration.service.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import com.igot.cb.formConfiguration.repository.FormConfigurationSummary;
import com.igot.cb.formConfiguration.rule.FormConfigCatalogProperties;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormConfigReadCacheTest {

    @InjectMocks
    private FormConfigReadCache readCache;

    @Mock
    private FormConfigurationRepository repository;

    @Mock
    private CacheService cacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private FormConfigCatalogProperties catalogProperties = new FormConfigCatalogProperties();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(readCache, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(readCache, "maxForms", 500L);
        readCache.init();
        catalogProperties.setRulePriority(List.of("rootOrg", "designation", "ministryOrStateType"));
        catalogProperties.setUserFields(Map.of("rootOrg", "rootOrgId"));
    }

    private FormConfigurationSummary row(long id, String subtype, String portal, double version, Map<String, Object> criteria) {
        FormConfigurationSummary r = mock(FormConfigurationSummary.class, withSettings().lenient());
        when(r.getId()).thenReturn(id);
        when(r.getName()).thenReturn("form-" + id);
        when(r.getType()).thenReturn("page");
        when(r.getSubtype()).thenReturn(subtype);
        when(r.getPortal()).thenReturn(portal);
        when(r.getClientVersion()).thenReturn(version);
        JsonNode node = criteria == null ? null : new ObjectMapper().valueToTree(criteria);
        when(r.getCriteria()).thenReturn(node);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsCatalogue_groupedByRoleAndPortal_withRoleStripped_andDefaultsInBothBuckets() {
        // rows are built first: row() stubs its own mock, which must not happen inside another when(...)
        List<FormConfigurationSummary> rows = List.of(
                row(3, "home", "portal", 1.0, Map.of("role", "PUBLIC", "rootOrg", "*")),
                row(8, "home", "portal", 1.0, Map.of("role", "VOLUNTEER", "rootOrg", "*")),
                row(9, "home", "mobile", 1.0, Map.of("role", "public", "rootOrg", "*")),
                row(55, "globalenv", "portal", 1.0, null),
                row(99, "home", "portal", 1.0, Map.of("role", "MDO_ADMIN", "rootOrg", "*"))
        );
        when(cacheService.getCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY)).thenReturn(null);
        when(repository.findAllProjectedBy()).thenReturn(rows);

        Map<String, Object> catalog = readCache.getCatalog();

        assertEquals(List.of("rootOrg", "designation", "ministryOrStateType"), catalog.get(Constants.RULE_PRIORITY));
        Map<String, Map<String, List<Map<String, Object>>>> forms =
                (Map<String, Map<String, List<Map<String, Object>>>>) catalog.get(Constants.FORMS);

        List<Map<String, Object>> publicPortal = forms.get("public").get("portal");
        assertEquals(List.of(55L, 3L), publicPortal.stream().map(e -> e.get("id")).toList());
        assertEquals(List.of(55L, 8L), forms.get("volunteer").get("portal").stream().map(e -> e.get("id")).toList());
        assertEquals(List.of(9L), forms.get("public").get("mobile").stream().map(e -> e.get("id")).toList());
        assertNull(forms.get("volunteer").get("mobile"));

        Map<String, Object> home = publicPortal.get(1);
        assertEquals("home", home.get(Constants.SUBTYPE));
        assertEquals("page", home.get(Constants.TYPE));
        Map<String, Object> criteria = (Map<String, Object>) home.get(Constants.CRITERIA);
        assertEquals(Map.of("rootOrg", "*"), criteria);
        assertEquals(Map.of(), publicPortal.get(0).get(Constants.CRITERIA));

        verify(cacheService).putCache(eq(Constants.FORM_CONFIG_CATALOG_CACHE_KEY), eq(catalog));
    }

    @Test
    @SuppressWarnings("unchecked")
    void slice_returnsFlatListForRoleAndPortal_andEmptyWhenMissing() {
        List<FormConfigurationSummary> rows = List.of(
                row(3, "home", "portal", 1.0, Map.of("role", "PUBLIC", "rootOrg", "*")));
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.findAllProjectedBy()).thenReturn(rows);

        Map<String, Object> slice = readCache.getCatalog("public", "portal");
        assertEquals(1, ((List<?>) slice.get(Constants.FORMS)).size());
        assertEquals(3L, ((List<Map<String, Object>>) slice.get(Constants.FORMS)).get(0).get("id"));
        assertNotNull(slice.get(Constants.RULE_PRIORITY));

        assertEquals(List.of(), readCache.getCatalog("volunteer", "portal").get(Constants.FORMS));
        assertEquals(List.of(), readCache.getCatalog("public", "mobile").get(Constants.FORMS));
    }

    @Test
    void secondRead_isServedFromMemory_withoutRedisOrDb() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.findAllProjectedBy()).thenReturn(List.of());

        readCache.getCatalog();
        readCache.getCatalog();
        readCache.getCatalog("public", "portal");

        verify(repository, times(1)).findAllProjectedBy();
        verify(cacheService, times(1)).getCache(anyString());
    }

    @Test
    void memoryMiss_readsRedisBeforeDb() throws Exception {
        Map<String, Object> fromRedis = Map.of(Constants.RULE_PRIORITY, List.of("x"), Constants.FORMS, Map.of());
        when(cacheService.getCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY))
                .thenReturn(new ObjectMapper().writeValueAsString(fromRedis));

        Map<String, Object> catalog = readCache.getCatalog();

        assertEquals(List.of("x"), catalog.get(Constants.RULE_PRIORITY));
        verifyNoInteractions(repository);
    }

    @Test
    void evictCatalog_clearsMemoryAndRedis_thenRebuildsFromDb() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.findAllProjectedBy()).thenReturn(List.of());

        readCache.getCatalog();
        readCache.evictCatalog();
        readCache.getCatalog();

        verify(cacheService).deleteCache(Constants.FORM_CONFIG_CATALOG_CACHE_KEY);
        verify(repository, times(2)).findAllProjectedBy();
    }

    @Test
    void evictCatalogFromMemory_doesNotTouchRedis() {
        when(cacheService.getCache(anyString())).thenReturn(null);
        when(repository.findAllProjectedBy()).thenReturn(List.of());

        readCache.getCatalog();
        readCache.evictCatalogFromMemory();
        readCache.getCatalog();

        verify(cacheService, never()).deleteCache(anyString());
        verify(cacheService, times(2)).getCache(anyString());
    }
}
