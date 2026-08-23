package in.healthconnect.controller;

import in.healthconnect.dto.request.CreateSpecialtyRequest;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.service.SpecialityService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
