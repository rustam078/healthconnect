package in.healthconnect.security.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for UserService.create: password is encoded, role is assigned, duplicates/unknown
// role are rejected.
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(userRepository, roleRepository, passwordEncoder);

    private Role role() {
        Role r = Role.builder().name("DOCTOR").build();
        r.setId(3);
        return r;
    }

    private CreateUserRequest request() {
        return new CreateUserRequest("drwho", "secret", 3, null, null, true);
    }

    @Test
    void createEncodesPasswordAndAssignsRole() {
        when(userRepository.existsByUsername("drwho")).thenReturn(false);
        when(roleRepository.findById(3)).thenReturn(Optional.of(role()));
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.create(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", captor.getValue().getPasswordHash());
        assertEquals("DOCTOR", captor.getValue().getRole().getName());
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("drwho")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(request()));
    }

    @Test
    void createRejectsUnknownRole() {
        when(userRepository.existsByUsername("drwho")).thenReturn(false);
        when(roleRepository.findById(3)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(request()));
    }
}
