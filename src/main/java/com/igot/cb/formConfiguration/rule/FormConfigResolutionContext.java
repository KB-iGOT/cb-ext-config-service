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
     * The org (from token or request) whose ministry/state resolution gates the designation rule.
     * Priority-resolved in FormsConfigurationServiceImpl: set to the caller's own rootOrg (from
     * token) when org-read resolves it to a "ministry"; else to the request's explicit rootOrg when
     * that resolves to a "ministry"; else to the caller's own rootOrg again when it resolves to a
     * "state"; else {@code null} if none of those apply (designation rule doesn't apply at all).
     * Kept for traceability alongside {@link #designationMinistryOrStateType}, which is what
     * actually gates/matches the rule.
     */
    private final String designationRootOrg;

    /**
     * "ministry" or "state" — the resolved type of {@link #designationRootOrg}, computed via the
     * same priority logic. Non-blank exactly when {@link #designationRootOrg} is non-null. Used both
     * as the designation rule's eligibility gate and as the value matched against a row's own
     * "ministryOrStateType" criteria field (rows that omit it are unscoped and match any caller).
     */
    private final String designationMinistryOrStateType;
}
