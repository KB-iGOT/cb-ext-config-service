package com.igot.cb.authentication.util;

import com.igot.cb.authentication.model.UserDetails;
import com.igot.cb.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    @InjectMocks
    private AccessTokenValidator validator;

    @Test
    void verifyUserToken_success_shouldReturnUserDetails() {
        AccessTokenValidator spyValidator = spy(validator);

        Map<String, Object> payload = Map.of(
                "iss", "valid-issuer",
                Constants.SUB, "user:123",
                "user_roles", List.of("MDO_ADMIN"),
                "org", "org1"
        );

        doReturn(payload).when(spyValidator).validateToken("valid-token");
        doReturn(true).when(spyValidator).checkIss("valid-issuer");

        UserDetails result = spyValidator.verifyUserToken("valid-token");

        assertNotNull(result);
        assertEquals("123", result.getUserId());
        assertEquals("org1", result.getOrg());
        assertEquals(List.of("MDO_ADMIN"), result.getUserRoles());
    }

    @Test
    void verifyUserToken_emptyPayload_shouldReturnUserDetailsWithNullUserId() {
        AccessTokenValidator spyValidator = spy(validator);

        doReturn(Collections.emptyMap()).when(spyValidator).validateToken("invalid-token");

        UserDetails result = spyValidator.verifyUserToken("invalid-token");

        assertNotNull(result);
        assertNull(result.getUserId());
    }

    @Test
    void verifyUserToken_invalidIssuer_shouldReturnUserDetailsWithNullUserId() {
        AccessTokenValidator spyValidator = spy(validator);

        Map<String, Object> payload = Map.of(
                "iss", "invalid-issuer",
                Constants.SUB, "user:123"
        );

        doReturn(payload).when(spyValidator).validateToken("token");
        doReturn(false).when(spyValidator).checkIss("invalid-issuer");

        UserDetails result = spyValidator.verifyUserToken("token");

        assertNotNull(result);
        assertNull(result.getUserId());
    }

    @Test
    void verifyUserToken_whenValidateTokenThrowsException_shouldReturnUnauthorizedUserDetails() {
        AccessTokenValidator spyValidator = spy(validator);

        doThrow(new RuntimeException("test exception"))
                .when(spyValidator).validateToken(any());

        UserDetails result = spyValidator.verifyUserToken("bad-token");

        assertNotNull(result);
        assertEquals(Constants.UNAUTHORIZED, result.getUserId());
    }

    @Test
    void fetchUserDetailsFromToken_nullToken_shouldReturnNull() {
        UserDetails result = validator.fetchUserDetailsFromToken(null);

        assertNull(result);
    }

    @Test
    void fetchUserDetailsFromToken_validToken_shouldReturnUserDetails() {
        AccessTokenValidator spyValidator = spy(validator);

        UserDetails userDetails = new UserDetails();
        userDetails.setUserId("123");
        userDetails.setOrg("org1");
        userDetails.setUserRoles(List.of("MDO_ADMIN"));

        doReturn(userDetails).when(spyValidator).verifyUserToken("valid-token");

        UserDetails result = spyValidator.fetchUserDetailsFromToken("valid-token");

        assertNotNull(result);
        assertEquals("123", result.getUserId());
        assertEquals("org1", result.getOrg());
        assertEquals(List.of("MDO_ADMIN"), result.getUserRoles());
    }

    @Test
    void fetchUserDetailsFromToken_unauthorizedUser_shouldReturnNull() {
        AccessTokenValidator spyValidator = spy(validator);

        UserDetails userDetails = new UserDetails();
        userDetails.setUserId(Constants.UNAUTHORIZED);

        doReturn(userDetails).when(spyValidator).verifyUserToken("bad-token");

        UserDetails result = spyValidator.fetchUserDetailsFromToken("bad-token");

        assertNull(result);
    }

    @Test
    void fetchUserDetailsFromToken_blankUserId_shouldReturnNull() {
        AccessTokenValidator spyValidator = spy(validator);

        UserDetails userDetails = new UserDetails();
        userDetails.setUserId("");

        doReturn(userDetails).when(spyValidator).verifyUserToken("token");

        UserDetails result = spyValidator.fetchUserDetailsFromToken("token");

        assertNull(result);
    }

    @Test
    void fetchUserDetailsFromToken_whenVerifyThrowsException_shouldReturnNull() {
        AccessTokenValidator spyValidator = spy(validator);

        doThrow(new RuntimeException("test exception"))
                .when(spyValidator).verifyUserToken("token");

        UserDetails result = spyValidator.fetchUserDetailsFromToken("token");

        assertNull(result);
    }

    @Test
    void validateToken_invalidFormat_shouldReturnEmptyMap() {
        Map<String, Object> result = validator.validateToken("invalid-token");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void validateToken_nullToken_shouldReturnEmptyMap() {
        Map<String, Object> result = validator.validateToken(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void checkIss_invalidIssuer_shouldReturnFalse() {
        boolean result = validator.checkIss("wrong-issuer");

        assertFalse(result);
    }

}

