package com.igot.cb.formConfiguration.rule;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;

import java.util.Optional;

/**
 * One matching "dimension" for resolving a form configuration. The {@link FormConfigRuleEngine}
 * holds every bean implementing this interface and orders them by priority (highest first, looked
 * up dynamically from {@link FormConfigRuleProperties} by {@link #ruleName()}), trying each in turn
 * until one produces a match. Adding a new matching dimension means adding a new implementation of
 * this interface plus a {@code form.config.rule.scores.<ruleName>} entry — nothing else changes.
 */
public interface FormConfigLookupRule {

    /** Stable id — used as the cache-key namespace, and as the lookup key into {@link FormConfigRuleProperties}. */
    String ruleName();

    /** Does this rule apply given the resolved context? */
    boolean supports(FormConfigResolutionContext ctx);

    Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx);

    String buildCacheKey(FormConfigResolutionContext ctx);
}
