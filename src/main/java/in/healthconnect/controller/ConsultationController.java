package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateConsultationRequest;
import in.healthconnect.dto.response.ConsultationResponse;
import in.healthconnect.service.ConsultationService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Consultations hang off an appointment: an appointment is the visit, a consultation is
// what the doctor recorded during it, so the routes are nested under the appointment.
@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/consultation")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

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
}
