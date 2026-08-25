package in.healthconnect.service;

import in.healthconnect.dto.response.AssignDoctorSpecialtiesResponse;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.DoctorSpecialty;
import in.healthconnect.entity.Specialty;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.DoctorSpecialtyRepository;
import in.healthconnect.repository.SpecialityRepository;
import in.healthconnect.utils.DoctorUtils;
import in.healthconnect.utils.SpecialityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorSpecialtyService {

    private final DoctorSpecialtyRepository doctorSpecialtyRepository;
    private final DoctorRepository doctorRepository;
    private final SpecialityRepository specialityRepository;

    public AssignDoctorSpecialtiesResponse assignSpecialties(Integer doctorId, List<Integer> request
    ) {
        //  Find the doctor.
        // Verify that the doctor exists.
        // Verify that the doctor is active.

        Doctor doctor = doctorRepository.findByIdAndDeletedFalse(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor with id '" + doctorId + "' does not exist or is inactive"));

        //   Find all supplied specialties.
        // Verify that all specialties exist.
        Set<Integer> specialtyIds = new HashSet<>(request);
        List<Specialty> specialties = specialityRepository.findAllByIdInAndDeletedFalse(specialtyIds);

        // Verify that all specialties are active.
        if (specialties.size() != specialtyIds.size()) {
            Set<Integer> existingSpecialtyIds = specialties.stream()
                    .map(Specialty::getId).collect(Collectors.toSet());

            Set<Integer> missingSpecialtyIds = new HashSet<>(specialtyIds);
            missingSpecialtyIds.removeAll(existingSpecialtyIds);

            throw new ResourceNotFoundException("This ids "+missingSpecialtyIds+" specialties  do not exist or are inactive");
        }

     // 5. Get doctor's existing active associations
        List<DoctorSpecialty> existingAssociations =
                doctorSpecialtyRepository.findByDoctorIdAndDeletedFalse(doctorId);

        // 6. Get IDs of already assigned specialties
        Set<Integer> existingSpecialtyIds = existingAssociations.stream()
                .map(DoctorSpecialty::getSpecialty)
                .map(Specialty::getId)
                .collect(Collectors.toSet());

        // 7. Determine missing specialties
        Set<Integer> missingSpecialtyIds = new HashSet<>(specialtyIds);
        missingSpecialtyIds.removeAll(existingSpecialtyIds);

        // 8. Create new associations
        List<DoctorSpecialty> newAssociations = new ArrayList<>();

        for (Specialty specialty : specialties) {
            if (missingSpecialtyIds.contains(specialty.getId())) {
                DoctorSpecialty doctorSpecialty = new DoctorSpecialty();

                doctorSpecialty.setDoctor(doctor);
                doctorSpecialty.setSpecialty(specialty);

                newAssociations.add(doctorSpecialty);
            }
        }

        // 9. Save new associations
        if (!newAssociations.isEmpty()) {
            doctorSpecialtyRepository.saveAll(newAssociations);
        }

        // 10. Get updated active associations
        List<DoctorSpecialty> updatedAssociations =
                doctorSpecialtyRepository.findByDoctorIdAndDeletedFalse(doctorId);

        // 11. Map specialties
        List<SpecialtyResponse> specialtyResponses =
                updatedAssociations.stream()
                        .map(DoctorSpecialty::getSpecialty)
                        .map(SpecialityUtils::mapToSpecialityResponse)
                        .toList();

        // 12. Map doctor
        DoctorResponse doctorResponse =
                DoctorUtils.mapToDoctorResponse(doctor);

        // 13. Return response
        return new AssignDoctorSpecialtiesResponse(
                doctorResponse,
                specialtyResponses
        );
    }
}