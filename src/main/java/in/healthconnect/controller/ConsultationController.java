package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.service.ConsultationPdfService;
import in.healthconnect.service.ConsultationService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Consultations hang off an appointment: an appointment is the visit, a consultation is
// what the doctor recorded during it, so the routes are nested under the appointment.
@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/consultation")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final ConsultationPdfService consultationPdfService;

    // Record the visit and mark the appointment COMPLETED in one call.
    @PostMapping
    public ResponseEntity<ApiResponse<ConsultationResponse>> create(@PathVariable Integer appointmentId,
                     @Valid @RequestBody CreateConsultationRequest request) {

        ConsultationResponse response = consultationService.createConsultation(appointmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Consultation recorded successfully"));
    }

    // Pull the recorded visit back for review, medicines included.
    @GetMapping
    public ResponseEntity<ApiResponse<ConsultationResponse>> get(@PathVariable Integer appointmentId) {

        ConsultationResponse response = consultationService.getByAppointmentId(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(response, "Consultation fetched successfully"));
    }

    // The consultation as a printable A4 PDF, rendered from the stored HTML template.
    // Served inline so the browser can preview it; the filename is used if the user saves.
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer appointmentId) {

        byte[] pdf = consultationPdfService.renderByAppointmentId(appointmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"consultation-" + appointmentId + ".pdf\"")
                .body(pdf);
    }
}
