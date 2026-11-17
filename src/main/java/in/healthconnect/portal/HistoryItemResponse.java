package in.healthconnect.portal;

import in.healthconnect.entity.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

// One row of a patient's full history (across all doctors), for the History tab list.
public record HistoryItemResponse(Integer appointmentId, LocalDate appointmentDate, LocalTime startTime,
                                  String doctorName, AppointmentStatus status, String diagnosis,
                                  boolean hasPrescription, int testCount) {
}
