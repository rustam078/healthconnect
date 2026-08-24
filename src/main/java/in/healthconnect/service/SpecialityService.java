package in.healthconnect.service;

import in.healthconnect.dto.request.CreateSpecialtyRequest;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Specialty;
import in.healthconnect.repository.SpecialityRepository;
import in.healthconnect.utils.SpecialityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

        return SpecialityUtils.mapToSpecialityResponse(specialty);
    }

        public Page<SpecialtyResponse> getSpecialties(Integer specialityId, String search, Pageable pageable) {

        if(specialityId!=null)
            specialityRepository.findById(specialityId).orElseThrow(() -> new IllegalArgumentException("specialityId is not exist with id " + specialityId));

        Page<Specialty> specialties = specialityRepository.searchSpecialties(
                            specialityId, search, pageable);

        return specialties.map(SpecialityUtils::mapToSpecialityResponse);
        }


    public SpecialtyResponse updateSpeciality(Integer id,CreateSpecialtyRequest createSpecialtyRequest) {
        Specialty specialty1 = specialityRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("specialityId is not exist with id " + id));
        if(specialty1.getId() == id && specialityRepository.existsByNameIgnoreCase(createSpecialtyRequest.getName().trim())) {
            specialty1.setDescription(createSpecialtyRequest.getDescription());
        }else
        {
            throw new IllegalArgumentException( "speciality name does not match with description ");
        }
        specialty1 = specialityRepository.save(specialty1);
        return SpecialityUtils.mapToSpecialityResponse(specialty1);
    }
}
