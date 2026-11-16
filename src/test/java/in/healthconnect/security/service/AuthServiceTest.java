package in.healthconnect.security.service;

import in.healthconnect.security.dto.LoginRequest;
import in.healthconnect.security.dto.LoginResponse;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;
import in.healthconnect.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for AuthService.login: valid login issues a token; bad/unknown/disabled accounts fail.
class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);

    private User user(boolean enabled) {
        Role role = Role.builder().name("ADMIN")
                .permissions(new LinkedHashSet<>(Set.of(
                        new RolePermission(AppModule.PATIENTS, PermissionAction.VIEW))))
                .build();
        User u = User.builder().username("alice").passwordHash("hash").enabled(enabled).role(role).build();
        u.setId(1);
        return u;
    }

    private LoginRequest request() {
        LoginRequest r = new LoginRequest();
        r.setUsername("alice");
        r.setPassword("secret");
        return r;
    }

    @Test
    void loginReturnsTokenAndPermissionsForValidCredentials() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(User.class))).thenReturn("the-token");

        LoginResponse response = service.login(request());

        assertEquals("the-token", response.token());
        assertEquals("alice", response.user().username());
        assertTrue(response.permissions().contains("PATIENTS_VIEW"));
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login(request()));
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> service.login(request()));
    }

    @Test
    void loginRejectsDisabledUser() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(false)));

        assertThrows(BadCredentialsException.class, () -> service.login(request()));
    }
}
