package com.igot.cb.formConfiguration.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class FormConfigurationEntityTest {

    @Test
    void noArgsConstructor_leavesFieldsNull() {
        FormConfigurationEntity entity = new FormConfigurationEntity();

        assertNull(entity.getId());
        assertNull(entity.getName());
    }

    @Test
    void allArgsConstructor_setsAllFields() {
        JsonNode criteria = mock(JsonNode.class);
        JsonNode data = mock(JsonNode.class);

        FormConfigurationEntity entity = new FormConfigurationEntity(
                1L, "name", "type", "subtype", "portal", criteria, data, 1.0,
                "createdAt", "updatedAt", "createdBy", "updatedBy");

        assertEquals(1L, entity.getId());
        assertEquals("name", entity.getName());
        assertEquals("type", entity.getType());
        assertEquals("subtype", entity.getSubtype());
        assertEquals("portal", entity.getPortal());
        assertEquals(criteria, entity.getCriteria());
        assertEquals(data, entity.getData());
        assertEquals(1.0, entity.getClientVersion());
        assertEquals("createdAt", entity.getCreatedAt());
        assertEquals("updatedAt", entity.getUpdatedAt());
        assertEquals("createdBy", entity.getCreatedBy());
        assertEquals("updatedBy", entity.getUpdatedBy());
    }

    @Test
    void settersAndGetters_roundTrip() {
        FormConfigurationEntity entity = new FormConfigurationEntity();

        entity.setId(2L);
        entity.setName("name2");
        entity.setType("type2");
        entity.setSubtype("subtype2");
        entity.setPortal("portal2");
        entity.setClientVersion(2.0);
        entity.setCreatedAt("createdAt2");
        entity.setUpdatedAt("updatedAt2");
        entity.setCreatedBy("createdBy2");
        entity.setUpdatedBy("updatedBy2");

        assertEquals(2L, entity.getId());
        assertEquals("name2", entity.getName());
        assertEquals("type2", entity.getType());
        assertEquals("subtype2", entity.getSubtype());
        assertEquals("portal2", entity.getPortal());
        assertEquals(2.0, entity.getClientVersion());
        assertEquals("createdAt2", entity.getCreatedAt());
        assertEquals("updatedAt2", entity.getUpdatedAt());
        assertEquals("createdBy2", entity.getCreatedBy());
        assertEquals("updatedBy2", entity.getUpdatedBy());
    }
}
