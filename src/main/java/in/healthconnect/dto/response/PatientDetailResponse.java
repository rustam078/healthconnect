package in.healthconnect.dto.response;

import java.time.LocalDate;

public record PatientDetailResponse(
        PatientResponse patient,
        Stats stats
) {
    public record Stats(
            long totalAppointments,
            long upcomingAppointments,
            long doctorsSeen,
            LocalDate lastVisitDate
    ) {
    }
}
