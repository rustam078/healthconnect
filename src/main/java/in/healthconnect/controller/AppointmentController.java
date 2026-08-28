package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateAppointmentRequest;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.service.AppointmentService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {

        AppointmentResponse response = appointmentService.createAppointment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Appointment booked successfully"));
    }


    @GetMapping("/{doctorId}")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getAllAppointmentByDoctorId(@PathVariable Integer doctorId,
                     @RequestParam(required = false) String search,@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appointmentDate,
                 @PageableDefault( page = 0, size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AppointmentResponse> response = appointmentService.getAllAppointmentsByDoctorId(doctorId, appointmentDate,pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointments fetched successfully"));
    }


    // Move an appointment to another status. A single value needs no request object, so it
    // rides as a query param: PATCH /appointments/12/status?status=COMPLETED. An unknown
    // value is caught by Spring's enum binding and comes back as a 400 listing the ones
    // that exist.
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateStatus(@PathVariable Integer id,
                     @RequestParam AppointmentStatus status) {

        AppointmentResponse response = appointmentService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment status updated successfully"));
    }


    // Move an existing appointment to a different doctor and/or time. The doctor is locked
    // while the new slot is checked, so two reschedules cannot land in the same slot.
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(@PathVariable Integer id,
                     @Valid @RequestBody CreateAppointmentRequest request) {

        AppointmentResponse response = appointmentService.rescheduleAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment rescheduled successfully"));
    }


    // Call an appointment off: status becomes CANCELLED and the row is soft-deleted.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> cancel(@PathVariable Integer id) {

        appointmentService.cancelAppointment(id);
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled successfully"));
    }

}