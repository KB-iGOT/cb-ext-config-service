package com.igot.cb.formConfiguration.rule;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 2 — fallback. Matches a form config by role + rootOrg (+ type/subtype/portal/clientVersion).
 * Tries the caller's own rootOrg first; if nothing matches there, falls back to the "*" (public)
 * rootOrg. Always applicable as long as role and rootOrg are known — this is the catch-all every
 * request eventually falls through to when the designation+ministry rule doesn't apply or misses.
 */
@Component
public class DefaultConfigurationRule implements FormConfigLookupRule {

    private static final String RULE_ID = "roleRootOrg";
    private static final String WILDCARD_ROOT_ORG = "*";

    @Autowired
    private FormConfigurationRepository repository;

    @Override
    public String ruleName() {
        return RULE_ID;
    }

    @Override
    public boolean supports(FormConfigResolutionContext ctx) {
        return CollectionUtils.isNotEmpty(ctx.getRoles()) && StringUtils.isNotBlank(ctx.getRootOrg());
    }

    @Override
    public Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx) {
        Optional<FormConfigurationEntity> found = repository.getDefaultFormConfigDataByCriteria(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getRootOrg(), ctx.getRoles(), ctx.getClientVersion()
        );
        if (found.isPresent() || WILDCARD_ROOT_ORG.equals(ctx.getRootOrg())) {
            return found;
        }
        return repository.getDefaultFormConfigDataByCriteria(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), WILDCARD_ROOT_ORG, ctx.getRoles(), ctx.getClientVersion()
        );
    }

    @Override
    public String buildCacheKey(FormConfigResolutionContext ctx) {
        return FormConfigCacheKeys.build(RULE_ID, ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                ctx.getRootOrg(), ctx.getRoles(), null);
    }
}
