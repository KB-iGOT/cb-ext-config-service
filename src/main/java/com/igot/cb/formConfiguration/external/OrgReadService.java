package com.igot.cb.formConfiguration.external;

/**
 * Resolves org-hierarchy attributes (currently just ministryOrStateType) for a given rootOrgId by
 * calling the org-read service. Used by the form-config rule engine's designation+ministry rule.
 */
public interface OrgReadService {

    /**
     * @param rootOrgId the org id to look up (from the user's token/context)
     * @param token     caller's auth token, forwarded to the downstream call when available
     * @return the org's ministryOrStateType, or {@code null} if it can't be resolved
     */
    String getMinistryOrStateType(String rootOrgId, String token);
}
