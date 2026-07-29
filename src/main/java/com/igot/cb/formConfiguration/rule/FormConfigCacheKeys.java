package com.igot.cb.formConfiguration.rule;

import com.igot.cb.util.Constants;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a distinct Redis key namespace per rule, so each rule's cached entries can be looked up
 * and invalidated independently of every other rule.
 */
final class FormConfigCacheKeys {

    private FormConfigCacheKeys() {
    }

    /**
     * @param dimension the rule's own matching dimension value (rootOrg for the role rule,
     *                   ministryOrStateType for the designation rule) — {@code "_"} if none.
     * @param roles     rolled into the key when the rule matches on role; pass {@code null} otherwise.
     * @param designationSegment pre-joined (sorted, pipe-separated) designation values; pass
     *                           {@code null}/blank when the rule doesn't match on designation.
     */
    static String build(String ruleId, String type, String subtype, String portal, Double clientVersion,
                         String dimension, List<String> roles, String designationSegment) {
        String roleSegment = roles == null || roles.isEmpty()
                ? "_"
                : roles.stream().filter(StringUtils::isNotBlank).sorted().collect(Collectors.joining("|"));

        StringBuilder key = new StringBuilder(Constants.FORM_CONFIG_RESULT)
                .append(Constants.DOT_SEPARATOR).append(ruleId)
                .append(Constants.DOT_SEPARATOR).append(type)
                .append(Constants.DOT_SEPARATOR).append(subtype)
                .append(Constants.DOT_SEPARATOR).append(portal)
                .append(Constants.DOT_SEPARATOR).append(clientVersion)
                .append(Constants.DOT_SEPARATOR).append(dimension == null ? "_" : dimension)
                .append(Constants.DOT_SEPARATOR).append(roleSegment);

        if (StringUtils.isNotBlank(designationSegment)) {
            key.append(Constants.DOT_SEPARATOR).append(sha256(designationSegment));
        }
        return key.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
