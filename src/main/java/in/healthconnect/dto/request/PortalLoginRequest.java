package in.healthconnect.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Optional login to create alongside a doctor/patient, on the same form.
public record PortalLoginRequest(@NotBlank String username, @NotBlank String password, @NotNull Integer roleId) {
}
