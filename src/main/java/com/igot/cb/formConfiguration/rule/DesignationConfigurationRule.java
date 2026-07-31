package com.igot.cb.formConfiguration.rule;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Rule 1 — highest priority. Matches a form config scoped to the caller's designation and a rootOrg
 * value chosen by whether the caller's ministryOrStateType has been verified (upstream, in
 * FormsConfigurationServiceImpl) to match their org's: the caller's actual rootOrg when verified, or
 * "*" (public) when it hasn't been. Only applies when the caller has at least one designation; falls
 * through to {@link DefaultConfigurationRule} otherwise or when no such row exists.
 */
@Component
public class DesignationConfigurationRule implements FormConfigLookupRule {

    private static final String RULE_ID = "designationMinistry";
    private static final String WILDCARD_ROOT_ORG = "*";

    @Autowired
    private FormConfigurationRepository repository;

    @Override
    public String ruleName() {
        return RULE_ID;
    }

    @Override
    public boolean supports(FormConfigResolutionContext ctx) {
        return CollectionUtils.isNotEmpty(ctx.getDesignations());
    }

    @Override
    public Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx) {
        String rootOrg = StringUtils.isNotBlank(ctx.getMinistryOrStateType()) ? ctx.getRootOrg() : WILDCARD_ROOT_ORG;
        return repository.getFormConfigByDesignationAndRootOrg(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(), rootOrg, ctx.getDesignations()
        );
    }

    @Override
    public String buildCacheKey(FormConfigResolutionContext ctx) {
        String designationSegment = ctx.getDesignations().stream()
                .filter(StringUtils::isNotBlank)
                .sorted()
                .collect(Collectors.joining("|"));
        String rootOrg = StringUtils.isNotBlank(ctx.getMinistryOrStateType()) ? ctx.getRootOrg() : WILDCARD_ROOT_ORG;
        return FormConfigCacheKeys.build(RULE_ID, ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                rootOrg, null, designationSegment);
    }
}
