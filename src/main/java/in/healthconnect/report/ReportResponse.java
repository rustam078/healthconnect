package in.healthconnect.report;

import java.time.Instant;

// Report metadata the UI lists (never the bytes - those come from the /file endpoint).
public record ReportResponse(Integer id, Integer patientId, Integer appointmentId, String title,
                             String originalFileName, String contentType, Long fileSizeBytes,
                             Instant createdAt, String uploadedBy) {

    public static ReportResponse from(PatientReport report) {
        return from(report, null);
    }

    public static ReportResponse from(PatientReport report, String uploadedBy) {
        return new ReportResponse(report.getId(), report.getPatientId(), report.getAppointmentId(),
                report.getTitle(), report.getOriginalFileName(), report.getContentType(),
                report.getFileSizeBytes(), report.getCreatedAt(), uploadedBy);
    }
}
