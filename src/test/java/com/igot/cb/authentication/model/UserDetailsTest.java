package com.igot.cb.authentication.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserDetailsTest {

    @Test
    void userDetails_shouldSetAndGetValues() {
        UserDetails userDetails = new UserDetails();

        userDetails.setUserId("user1");
        userDetails.setOrg("org1");
        userDetails.setUserRoles(List.of("MDO_ADMIN", "USER"));

        assertEquals("user1", userDetails.getUserId());
        assertEquals("org1", userDetails.getOrg());
        assertEquals(List.of("MDO_ADMIN", "USER"), userDetails.getUserRoles());
    }

    @Test
    void userDetails_defaultValuesShouldBeNull() {
        UserDetails userDetails = new UserDetails();

        assertNull(userDetails.getUserId());
        assertNull(userDetails.getOrg());
        assertNull(userDetails.getUserRoles());
    }
}
