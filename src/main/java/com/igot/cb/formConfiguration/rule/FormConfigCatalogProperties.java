package com.igot.cb.formConfiguration.rule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matching contract handed to the client with the form catalogue
 * (GET /formsConfig/v1/readFormDetails). The client evaluates each form's {@code criteria} against
 * the user profile using these three maps; the backend does no evaluation on that path.
 * Backed by {@code form.config.catalog.*} in application.properties.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "form.config.catalog")
public class FormConfigCatalogProperties {
    /** criteria keys in evaluation order, most significant first */
    private List<String> rulePriority = new ArrayList<>();
    /** criteria key → path in the user-read response (result.response) */
    private Map<String, String> userFields = new LinkedHashMap<>();
    /** criteria key → ordered list of values, most preferred first */
    private Map<String, List<String>> valuePriority = new LinkedHashMap<>();
}
