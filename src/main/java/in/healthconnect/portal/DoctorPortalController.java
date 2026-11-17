package in.healthconnect.portal;

import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.PatientDetailResponse;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.report.ReportController;
import in.healthconnect.report.ReportResponse;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// The doctor portal API. Guarded centrally by DOCTOR_PORTAL_*; every call is scoped to the
// logged-in doctor inside the service.
@RestController
@RequestMapping("/api/v1/doctor-portal")
@RequiredArgsConstructor
public class DoctorPortalController {

    private final DoctorPortalService service;

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> mySchedule(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.mySchedule(date, pageable)));
    }

    @PostMapping("/appointments/{appointmentId}/consultation")
    public ResponseEntity<ApiResponse<ConsultationResponse>> writeConsultation(
            @PathVariable Integer appointmentId, @RequestBody @Valid CreateConsultationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.writeConsultation(appointmentId, request), "Saved"));
    }

    @GetMapping("/appointments/{appointmentId}/consultation")
    public ResponseEntity<ApiResponse<ConsultationResponse>> consultation(@PathVariable Integer appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(service.consultation(appointmentId)));
    }

    @PutMapping("/appointments/{appointmentId}/complete")
    public ResponseEntity<ApiResponse<String>> complete(@PathVariable Integer appointmentId) {
        service.completeAppointment(appointmentId);
        return ResponseEntity.ok(ApiResponse.success("Appointment marked completed"));
    }

    @GetMapping("/appointments/{appointmentId}/consultation/pdf")
    public ResponseEntity<byte[]> consultationPdf(@PathVariable Integer appointmentId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(service.consultationPdf(appointmentId));
    }

    @GetMapping("/patients")
    public ResponseEntity<ApiResponse<List<PatientResponse>>> departmentPatients() {
        return ResponseEntity.ok(ApiResponse.success(service.departmentPatients()));
    }

    @GetMapping("/patients/{patientId}")
    public ResponseEntity<ApiResponse<PatientDetailResponse>> patientProfile(@PathVariable Integer patientId) {
        return ResponseEntity.ok(ApiResponse.success(service.patientProfile(patientId)));
    }

    @GetMapping("/patients/{patientId}/history")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> patientHistory(@PathVariable Integer patientId) {
        return ResponseEntity.ok(ApiResponse.success(service.patientHistory(patientId)));
    }

    @GetMapping("/patients/{patientId}/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> patientReports(@PathVariable Integer patientId) {
        return ResponseEntity.ok(ApiResponse.success(service.patientReports(patientId)));
    }

    @GetMapping("/patients/{patientId}/reports/{reportId}/file")
    public ResponseEntity<byte[]> patientReportFile(@PathVariable Integer patientId, @PathVariable Integer reportId) {
        return ReportController.inline(service.patientReportFile(patientId, reportId));
    }
}
