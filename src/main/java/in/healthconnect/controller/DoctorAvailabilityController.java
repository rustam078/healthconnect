package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateDoctorAvailabilityRequest;
import in.healthconnect.service.DoctorAvailabilityService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorAvailabilityController {
    @Autowired
    private DoctorAvailabilityService service;

    @PostMapping("/{doctorId}/availability")
    public ResponseEntity<ApiResponse<?>> createOrUpdateDoctor(@PathVariable Integer doctorId, @RequestBody @Valid CreateDoctorAvailabilityRequest request)
    {
        try {
            String DoctorAvailability = service.createOrUpdateDoctorAvailability(doctorId, request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(DoctorAvailability));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/details/{doctorId}")
    public ResponseEntity<ApiResponse<?>> getDoctorDetails(@PathVariable Integer doctorId)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.getDoctorDetails(doctorId),"doctor Availability Get successfully"));
    }


    @DeleteMapping("{doctorId}/availability/{availabilityId}")
    public ResponseEntity<ApiResponse<?>> deleteDoctorAvailability(@PathVariable Integer doctorId, @PathVariable Integer availabilityId) {
        service.deleteDoctorAvailability(doctorId, availabilityId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Doctor availability deleted successfully"));
    }

}
