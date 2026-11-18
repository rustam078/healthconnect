package in.healthconnect.service;

import in.healthconnect.dto.request.*;
import in.healthconnect.specificationFilter.DoctorSpecification;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.exception.EmailExistException;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.service.UserService;
import in.healthconnect.utils.CommonUtils;
import in.healthconnect.utils.DoctorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorUtils doctorUtils;
    private final DoctorSpecialtyService doctorSpecialtyService;
    private final DoctorAvailabilityService doctorAvailabilityService;
    private final UserService userService;

    // Create a doctor, map any specialties, and optionally create their login - all in one
    // transaction so a failure (e.g. duplicate username) leaves nothing half-created.
    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        if (doctorRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new EmailExistException("Doctor with this email '" + request.getEmail() + "' already exists");
        }
        Doctor doctor = doctorRepository.save(buildDoctor(request, doctorUtils.generateDoctorCode()));
        assignSpecialtiesIfAny(doctor.getId(), request.getSpecialtyIds());
        createLoginIfAny(doctor.getId(), request.getLogin());
        createDefaultAvailityTime(doctor.getId());
        return DoctorUtils.mapToDoctorResponse(doctor);
    }

    private void createDefaultAvailityTime(Integer doctorId) {
        List<DoctorAvailabilityRequest> defaultAvailability = Arrays.stream(
                        new DayOfWeek[]{
                                DayOfWeek.MONDAY,
                                DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                                DayOfWeek.FRIDAY
                        })
                .map(day -> new DoctorAvailabilityRequest(
                        day,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0)
                ))
                .toList();

        CreateDoctorAvailabilityRequest request = CreateDoctorAvailabilityRequest.builder().availability(defaultAvailability).build();
        doctorAvailabilityService.createOrUpdateDoctorAvailability(doctorId, request);
    }

    private Doctor buildDoctor(CreateDoctorRequest r, String code) {
        return Doctor.builder().doctorCode(code)
                .firstName(r.getFirstName()).lastName(r.getLastName())
                .dateOfBirth(r.getDateOfBirth()).gender(r.getGender())
                .phone(r.getPhone()).email(r.getEmail()).qualification(r.getQualification())
                .experienceYears(r.getExperienceYears()).consultationFee(r.getConsultationFee()).build();
    }

    private void assignSpecialtiesIfAny(Integer doctorId, List<Integer> specialtyIds) {
        if (specialtyIds != null && !specialtyIds.isEmpty()) {
            doctorSpecialtyService.assignSpecialties(doctorId, specialtyIds);
        }
    }

    private void createLoginIfAny(Integer doctorId, PortalLoginRequest login) {
        if (login != null) {
            userService.create(new CreateUserRequest(login.username(), login.password(), login.roleId(), doctorId, null, true));
        }
    }

    public  Page<DoctorResponse> searchDoctors(DoctorFilterDto doctorFilterDto, String search, Integer doctorId, Pageable pageable) {
        if(doctorId!=null){
                Optional<Doctor> doctorOptional = doctorRepository.findById(doctorId);
                if (doctorOptional.isEmpty()) {return Page.empty(pageable);}
                return new PageImpl<>(List.of(withSpecialties(doctorOptional.get())), pageable, 1);
            }

        Page<Doctor> doctors = doctorRepository.findAll(
                DoctorSpecification.search(doctorFilterDto,search),
                pageable
        );
        List<Integer> ids = doctors.getContent().stream().map(Doctor::getId).toList();
        Map<Integer, List<String>> byDoctor = doctorSpecialtyService.specialtyNamesByDoctorIds(ids);
        return doctors.map((d) -> mapWithSpecialties(d, byDoctor.getOrDefault(d.getId(), List.of())));

    }

    // Map a doctor to its response and attach its specialty names.
    private DoctorResponse mapWithSpecialties(Doctor doctor, List<String> specialties) {
        DoctorResponse response = DoctorUtils.mapToDoctorResponse(doctor);
        response.setSpecialties(specialties);
        return response;
    }

    // Single-doctor variant: resolve its specialty names, then map.
    private DoctorResponse withSpecialties(Doctor doctor) {
        List<String> names = doctorSpecialtyService
                .specialtyNamesByDoctorIds(List.of(doctor.getId()))
                .getOrDefault(doctor.getId(), List.of());
        return mapWithSpecialties(doctor, names);
    }

    public void deleteDoctorById(Integer id) {
       Doctor doctor = doctorRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + id + "' does not exist"));

        doctorRepository.delete(doctor);
    }



    public DoctorResponse updateDoctor(Integer doctorId, CreateDoctorRequest request) {

        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(() ->
                        new ResourceNotFoundException("Doctor with id '" + doctorId + "' does not exist"));

        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            CommonUtils.validateEmail(email);
            if (doctorRepository.countByEmailIgnoreCaseAndDoctorIdNot(request.getEmail().trim(),doctorId)>0) {
                throw new EmailExistException("Doctor with this email '" + request.getEmail() + "' already exists");
            }
            doctor.setEmail(email);
        }

        if (request.getFirstName() != null) {doctor.setFirstName(request.getFirstName().trim());}

        if (request.getLastName() != null) {doctor.setLastName(request.getLastName().trim());}

        if (request.getPhone() != null) {doctor.setPhone(request.getPhone().trim());}

//        if (request.getGender() != null) {doctor.setGender(request.getGender());}
//
//        if (request.getDateOfBirth() != null) {doctor.setDateOfBirth(request.getDateOfBirth());}

        if (request.getQualification() != null) {doctor.setQualification(request.getQualification().trim());}

        if (request.getExperienceYears() != null) {doctor.setExperienceYears(request.getExperienceYears());}

        if (request.getConsultationFee() != null) {doctor.setConsultationFee(request.getConsultationFee());}

        // save
        doctorRepository.save(doctor);

        return DoctorUtils.mapToDoctorResponse(doctor);
    }
}
