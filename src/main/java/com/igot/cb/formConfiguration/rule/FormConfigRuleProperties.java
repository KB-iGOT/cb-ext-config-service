package com.igot.cb.formConfiguration.rule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Externalizes each {@link FormConfigLookupRule}'s priority (higher = tried first) so adding or
 * re-prioritizing a matching dimension is a config change, not a code change. Keyed by
 * {@link FormConfigLookupRule#ruleName()}. Backed by {@code form.config.rule.scores.*} in
 * application.properties, e.g. {@code form.config.rule.scores.designationMinistry=200}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "form.config.rule")
public class FormConfigRuleProperties {

    private Map<String, Integer> scores = new LinkedHashMap<>();
}
