package in.healthconnect.report;

import in.healthconnect.entity.enums.LabTestCategory;
import in.healthconnect.entity.enums.OrderedTestStatus;

import java.time.Instant;

// One ordered lab test on an appointment, with its report (null until uploaded).
public record LabOrderItem(Integer appointmentId, Integer orderedTestId, Integer reportId,
                           LabTestCategory category, OrderedTestStatus labStatus, String testName,
                           ReportInfo report) {

    public record ReportInfo(String originalFileName, String contentType, Long fileSizeBytes, Instant createdAt) {
    }
}
