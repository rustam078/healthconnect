package in.healthconnect.service;

import in.healthconnect.dto.request.CreateSpecialtyRequest;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Specialty;
import in.healthconnect.repository.SpecialityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SpecialityService {

    private  final SpecialityRepository  specialityRepository;

    public SpecialtyResponse createSpeciality(
            CreateSpecialtyRequest createSpecialtyRequest) {


        if (specialityRepository.existsByNameIgnoreCase(createSpecialtyRequest.getName().trim())) {
            throw new IllegalArgumentException("Specialty with name '" + createSpecialtyRequest.getName() + "' already exists");}

        Specialty specialty = new Specialty();

        specialty.setName(createSpecialtyRequest.getName().trim());
        specialty.setDescription(createSpecialtyRequest.getDescription());

        specialty = specialityRepository.save(specialty);

      final SpecialtyResponse specialtyResponse = new SpecialtyResponse();
        specialtyResponse.setId(specialty.getId());
        specialtyResponse.setName(specialty.getName());
        specialtyResponse.setDescription(specialty.getDescription());
        specialtyResponse.setCreatedAt(specialty.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime());
        specialtyResponse.setUpdatedAt(specialty.getUpdatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime());

        return specialtyResponse;
    }

}
