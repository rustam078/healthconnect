package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateDoctorRequest;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.service.DoctorService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    @Autowired
    private DoctorService doctorService;

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorResponse>> createDoctor(@RequestBody @Valid CreateDoctorRequest createDoctorRequest)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doctorService.createDoctor(createDoctorRequest),"doctor created successfully"));
    }
}
