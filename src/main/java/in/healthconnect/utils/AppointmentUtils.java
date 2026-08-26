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
                .appointmentDate(appointment.getAppointmentDate())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .durationMinutes(appointment.getDurationMinutes())
                .status(appointment.getStatus())
                .createdAt(appointment.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .updatedAt(appointment.getUpdatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .build();

    }

}
