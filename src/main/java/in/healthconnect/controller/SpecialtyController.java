package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateSpecialtyRequest;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.service.SpecialityService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialityService  specialityService;

    @PostMapping
    public ResponseEntity<ApiResponse<SpecialtyResponse>> createSpecialty(@RequestBody @Valid CreateSpecialtyRequest createSpecialtyRequest){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(specialityService.createSpeciality(createSpecialtyRequest),"Specialty created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SpecialtyResponse>>> getSpecialties(
            @RequestParam(required = false) Integer specialityId,
            @RequestParam(required = false) String search,
            @PageableDefault(page = 0, size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        specialityService.getSpecialties(specialityId, search, pageable),
                        "Specialties retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> updateSpecialty(@PathVariable Integer id, @RequestBody  CreateSpecialtyRequest createSpecialtyRequest){

        return ResponseEntity.ok(ApiResponse.success( specialityService.updateSpeciality(id, createSpecialtyRequest),"Specialty updated successfully"));
    }


}
