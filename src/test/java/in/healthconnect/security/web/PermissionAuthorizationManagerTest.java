package in.healthconnect.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests for the central authorization decision: does THIS user have what THIS request needs?
class PermissionAuthorizationManagerTest {

    private final PermissionAuthorizationManager manager =
            new PermissionAuthorizationManager(new RequiredPermissionResolver());

    private Authentication userWith(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                "alice", "x", AuthorityUtils.createAuthorityList(authorities));
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken(
                "key", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    @Test
    void grantsWhenUserHasTheAuthority() {
        assertTrue(manager.isGranted(userWith("PATIENTS_VIEW"), "GET", "/api/v1/patients"));
    }

    @Test
    void deniesWhenUserLacksTheAuthority() {
        assertFalse(manager.isGranted(userWith("DOCTORS_VIEW"), "GET", "/api/v1/patients"));
    }

    @Test
    void authenticatedSentinelGrantsAnyLoggedInUser() {
        assertTrue(manager.isGranted(userWith(), "GET", "/api/v1/auth/me"));
    }

    @Test
    void anonymousIsDeniedEvenForAuthenticatedEndpoint() {
        assertFalse(manager.isGranted(anonymous(), "GET", "/api/v1/auth/me"));
    }

    @Test
    void deniesWhenNoMappingExists() {
        assertFalse(manager.isGranted(userWith("PATIENTS_VIEW"), "GET", "/api/v1/mystery"));
    }

    @Test
    void deniesNullAuthentication() {
        assertFalse(manager.isGranted(null, "GET", "/api/v1/patients"));
    }
}
