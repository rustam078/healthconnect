package in.healthconnect.portal;

import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.entity.enums.Gender;

import java.time.LocalDate;
import java.time.LocalTime;

// Header data for the doctor's appointment workspace page: the visit plus the patient card.
public record AppointmentWorkspaceResponse(Integer appointmentId, AppointmentStatus status,
                                           LocalDate appointmentDate, LocalTime startTime, LocalTime endTime,
                                           Integer patientId, String patientName, String patientCode,
                                           Integer age, Gender gender, String phone,
                                           LocalDate lastVisitDate, LocalDate nextFollowUpDate,
                                           boolean followUpScheduled, boolean referred) {
}
