package com.igot.cb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRespParamTest {

    @Test
    void idConstructor_setsResMsgIdAndMsgId() {
        ApiRespParam param = new ApiRespParam("id-123");

        assertEquals("id-123", param.getResMsgId());
        assertEquals("id-123", param.getMsgId());
    }

    @Test
    void noArgsConstructor_leavesFieldsNull() {
        ApiRespParam param = new ApiRespParam();

        assertNull(param.getResMsgId());
        assertNull(param.getMsgId());
    }

    @Test
    void settersAndGetters_roundTrip() {
        ApiRespParam param = new ApiRespParam();

        param.setErr("err-code");
        param.setErrMsg("something failed");
        param.setStatus("FAILED");

        assertEquals("err-code", param.getErr());
        assertEquals("something failed", param.getErrMsg());
        assertEquals("FAILED", param.getStatus());
    }
}
