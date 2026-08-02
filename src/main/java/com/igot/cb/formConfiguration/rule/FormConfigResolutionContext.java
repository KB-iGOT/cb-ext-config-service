package com.igot.cb.formConfiguration.rule;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Everything a {@link FormConfigLookupRule} needs to decide whether it applies and, if so, find a
 * match. Resolved once per read request so every rule sees the same already-resolved data.
 */
@Getter
@Builder
public class FormConfigResolutionContext {
    private final String type;
    private final String subtype;
    private final String portal;
    private final Double clientVersion;

    private final List<String> roles;
    private final String rootOrg;

    private final List<String> designations;

    /**
     * Eligibility gate for the designation rule — never matched against a row's criteria, only
     * checked for non-blank. Priority-resolved in FormsConfigurationServiceImpl: set to the caller's
     * own rootOrg (from token) when org-read resolves it to a "ministry"; else to the request's
     * explicit rootOrg when that resolves to a "ministry"; else to the caller's own rootOrg again
     * when it resolves to a "state"; else {@code null} if none of those apply (designation rule
     * doesn't apply at all).
     */
    private final String designationRootOrg;
}
