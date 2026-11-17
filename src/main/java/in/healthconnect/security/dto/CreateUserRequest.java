package in.healthconnect.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Admin creates a user: login name, initial password, and the role to assign.
public record CreateUserRequest(@NotBlank String username, @NotBlank String password,
                                @NotNull Integer roleId, Integer doctorId, Integer patientId,
                                Boolean enabled) {
}
