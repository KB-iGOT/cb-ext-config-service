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
 * Rule 1 — highest priority. Matches a form config scoped to the caller's designation AND their
 * org's ministryOrStateType (6 attributes total: type, subtype, portal, clientVersion, designation,
 * ministryOrStateType). Only applies when both are resolved; falls through to
 * {@link DefaultConfigurationRule} otherwise or when no such row exists.
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
        return CollectionUtils.isNotEmpty(ctx.getDesignations()) && StringUtils.isNotBlank(ctx.getMinistryOrStateType());
    }

    @Override
    public Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx) {
        return repository.getFormConfigByDesignationAndMinistry(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                ctx.getMinistryOrStateType(), ctx.getDesignations()
        );
    }

    @Override
    public String buildCacheKey(FormConfigResolutionContext ctx) {
        String designationSegment = ctx.getDesignations().stream()
                .filter(StringUtils::isNotBlank)
                .sorted()
                .collect(Collectors.joining("|"));
        return FormConfigCacheKeys.build(RULE_ID, ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                ctx.getMinistryOrStateType(), null, designationSegment);
    }
}
