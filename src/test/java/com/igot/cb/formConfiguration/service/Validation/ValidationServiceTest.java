package com.igot.cb.formConfiguration.service.Validation;

import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationServiceTest {

    private final ValidationService validationService = new ValidationService();

    private Map<String, Object> wrap(Map<String, Object> requestObject) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put(Constants.Parameters.REQUEST, requestObject);
        return wrapper;
    }

    private Map<String, Object> baseRequest(String name) {
        Map<String, Object> req = new HashMap<>();
        req.put(Constants.NAME, name);
        req.put(Constants.TYPE, "page");
        req.put(Constants.SUBTYPE, "globalenv");
        req.put(Constants.PORTAL, "portal");
        req.put(Constants.CLIENT_VERSION, 1.0);
        return req;
    }

    @Test
    void validateForm_update_globalEnvConfig_noCriteria_succeeds() {
        Map<String, Object> req = baseRequest("portal_global_env_config");

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.SUCCESSFUL, result);
    }

    @Test
    void validateForm_update_globalEnvConfig_nameMatchIsCaseInsensitive() {
        Map<String, Object> req = baseRequest("Portal_Global_ENV_Config");

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.SUCCESSFUL, result);
    }

    @Test
    void validateForm_update_otherName_noCriteria_stillFails() {
        Map<String, Object> req = baseRequest("someOtherForm");

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.ResponseMessages.FIELD_CRIETERIA_MISSING, result);
    }

    @Test
    void validateForm_update_globalEnvConfig_missingType_stillFails() {
        Map<String, Object> req = baseRequest("portal_global_env_config");
        req.remove(Constants.TYPE);

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.ResponseMessages.FIELD_TYPE_MISSING, result);
    }

    @Test
    void validateForm_update_globalEnvConfig_missingClientVersion_stillFails() {
        Map<String, Object> req = baseRequest("portal_global_env_config");
        req.remove(Constants.CLIENT_VERSION);

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.ResponseMessages.FIELD_CLIENTVERSION_MISSING, result);
    }

    @Test
    void validateForm_update_otherName_withValidCriteria_succeeds() {
        Map<String, Object> req = baseRequest("someOtherForm");
        Map<String, Object> criteria = new HashMap<>();
        criteria.put(Constants.ROLE, "PUBLIC");
        criteria.put(Constants.ROOTORG, "org1");
        req.put(Constants.CRITERIA, criteria);

        String result = validationService.validateForm(wrap(req), Constants.Parameters.UPDATE);

        assertEquals(Constants.SUCCESSFUL, result);
    }
}
