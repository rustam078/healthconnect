package in.healthconnect.service;

import in.healthconnect.dto.request.CreateDoctorAvailabilityRequest;
import in.healthconnect.dto.request.DoctorAvailabilityRequest;
import in.healthconnect.dto.response.AssignDoctorSpecialtiesResponse;
import in.healthconnect.dto.response.DoctorAvailabilityResponse;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.dto.response.SpecialtyResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.DoctorAvailability;
import in.healthconnect.entity.DoctorSpecialtyMap;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.DoctorAvailabilityRepository;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.DoctorSpecialtyRepository;
import in.healthconnect.utils.DoctorAvailabilityUtils;
import in.healthconnect.utils.DoctorUtils;
import in.healthconnect.utils.SpecialityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorAvailabilityService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;
    private final DoctorSpecialtyRepository doctorSpecialtyRepository;
    @Transactional
    public AssignDoctorSpecialtiesResponse createOrUpdateDoctorAvailability(Integer doctorId, CreateDoctorAvailabilityRequest request) {

        // 1. Fetch doctor
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Doctor with id '" + doctorId + "' does not exist"));

        // 2. Fetch all existing availability ONCE
        List<DoctorAvailability> existingAvailability = doctorAvailabilityRepository.findByDoctorId(doctorId);

        // 3. Convert existing availability into Map
        Map<DayOfWeek, DoctorAvailability> availabilityMap = existingAvailability.stream().collect(Collectors.toMap(DoctorAvailability::getDayOfWeek, Function.identity()));
        List<DoctorAvailability> availabilityToSave = new ArrayList<>();

        // 4. Process request
        for (DoctorAvailabilityRequest item : request.getAvailability()) {
            validateAvailability(item);
            // Check whether availability already exists for this day
            DoctorAvailability availability = availabilityMap.get(item.dayOfWeek());
            if (availability != null) {
                // UPDATE existing record
                availability.setStartTime(item.startTime());
                availability.setEndTime(item.endTime());
                availability.setBreakStartTime(item.breakStartTime());
                availability.setBreakEndTime(item.breakEndTime());
            } else {
                // CREATE new record
                availability = DoctorAvailability.builder().doctor(doctor).dayOfWeek(item.dayOfWeek()).startTime(item.startTime()).endTime(item.endTime()).breakStartTime(item.breakStartTime()).breakEndTime(item.breakEndTime()).build();
            }

            availabilityToSave.add(availability);
        }
        // 5. Save new + updated records
        List<DoctorAvailability> saved = doctorAvailabilityRepository.saveAll(availabilityToSave);

        //Convert to response
        //  Map doctor
        DoctorResponse doctorResponse =
                DoctorUtils.mapToDoctorResponse(doctor);

        List<DoctorSpecialtyMap> existingAssociations =
                doctorSpecialtyRepository.findByDoctorId(doctorId);


        //  Map specialties
        List<SpecialtyResponse> specialtyResponses =
                existingAssociations.stream()
                        .map(DoctorSpecialtyMap::getSpecialty)
                        .map(SpecialityUtils::mapToSpecialityResponse)
                        .toList();
        List<DoctorAvailabilityResponse> list = saved.stream().map(DoctorAvailabilityUtils::mapToResponse).toList();
        //  Return response
        return new AssignDoctorSpecialtiesResponse(
                doctorResponse,
                specialtyResponses,
                list
        );
        // 6. Convert to response
//        return saved.stream()
//                .map(DoctorAvailabilityUtils::mapToResponse)
//                .toList();
//        return null;
    }


    private void validateAvailability(DoctorAvailabilityRequest item) {
        LocalTime startTime = item.startTime();
        LocalTime endTime = item.endTime();
        // 1. Start must be before end
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time for " + item.dayOfWeek());
        }
        // 2. Maximum availability duration = 10 hours
        long minutes = Duration.between(startTime, endTime).toMinutes();

        if (minutes > 10 * 60) {
            throw new IllegalArgumentException("Availability cannot exceed 10 hours for " + item.dayOfWeek());
        }

    }


}