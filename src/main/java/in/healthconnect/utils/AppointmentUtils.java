package in.healthconnect.utils;

import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.entity.Appointment;
import org.springframework.stereotype.Component;
import java.time.ZoneId;


@Component
public class AppointmentUtils {

    public static AppointmentResponse mapToAppointmentResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatient().getId())
                .doctorId(appointment.getDoctor().getId())
                .doctorName(fullName(appointment.getDoctor().getFirstName(),
                        appointment.getDoctor().getLastName()))
                .patientName(fullName(appointment.getPatient().getFirstName(),
                        appointment.getPatient().getLastName()))
                .appointmentDate(appointment.getAppointmentDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .durationMinutes(appointment.getDurationMinutes())
                .status(appointment.getStatus())
                .createdAt(appointment.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .updatedAt(appointment.getUpdatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .build();

    }

    // "Pooja" + "Singh" -> "Pooja Singh". Either half may be missing in older rows, so the
    // blank one is dropped rather than shown as a stray space.
    private static String fullName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

}
