package in.healthconnect.portal;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

// A patient booking a slot. The patient id is taken from the logged-in user, never the body.
public record BookAppointmentRequest(@NotNull Integer doctorId, @NotNull LocalDate date,
                                     @NotNull LocalTime startTime) {
}
