package in.healthconnect.report;

import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.OrderedTest;
import in.healthconnect.entity.enums.OrderedTestStatus;
import in.healthconnect.entity.projection.AppointmentLabOrderProjection;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.OrderedTestRepository;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final OrderedTestRepository orderedTestRepository;
    private final AppointmentRepository appointmentRepository;

    public record ReportFile(PatientReport report, byte[] bytes) {
    }

    // ---------- Admin lab-order management (appointment-centric reports screen) ----------

    // Appointments that have lab orders: paginated, pending first, oldest first.
    @Transactional(readOnly = true)
    public Page<AppointmentLabOrderResponse> labOrders(Integer appointmentId, Pageable pageable) {

        // Query 1: Fetch paginated appointment IDs
        Page<Integer> ids = orderedTestRepository.findAppointmentIdsWithOrders(appointmentId, OrderedTestStatus.AVAILABLE, pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }

        // Query 2: Fetch all appointment, patient, test and report details
        List<AppointmentLabOrderProjection> rows = orderedTestRepository.findLabOrderDetails(ids.getContent());

        // Group rows by appointment ID
        Map<Integer, List<AppointmentLabOrderProjection>> grouped =
                rows.stream().collect(Collectors
                        .groupingBy(AppointmentLabOrderProjection::getAppointmentId, LinkedHashMap::new, Collectors.toList()));

        // Maintain the original pagination order
        List<AppointmentLabOrderResponse> content = ids.getContent()
                .stream()
                .map(id -> toLabOrderResponse(grouped.get(id)))
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(content, pageable, ids.getTotalElements());
    }


    private AppointmentLabOrderResponse toLabOrderResponse(List<AppointmentLabOrderProjection> rows) {

        if (rows == null || rows.isEmpty()) {
            return null;
        }

        AppointmentLabOrderProjection first = rows.get(0);

        List<LabOrderItem> items = rows.stream()
                .map(row -> {
                    LabOrderItem.ReportInfo info = null;
                    if (row.getReportId() != null && row.getOriginalFileName() != null) {
                        info = new LabOrderItem.ReportInfo(
                                row.getOriginalFileName(),
                                row.getContentType(),
                                row.getFileSizeBytes(),
                                row.getCreatedAt()
                        );
                    }

                    return new LabOrderItem(
                            row.getAppointmentId(),
                            row.getOrderedTestId(),
                            row.getReportId(),
                            row.getCategory(),
                            row.getLabStatus(),
                            row.getTestName(),
                            info
                    );
                })
                .toList();

        boolean allAvailable = !items.isEmpty() && items.stream().allMatch(item -> item.labStatus() == OrderedTestStatus.AVAILABLE);
        String status = allAvailable ? first.getAppointmentStatus() : OrderedTestStatus.ORDERED.name();

        Integer age = first.getDateOfBirth() == null ? null : Period.between(first.getDateOfBirth(), LocalDate.now()).getYears();

        String name = Stream.of(first.getFirstName(), first.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        return new AppointmentLabOrderResponse(
                first.getAppointmentId(),
                first.getPatientId(),
                name,
                first.getAppointmentDate(),
                age,
                first.getGender(),
                first.getBloodGroup(),
                status,
                items
        );
    }
    // Upload a report against an ordered test: title is the test name, and the test is marked
    // available. Accepts a PDF or image.
    @Transactional
    public ReportResponse uploadForOrderedTest(Integer orderedTestId, MultipartFile file) {
        OrderedTest test = orderedTestRepository.findById(orderedTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Ordered test not found: " + orderedTestId));
        Appointment appointment = appointmentRepository.findById(test.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + test.getAppointmentId()));
        String extension = validateFile(file);
        byte[] bytes = readBytes(file);
        PatientReport report = reportRepository.save(PatientReport.builder()
                .patientId(appointment.getPatient().getId())
                .appointmentId(test.getAppointmentId())
                .title(test.getTestName())
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storageService.store(bytes, extension))
                .contentType(ALLOWED.get(extension))
                .fileSizeBytes((long) bytes.length)
                .uploadedByUserId(currentUserProvider.currentUser().getId())
                .build());
        test.setStatus(OrderedTestStatus.AVAILABLE);
        test.setReportId(report.getId());
        orderedTestRepository.save(test);
        return withUploader(report);
    }

    // Remove the report from an ordered test (delete file + row) and reset it to ORDERED.
    @Transactional
    public void removeReportFromOrderedTest(Integer orderedTestId) {
        OrderedTest test = orderedTestRepository.findById(orderedTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Ordered test not found: " + orderedTestId));
        deleteReportIfPresent(test.getReportId());
        test.setReportId(null);
        test.setStatus(OrderedTestStatus.ORDERED);
        orderedTestRepository.save(test);
    }

    // Delete an ordered test entirely (patient does not want it), plus its report if any.
    @Transactional
    public void deleteOrderedTest(Integer orderedTestId) {
        OrderedTest test = orderedTestRepository.findById(orderedTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Ordered test not found: " + orderedTestId));
        deleteReportIfPresent(test.getReportId());
        orderedTestRepository.delete(test);
    }

    private void deleteReportIfPresent(Integer reportId) {
        if (reportId == null) {
            return;
        }
        reportRepository.findById(reportId).ifPresent(report -> {
            storageService.delete(report.getStoredFileName());
            reportRepository.delete(report);
        });
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
