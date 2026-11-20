package in.healthconnect.portal;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.request.FollowUpRequest;
import in.healthconnect.dto.request.ReferRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.LabTestResponse;
import in.healthconnect.dto.response.OrderedTestResponse;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.service.TestOrderService;
import in.healthconnect.service.SlotAvailabilityService;
import in.healthconnect.service.SpecialityService;
import in.healthconnect.dto.response.PatientDetailResponse;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.Referral;
import in.healthconnect.repository.ReferralRepository;
import in.healthconnect.entity.Consultation;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.Patient;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.report.ReportResponse;
import in.healthconnect.report.ReportService;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.ConsultationRepository;
import in.healthconnect.repository.OrderedTestRepository;
import in.healthconnect.service.AppointmentService;
import in.healthconnect.service.ConsultationPdfService;
import in.healthconnect.service.ConsultationService;
import in.healthconnect.service.PatientService;
import in.healthconnect.utils.AppointmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Everything the doctor portal does, always scoped to the logged-in doctor.
@Service
@RequiredArgsConstructor
public class DoctorPortalService {

    private final CurrentUserProvider currentUser;
    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationService consultationService;
    private final ConsultationPdfService consultationPdfService;
    private final ReportService reportService;
    private final PatientAccessPolicy patientAccessPolicy;
    private final PatientService patientService;
    private final TestOrderService testOrderService;
    private final ConsultationRepository consultationRepository;
    private final OrderedTestRepository orderedTestRepository;
    private final SlotAvailabilityService slotAvailabilityService;
    private final ReferralRepository referralRepository;
    private final SpecialityService specialityService;

    // The doctor's own free slots on a date (for scheduling a follow-up).
    @Transactional(readOnly = true)
    public List<LocalTime> availableSlots(LocalDate date) {
        return slotAvailabilityService.freeSlots(currentUser.requireDoctorId(), date);
    }

    // Book a follow-up for this visit's patient with the current doctor.
    @Transactional
    public AppointmentResponse scheduleFollowUp(Integer appointmentId, FollowUpRequest request) {
        Appointment appointment = assertMyAppointment(appointmentId);
        if (appointment.getFollowUpAppointmentId() != null) {
            throw new IllegalArgumentException("A follow-up is already scheduled for this visit.");
        }
        if(appointment.getFollowUpValidUntil().isBefore(request.date())){
            throw new IllegalArgumentException("Follow-up date must be within 30 days of the original appointment date.");
        }

        CreateAppointmentRequest create = CreateAppointmentRequest.builder()
                .patientId(appointment.getPatient().getId())
                .doctorId(appointment.getDoctor().getId())
                .appointmentDate(request.date())
                .startTime(request.startTime())
                .appointmentType("FOLLOW_UP")
                .followUpValidUntil(appointment.getFollowUpValidUntil())
                .durationMinutes(30)

                .build();
        AppointmentResponse created = appointmentService.createAppointment(create);
        appointment.setFollowUpAppointmentId(created.getId());
        appointmentRepository.save(appointment);
        return created;
    }


    // Record a referral of this visit's patient to a specialty.
    @Transactional
    public void refer(Integer appointmentId, ReferRequest request) {
        assertMyAppointment(appointmentId);
        if (referralRepository.existsByAppointmentId(appointmentId)) {
            throw new IllegalArgumentException("This visit is already referred.");
        }
        referralRepository.save(Referral.builder()
                .appointmentId(appointmentId)
                .specialtyId(request.specialtyId())
                .fromDoctorId(currentUser.requireDoctorId())
                .reason(request.reason())
                .build());
    }

    // A patient's full visit history across ALL doctors (the doctor may review it for a
    // patient in their department). Newest first.
    @Transactional(readOnly = true)
    public List<HistoryItemResponse> fullHistory(Integer patientId) {
        patientAccessPolicy.assertCanView(patientId);
        return appointmentRepository.findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patientId)
                .stream().map(this::toHistoryItem).toList();
    }

    // The full record of one past visit (consultation + tests + reports), for any visit of a
    // patient the doctor may view - even if another doctor saw them.
    @Transactional(readOnly = true)
    public HistoryDetailResponse historyDetail(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        patientAccessPolicy.assertCanView(appointment.getPatient().getId());
        return new HistoryDetailResponse(appointment.getId(), appointment.getAppointmentDate(),
                appointment.getStartTime(), doctorName(appointment.getDoctor()),
                consultationService.getByAppointmentIdOrNull(appointmentId),
                testOrderService.listOrdered(appointmentId), reportService.listByAppointment(appointmentId));
    }

    private HistoryItemResponse toHistoryItem(Appointment appointment) {
        Consultation consultation = consultationRepository.findByAppointmentId(appointment.getId()).orElse(null);
        int testCount = orderedTestRepository.findByAppointmentIdOrderByIdAsc(appointment.getId()).size();
        return new HistoryItemResponse(appointment.getId(), appointment.getAppointmentDate(),
                appointment.getStartTime(), doctorName(appointment.getDoctor()), appointment.getStatus(),
                consultation == null ? null : consultation.getDiagnosis(),
                consultation != null && !consultation.getMedicines().isEmpty(), testCount);
    }

    private String doctorName(Doctor doctor) {
        return ((doctor.getFirstName() == null ? "" : doctor.getFirstName()) + " "
                + (doctor.getLastName() == null ? "" : doctor.getLastName())).trim();
    }

    // The lab-test catalogue the doctor searches when ordering tests.
    @Transactional(readOnly = true)
    public List<LabTestResponse> labTestCatalog() {
        return testOrderService.catalog();
    }

    @Transactional
    public List<OrderedTestResponse> orderTests(Integer appointmentId, List<Integer> labTestIds) {
        assertMyAppointment(appointmentId);
        return testOrderService.order(appointmentId, labTestIds);
    }

    @Transactional(readOnly = true)
    public List<OrderedTestResponse> orderedTests(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        return testOrderService.listOrdered(appointmentId);
    }

    public Page<AppointmentResponse> mySchedule(LocalDate date, Pageable pageable) {
        return appointmentService.getAllAppointmentsByDoctorId(currentUser.requireDoctorId(), date, pageable);
    }

    // The doctor's whole day as schedule rows (patient details included, earliest slot first).
    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> schedule(LocalDate date) {
        return appointmentRepository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(currentUser.requireDoctorId(), date)
                .stream().map(this::toScheduleRow).toList();
    }

    private DoctorScheduleResponse toScheduleRow(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Integer age = patient.getDateOfBirth() == null ? null
                : java.time.Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
        String name = ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " "
                + (patient.getLastName() == null ? "" : patient.getLastName())).trim();
        return new DoctorScheduleResponse(appointment.getId(), patient.getId(), name, age,
                patient.getGender(), patient.getPhone(), patient.getBloodGroup(),
                appointment.getStartTime(), appointment.getEndTime(), appointment.getStatus());
    }


    // Header for the consultation workspace: the appointment + the patient card details.
    @Transactional(readOnly = true)
    public AppointmentWorkspaceResponse workspace(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (!appointment.getDoctor().getId().equals(currentUser.requireDoctorId())) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
        Patient patient = appointment.getPatient();
        Integer age = patient.getDateOfBirth() == null ? null
                : java.time.Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears();
        String name = ((patient.getFirstName() == null ? "" : patient.getFirstName()) + " "
                + (patient.getLastName() == null ? "" : patient.getLastName())).trim();
        return new AppointmentWorkspaceResponse(appointment.getId(), appointment.getStatus(),
                appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getEndTime(),
                patient.getId(), name, patient.getPatientCode(), age, patient.getGender(), patient.getPhone(),
                appointmentRepository.findLastVisitDate(patient.getId(), AppointmentStatus.COMPLETED, LocalDate.now()),
                appointmentRepository.findNextFollowUpDate(patient.getId(), AppointmentStatus.SCHEDULED, LocalDate.now()),
                appointment.getFollowUpAppointmentId() != null,
                referralRepository.existsByAppointmentId(appointmentId), appointment.getReason());
    }

    // Mark one of the doctor's own appointments as completed.

    // Every patient in the doctor's department (read scope).
    @Transactional(readOnly = true)
    public List<PatientResponse> departmentPatients() {
        List<Integer> ids = patientAccessPolicy.visiblePatientIds();
        return ids == null ? List.of() : patientService.findSummaries(ids);
    }

    // One patient's profile, if the doctor's scope lets them see this patient.
    @Transactional(readOnly = true)
    public PatientDetailResponse patientProfile(Integer patientId) {
        patientAccessPolicy.assertCanView(patientId);
        return patientService.getPatientDetails(patientId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> patientHistory(Integer patientId) {
        patientAccessPolicy.assertCanView(patientId);
        return appointmentRepository
                .findByDoctorIdAndPatientIdOrderByAppointmentDateDescStartTimeDesc(currentUser.requireDoctorId(), patientId)
                .stream().map(AppointmentUtils::mapToAppointmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> patientReports(Integer patientId) {
        patientAccessPolicy.assertCanView(patientId);
        return reportService.listByPatient(patientId);
    }

    public ReportService.ReportFile patientReportFile(Integer patientId, Integer reportId) {
        patientAccessPolicy.assertCanView(patientId);
        return reportService.getFileForPatient(reportId, patientId);
    }

    private Appointment assertMyAppointment(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (!appointment.getDoctor().getId().equals(currentUser.requireDoctorId())) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
        return appointment;
    }
}
