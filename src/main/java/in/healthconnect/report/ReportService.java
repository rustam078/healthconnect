package in.healthconnect.report;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

// Upload / list / fetch report files. PDF only. Fetch can be scoped to a patient so a portal
// user can never read someone else's report.
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "png", "image/png", "webp", "image/webp", "gif", "image/gif");

    private final PatientReportRepository reportRepository;
    private final ReportStorageService storageService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public record ReportFile(PatientReport report, byte[] bytes) {
    }

    // appointmentId is optional - when set, the report shows in that visit's record.
    // Accepts a PDF or an image.
    public ReportResponse upload(UploadReportCommand command) {
        MultipartFile file = command.file();
        String extension = validateFile(file);
        byte[] bytes = readBytes(file);
        String stored = storageService.store(bytes, extension);
        PatientReport report = PatientReport.builder()
                .patientId(command.patientId())
                .appointmentId(command.appointmentId())
                .title(command.title())
                .originalFileName(file.getOriginalFilename())
                .storedFileName(stored)
                .contentType(ALLOWED.get(extension))
                .fileSizeBytes((long) bytes.length)
                .uploadedByUserId(currentUserProvider.currentUser().getId())
                .build();
        return withUploader(reportRepository.save(report));
    }

    // Resolve the uploader's username for display.
    private ReportResponse withUploader(PatientReport report) {
        String uploadedBy = report.getUploadedByUserId() == null ? null
                : userRepository.findById(report.getUploadedByUserId()).map(User::getUsername).orElse(null);
        return ReportResponse.from(report, uploadedBy);
    }

    // Groups the upload inputs so the method stays within the parameter limit.
    public record UploadReportCommand(Integer patientId, Integer appointmentId, String title, MultipartFile file) {
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listByPatient(Integer patientId) {
        return reportRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(this::withUploader).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listByAppointment(Integer appointmentId) {
        return reportRepository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId).stream()
                .map(this::withUploader).toList();
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

    // Returns the validated lowercase extension; rejects anything outside PDF/image types.
    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A report file is required.");
        }
        String name = file.getOriginalFilename();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        if (!StringUtils.hasText(name) || !ALLOWED.containsKey(extension)) {
            throw new IllegalArgumentException("Only PDF or image files are allowed.");
        }
        return extension;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
