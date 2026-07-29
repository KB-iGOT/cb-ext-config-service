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
    private final String ministryOrStateType;
}
