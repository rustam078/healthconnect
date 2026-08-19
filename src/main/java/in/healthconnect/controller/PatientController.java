package in.healthconnect.controller;

import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.service.PatientService;
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
@RequestMapping("/api/v1/patients")
public class PatientController {

    @Autowired
    private PatientService patientService;

    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        PatientResponse patientResponse = patientService.createPatient(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success( patientResponse,"Patient created successfully"));
    }
}
