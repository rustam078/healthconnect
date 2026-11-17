package in.healthconnect.portal;

import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.dto.response.SpecialtyResponse;
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

// The patient portal API. Guarded centrally by CUSTOMER_PORTAL_*; every call is scoped to
// the logged-in patient inside the service.
@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final CustomerPortalService service;

    @GetMapping("/specialties")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> specialties() {
        return ResponseEntity.ok(ApiResponse.success(service.specialties()));
    }

    @GetMapping("/available-doctors")
    public ResponseEntity<ApiResponse<List<AvailableDoctorResponse>>> availableDoctors(
            @RequestParam Integer specialtyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(service.availableDoctors(specialtyId, date)));
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(@RequestBody @Valid BookAppointmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.book(request), "Appointment booked"));
    }

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> myAppointments(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(service.myAppointments(pageable)));
    }

    @DeleteMapping("/appointments/{id}")
    public ResponseEntity<ApiResponse<String>> cancel(@PathVariable Integer id) {
        service.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled"));
    }

    @GetMapping("/appointments/{id}/consultation")
    public ResponseEntity<ApiResponse<ConsultationResponse>> consultation(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(service.consultation(id)));
    }

    @GetMapping("/appointments/{id}/consultation/pdf")
    public ResponseEntity<byte[]> consultationPdf(@PathVariable Integer id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(service.consultationPdf(id));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> myReports() {
        return ResponseEntity.ok(ApiResponse.success(service.myReports()));
    }

    @GetMapping("/reports/{id}/file")
    public ResponseEntity<byte[]> reportFile(@PathVariable Integer id) {
        return ReportController.inline(service.reportFile(id));
    }
}
