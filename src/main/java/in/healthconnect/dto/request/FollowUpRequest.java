package in.healthconnect.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

// Schedule a follow-up for the current appointment's patient, with this doctor.
public record FollowUpRequest(@NotNull LocalDate date, @NotNull LocalTime startTime, String reason) {
}
