package in.healthconnect.controller;

import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.service.PatientService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping
    public ResponseEntity<ApiResponse< Page<PatientResponse>>>
    getAllPatientOnPage( @PageableDefault(page = 0, size=20, sort="createdAt",
                            direction = Sort.Direction.DESC )@RequestParam(required = false) Integer patientId, Pageable pageable)
    {
        Page<PatientResponse> patients = patientService.getAllPatientOnPage(pageable,patientId);
        return ResponseEntity.ok(ApiResponse.success(patients));
    }
}
