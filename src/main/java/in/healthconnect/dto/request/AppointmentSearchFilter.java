package in.healthconnect.dto.request;

import in.healthconnect.entity.enums.AppointmentStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Optional filters for the admin appointment search; any combination may be set, all are nullable.
@Data
public class AppointmentSearchFilter {

    private Integer appointmentId;
    private Integer doctorId;
    private Integer patientId;
    private String patientName;
    private AppointmentStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
