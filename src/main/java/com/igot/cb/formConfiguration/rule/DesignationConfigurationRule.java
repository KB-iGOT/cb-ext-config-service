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
 * Rule 1 — highest priority. Matches a form config scoped purely to the caller's designation.
 * {@code ctx.getDesignationRootOrg()} is an eligibility gate only — priority-resolved upstream in
 * FormsConfigurationServiceImpl (non-null when the caller's own rootOrg, or the request's explicit
 * rootOrg, resolves via org-read to "ministry"/"state") — it is never matched against the row's
 * criteria. Only applies when the caller has at least one designation and that gate passed; falls
 * through to {@link DefaultConfigurationRule} otherwise or when no such row exists.
 */
@Component
public class DesignationConfigurationRule implements FormConfigLookupRule {

    private static final String RULE_ID = "designationMinistry";

    @Autowired
    private FormConfigurationRepository repository;

    @Override
    public String ruleName() {
        return RULE_ID;
    }

    @Override
    public boolean supports(FormConfigResolutionContext ctx) {
        return CollectionUtils.isNotEmpty(ctx.getDesignations()) && StringUtils.isNotBlank(ctx.getDesignationRootOrg());
    }

    @Override
    public Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx) {
        return repository.getFormConfigByDesignation(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(), ctx.getDesignations()
        );
    }

    @Override
    public String buildCacheKey(FormConfigResolutionContext ctx) {
        String designationSegment = ctx.getDesignations().stream()
                .filter(StringUtils::isNotBlank)
                .sorted()
                .collect(Collectors.joining("|"));
        return FormConfigCacheKeys.build(RULE_ID, ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                null, null, designationSegment);
    }
}
