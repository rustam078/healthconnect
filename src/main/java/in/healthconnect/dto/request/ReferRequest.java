package in.healthconnect.dto.request;

import jakarta.validation.constraints.NotNull;

// Refer the appointment's patient to a specialty, with a reason.
public record ReferRequest(@NotNull Integer specialtyId, String reason) {
}
