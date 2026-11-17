package in.healthconnect.security.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.dto.UpdateUserRequest;
import in.healthconnect.security.dto.UserAdminResponse;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

// Admin management of login users: list, create (encode password + assign role), update, delete.
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserAdminResponse> list() {
        return userRepository.findAll().stream().map(UserAdminResponse::from).toList();
    }

    public UserAdminResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("A user named '" + request.username() + "' already exists.");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(findRole(request.roleId()));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setDoctorId(request.doctorId());
        user.setPatientId(request.patientId());
        return UserAdminResponse.from(userRepository.save(user));
    }

    public UserAdminResponse update(Integer id, UpdateUserRequest request) {
        User user = findUser(id);
        if (request.roleId() != null) {
            user.setRole(findRole(request.roleId()));
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setDoctorId(request.doctorId());
        user.setPatientId(request.patientId());
        return UserAdminResponse.from(userRepository.save(user));
    }

    public void delete(Integer id) {
        userRepository.delete(findUser(id));
    }

    private Role findRole(Integer roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
    }

    private User findUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
