package in.healthconnect.controller;

import in.healthconnect.dto.request.PatientSearchRequest;
import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.response.AppointmentResponse;
import in.healthconnect.dto.response.PatientDetailResponse;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;
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

//    @GetMapping
//    public ResponseEntity<ApiResponse< Page<PatientResponse>>>
//    getAllPatientOnPage( @PageableDefault(page = 0, size=20, sort="createdAt",
//                            direction = Sort.Direction.DESC )@RequestParam(required = false) Integer patientId, Pageable pageable)
//    {
//        Page<PatientResponse> patients = patientService.getAllPatientOnPage(pageable,patientId);
//        return ResponseEntity.ok(ApiResponse.success(patients));
//    }


    @GetMapping
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> searchPatients(
            @RequestParam(required = false)
            Integer patientId,
            @RequestParam(required = false)
            String search,
            @RequestParam(required = false)
            String firstName,
            @RequestParam(required = false)
            Gender gender,
            @RequestParam(required = false)
            BloodGroup bloodGroup,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable

    ) {

        PatientSearchRequest request =
                new PatientSearchRequest(
                        search,
                        firstName,
                        gender,
                        bloodGroup
                );
        return ResponseEntity
                .ok(ApiResponse.success(patientService.searchPatients(request,patientId, pageable),"Patients retrieved successfully"));
    }


    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatientById(@PathVariable Integer id, @RequestBody CreatePatientRequest patientRequest) {
        PatientResponse patientResponse = patientService.updateExistPatientById(id, patientRequest);

        return ResponseEntity.ok(ApiResponse.success(patientResponse,"Patient updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePatientById(@PathVariable Integer id) {
        patientService.deletePatientById(id);
        return ResponseEntity.ok(ApiResponse.success(null,"Patient deleted successfully"));
    }

    // ---------- patient record page ----------

    // One patient plus their summary numbers. This is also the only way to fetch a single
    // patient: the search endpoint above returns a page, not a record.
    @GetMapping("/{id}/details")
    public ResponseEntity<ApiResponse<PatientDetailResponse>> getPatientDetails(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(
                patientService.getPatientDetails(id), "Patient details fetched successfully"));
    }

    // That patient's visit history, newest first. Paged because a long-standing patient
    // has more visits than anyone wants to scroll in one response.
    @GetMapping("/{id}/appointments")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> getPatientAppointments(
            @PathVariable Integer id,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                patientService.getPatientAppointments(id, pageable),
                "Patient appointments fetched successfully"));
    }
}
