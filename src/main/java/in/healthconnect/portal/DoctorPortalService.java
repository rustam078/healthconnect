package in.healthconnect.portal;

import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.PatientDetailResponse;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.report.ReportResponse;
import in.healthconnect.report.ReportService;
import in.healthconnect.repository.AppointmentRepository;
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

import java.time.LocalDate;
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

    public Page<AppointmentResponse> mySchedule(LocalDate date, Pageable pageable) {
        return appointmentService.getAllAppointmentsByDoctorId(currentUser.requireDoctorId(), date, pageable);
    }

    @Transactional
    public ConsultationResponse writeConsultation(Integer appointmentId, CreateConsultationRequest request) {
        assertMyAppointment(appointmentId);
        return consultationService.createConsultation(appointmentId, request);
    }

    // Mark one of the doctor's own appointments as completed.
    @Transactional
    public void completeAppointment(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (!appointment.getDoctor().getId().equals(currentUser.requireDoctorId())) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
    }

    @Transactional(readOnly = true)
    public ConsultationResponse consultation(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        return consultationService.getByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public byte[] consultationPdf(Integer appointmentId) {
        assertMyAppointment(appointmentId);
        return consultationPdfService.renderByAppointmentId(appointmentId);
    }

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

    private void assertMyAppointment(Integer appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        if (!appointment.getDoctor().getId().equals(currentUser.requireDoctorId())) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
    }
}
