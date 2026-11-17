package in.healthconnect.portal;

import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;

import java.time.LocalTime;

// One row of a doctor's day: the appointment plus the patient details shown in the schedule.
public record DoctorScheduleResponse(Integer appointmentId, Integer patientId, String patientName,
                                     Integer age, Gender gender, String phone, BloodGroup bloodGroup,
                                     LocalTime startTime, LocalTime endTime, AppointmentStatus status) {
}
