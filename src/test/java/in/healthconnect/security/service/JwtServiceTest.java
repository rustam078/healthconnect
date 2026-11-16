package in.healthconnect.security.service;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests for JwtService: a token must round-trip the user's details, and any tampered or
// expired token must be rejected.
class JwtServiceTest {

    private static final String SECRET = "this-is-a-test-secret-key-at-least-32-bytes!!";
    private final JwtService jwt = new JwtService(SECRET, 120);

    private User sampleUser() {
        Role role = Role.builder()
                .name("ADMIN")
                .permissions(new LinkedHashSet<>(Set.of(
                        new RolePermission(AppModule.PATIENTS, PermissionAction.VIEW))))
                .build();
        User user = User.builder().username("alice").role(role).build();
        user.setId(7);
        return user;
    }

    @Test
    void tokenRoundTripsUserDetails() {
        String token = jwt.generateToken(sampleUser());

        assertTrue(jwt.isValid(token));
        assertEquals("alice", jwt.getUsername(token));
        assertEquals(7, jwt.getUserId(token));
        assertTrue(jwt.getAuthorities(token).contains("PATIENTS_VIEW"));
    }

    @Test
    void garbageTokenIsInvalid() {
        assertFalse(jwt.isValid("not-a-real-token"));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwt.generateToken(sampleUser());
        String tampered = token.substring(0, token.length() - 2) + "xy";
        assertFalse(jwt.isValid(tampered));
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService shortLived = new JwtService(SECRET, -1); // already expired
        String token = shortLived.generateToken(sampleUser());
        assertFalse(shortLived.isValid(token));
    }
}
