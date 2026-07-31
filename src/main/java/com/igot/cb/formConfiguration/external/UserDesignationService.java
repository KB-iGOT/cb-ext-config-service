package com.igot.cb.formConfiguration.external;

import com.igot.cb.authentication.model.UserDetails;

/**
 * Resolves the designation(s) and ministryOrStateType (via the user's own rootOrg) held by a user,
 * by calling the user-read service. Used by the form-config rule engine's designation+ministry rule.
 */
public interface UserDesignationService {

    /**
     * Looks up {@code userDetails.getUserId()} via the user-read service and sets its designations
     * and ministryOrStateType fields in place (empty list / null if unresolvable). Does not touch
     * any other field on {@code userDetails}.
     *
     * @param userDetails the already-resolved user, mutated with designations + ministryOrStateType
     * @param token       caller's auth token, forwarded to the downstream call when available
     */
    void resolveUserProfile(UserDetails userDetails, String token);
}
