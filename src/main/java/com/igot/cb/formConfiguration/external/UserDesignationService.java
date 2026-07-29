package com.igot.cb.formConfiguration.external;

import java.util.List;

/**
 * Resolves the designation(s) held by a user, by calling the user-read service. Used by the
 * form-config rule engine's designation+ministry rule.
 */
public interface UserDesignationService {

    /**
     * @param userId the user to look up
     * @param token  caller's auth token, forwarded to the downstream call when available
     * @return the user's designations, or an empty list if none/unresolvable
     */
    List<String> getDesignations(String userId, String token);
}
