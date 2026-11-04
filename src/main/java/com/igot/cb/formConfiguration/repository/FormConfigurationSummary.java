package com.igot.cb.formConfiguration.repository;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Projection for the form catalogue: every column except {@code data}, so building the catalogue
 * never loads form bodies.
 */
public interface FormConfigurationSummary {
    Long getId();
    String getName();
    String getType();
    String getSubtype();
    String getPortal();
    Double getClientVersion();
    JsonNode getCriteria();
}
