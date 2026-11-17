package in.healthconnect.report;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

// Upload / list / fetch report files. PDF only. Fetch can be scoped to a patient so a portal
// user can never read someone else's report.
@Service
@RequiredArgsConstructor
public class ReportService {

    private final PatientReportRepository reportRepository;
    private final ReportStorageService storageService;
    private final CurrentUserProvider currentUserProvider;

    public record ReportFile(PatientReport report, byte[] bytes) {
    }

    // appointmentId is optional - when set, the report shows in that visit's record.
    public ReportResponse upload(UploadReportCommand command) {
        MultipartFile file = command.file();
        validatePdf(file);
        byte[] bytes = readBytes(file);
        String stored = storageService.store(bytes, "pdf");
        PatientReport report = PatientReport.builder()
                .patientId(command.patientId())
                .appointmentId(command.appointmentId())
                .title(command.title())
                .originalFileName(file.getOriginalFilename())
                .storedFileName(stored)
                .contentType("application/pdf")
                .fileSizeBytes((long) bytes.length)
                .uploadedByUserId(currentUserProvider.currentUser().getId())
                .build();
        return ReportResponse.from(reportRepository.save(report));
    }

    // Groups the upload inputs so the method stays within the parameter limit.
    public record UploadReportCommand(Integer patientId, Integer appointmentId, String title, MultipartFile file) {
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listByPatient(Integer patientId) {
        return reportRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(ReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportFile getFile(Integer id) {
        PatientReport report = findReport(id);
        return new ReportFile(report, storageService.load(report.getStoredFileName()));
    }

    // Same as getFile but only if the report belongs to this patient (portal scope).
    @Transactional(readOnly = true)
    public ReportFile getFileForPatient(Integer id, Integer patientId) {
        PatientReport report = findReport(id);
        if (!report.getPatientId().equals(patientId)) {
            throw new ResourceNotFoundException("Report not found: " + id);
        }
        return new ReportFile(report, storageService.load(report.getStoredFileName()));
    }

    private PatientReport findReport(Integer id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A report file is required.");
        }
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name) || !name.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
