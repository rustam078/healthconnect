package in.healthconnect.report;

import in.healthconnect.report.ReportService.ReportFile;
import in.healthconnect.wrapper.ApiResponse;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> upload(@RequestParam Integer patientId,
                                                              @RequestParam(required = false) Integer appointmentId,
                                                              @RequestParam String title,
                                                              @RequestParam("file") MultipartFile file) {
        ReportResponse saved = reportService.upload(
                new ReportService.UploadReportCommand(patientId, appointmentId, title, file));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Report uploaded"));
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
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + reportFile.report().getOriginalFileName() + "\"")
                .body(reportFile.bytes());
    }
}
