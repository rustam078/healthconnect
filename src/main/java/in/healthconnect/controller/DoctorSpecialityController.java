package in.healthconnect.controller;

import in.healthconnect.dto.request.AssignDoctorSpecialtiesRequest;
import in.healthconnect.dto.response.AssignDoctorSpecialtiesResponse;
import in.healthconnect.service.DoctorSpecialtyService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DoctorSpecialityController {

    @Autowired
    private DoctorSpecialtyService doctorSpecialtyService;

    @PostMapping("/doctors/{doctorId}/specialties")
    public ResponseEntity<ApiResponse<AssignDoctorSpecialtiesResponse>> assignSpecialties(
            @PathVariable Integer doctorId, @Valid @RequestBody AssignDoctorSpecialtiesRequest request) {
        return ResponseEntity.ok(ApiResponse.success( doctorSpecialtyService.assignSpecialties(doctorId
                , request.specialtyIds()), "Specialties assigned successfully"));
    }


    @GetMapping("/doctors/{doctorId}/specialties")
    public ResponseEntity<ApiResponse<AssignDoctorSpecialtiesResponse>> getSpecialtieswithDoctorId(@PathVariable Integer doctorId) {

        return ResponseEntity.ok(ApiResponse.success( doctorSpecialtyService.getSpecialtieswithDoctorId(doctorId),"All the  Doctor and specialties assigned get successfully"));
    }

}

