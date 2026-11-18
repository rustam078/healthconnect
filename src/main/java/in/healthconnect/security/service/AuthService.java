package in.healthconnect.security.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.security.dto.AuthMeResponse;
import in.healthconnect.security.dto.LoginRequest;
import in.healthconnect.security.dto.LoginResponse;
import in.healthconnect.security.dto.UserResponse;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Verifies credentials and issues a signed token; also serves the current user's details.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Same error for unknown user / wrong password / disabled, so no account details leak.
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, buildUserResponse(user), permissionsOf(user));
    }

    private UserResponse buildUserResponse(User user) {
        String name = user.getUsername();

        if (user.getDoctorId() != null) {
            name = doctorRepository.findById(user.getDoctorId())
                    .map(doctor -> doctor.getFirstName() + " " + doctor.getLastName())
                    .orElse(user.getUsername());

        } else if (user.getPatientId() != null) {
            name = patientRepository.findById(user.getPatientId())
                    .map(patient -> patient.getFirstName() + " " + patient.getLastName())
                    .orElse(user.getUsername());
        }

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                name,
                user.getRole().getName(),
                user.getDoctorId(),
                user.getPatientId(),
                user.getAvatarFileId()
        );
    }

    @Transactional(readOnly = true)
    public AuthMeResponse me(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return new AuthMeResponse(buildUserResponse(user), permissionsOf(user));
    }

    private List<String> permissionsOf(User user) {
        return user.getRole().getPermissions().stream().map(RolePermission::authority).toList();
    }
}
