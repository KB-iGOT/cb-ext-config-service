package com.igot.cb.formConfiguration.rule;

import com.igot.cb.formConfiguration.entity.FormConfigurationEntity;
import com.igot.cb.formConfiguration.repository.FormConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Last-resort fallback — matches a row with NO criteria at all (e.g. one created via /v2/create,
 * which never sets criteria), scoped only by type/subtype/portal/clientVersion. Always applicable;
 * kept at the lowest priority so {@link DesignationConfigurationRule} and
 * {@link DefaultConfigurationRule} — both of which require criteria to say who a row is for — get
 * first refusal.
 */
@Component
public class NoCriteriaConfigurationRule implements FormConfigLookupRule {

    private static final String RULE_ID = "noCriteria";

    @Autowired
    private FormConfigurationRepository repository;

    @Override
    public String ruleName() {
        return RULE_ID;
    }

    @Override
    public boolean supports(FormConfigResolutionContext ctx) {
        return true;
    }

    @Override
    public Optional<FormConfigurationEntity> find(FormConfigResolutionContext ctx) {
        return repository.findByTypeAndSubtypeAndPortalAndClientVersionAndCriteriaIsNull(
                ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion()
        );
    }

    @Override
    public String buildCacheKey(FormConfigResolutionContext ctx) {
        return FormConfigCacheKeys.build(RULE_ID, ctx.getType(), ctx.getSubtype(), ctx.getPortal(), ctx.getClientVersion(),
                null, null, null);
    }
}
