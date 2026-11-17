package in.healthconnect.report;

import in.healthconnect.dto.response.OrderedTestResponse;
import in.healthconnect.report.ReportService.ReportFile;
import in.healthconnect.service.TestOrderService;
import in.healthconnect.wrapper.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Admin reports: upload a PDF for a patient, list a patient's reports, preview a file inline.
// Guarded centrally by REPORTS_*.
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final TestOrderService testOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> upload(@RequestParam Integer patientId,
                                                              @RequestParam(required = false) Integer appointmentId,
                                                              @RequestParam(required = false) Integer orderedTestId,
                                                              @RequestParam(required = false) String title,
                                                              @RequestParam("file") MultipartFile file) {
        String reportTitle = title != null ? title : file.getOriginalFilename();
        ReportResponse saved = reportService.upload(
                new ReportService.UploadReportCommand(patientId, appointmentId, reportTitle, file));
        if (orderedTestId != null) {
            testOrderService.markAvailable(orderedTestId, saved.id());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Report uploaded"));
    }

    @GetMapping("/appointments/{appointmentId}/tests")
    public ResponseEntity<ApiResponse<List<OrderedTestResponse>>> orderedTests(@PathVariable Integer appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(testOrderService.listOrdered(appointmentId)));
    }

    // Appointments that have lab orders, paginated (pending first, oldest first).
    @GetMapping("/lab-orders")
    public ResponseEntity<ApiResponse<Page<AppointmentLabOrderResponse>>> labOrders(@RequestParam(required = false) Integer appointmentId, @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reportService.labOrders(appointmentId, pageable)));
    }

    // Upload a report file against one ordered test (title = the test name).
    @PostMapping("/ordered-tests/{orderedTestId}/report")
    public ResponseEntity<ApiResponse<ReportResponse>> uploadForTest(@PathVariable Integer orderedTestId,
                                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(reportService.uploadForOrderedTest(orderedTestId, file), "Report uploaded"));
    }

    // Remove the report from an ordered test (delete file + row), resetting it to ORDERED.
    @DeleteMapping("/ordered-tests/{orderedTestId}/report")
    public ResponseEntity<ApiResponse<String>> removeReport(@PathVariable Integer orderedTestId) {
        reportService.removeReportFromOrderedTest(orderedTestId);
        return ResponseEntity.ok(ApiResponse.success("Report removed"));
    }

    // Delete the ordered test entirely (patient does not want it).
    @DeleteMapping("/ordered-tests/{orderedTestId}")
    public ResponseEntity<ApiResponse<String>> deleteOrderedTest(@PathVariable Integer orderedTestId) {
        reportService.deleteOrderedTest(orderedTestId);
        return ResponseEntity.ok(ApiResponse.success("Ordered test deleted"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportResponse>>> list(@RequestParam Integer patientId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.listByPatient(patientId)));
    }

    // Inline so the browser previews the PDF instead of downloading it.
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Integer id) {
        return inline(reportService.getFile(id));
    }

    public static ResponseEntity<byte[]> inline(ReportFile reportFile) {
        String contentType = reportFile.report().getContentType();
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_PDF : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + reportFile.report().getOriginalFileName() + "\"")
                .body(reportFile.bytes());
    }
}
