package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateDoctorRequest;
import in.healthconnect.dto.request.DoctorFilterDto;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.service.DoctorService;
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

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Page<DoctorResponse>>> searchDoctor(
            @RequestParam(required = false)
            Integer doctorId,
            @RequestParam(required = false)
            String search,
            @RequestBody DoctorFilterDto doctorFilterDto,
            @PageableDefault(size = 20 ,sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable

    ) {

          return ResponseEntity.ok(ApiResponse.success(doctorService.searchDoctors(doctorFilterDto,search,doctorId, pageable),"Doctors retrieved successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoctorById(@PathVariable Integer id) {
        doctorService.deleteDoctorById(id);
        return ResponseEntity.ok(ApiResponse.success(null,"Doctor deleted successfully"));
    }

}
