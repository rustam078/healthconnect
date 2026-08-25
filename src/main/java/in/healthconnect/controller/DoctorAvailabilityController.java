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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.createOrUpdateDoctorAvailability(doctorId,request),"doctor Availability created successfully"));
    }

    @GetMapping("/{doctorId}/availability")
    public ResponseEntity<ApiResponse<?>> getDoctorAvailability(@PathVariable Integer doctorId)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.getDoctorAvailability(doctorId),"doctor Availability Get successfully"));
    }
}
