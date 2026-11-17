package in.healthconnect.portal;

import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.OrderedTestResponse;
import in.healthconnect.report.ReportResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// The full record of one past visit, shown in the History details panel.
public record HistoryDetailResponse(Integer appointmentId, LocalDate appointmentDate, LocalTime startTime,
                                    String doctorName, ConsultationResponse consultation,
                                    List<OrderedTestResponse> tests, List<ReportResponse> reports) {
}
