package in.healthconnect.service;

import in.healthconnect.DoctorSpecification;
import in.healthconnect.dto.request.CreateDoctorRequest;
import in.healthconnect.dto.request.DoctorFilterDto;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.exception.EmailExistException;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.utils.DoctorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorUtils doctorUtils;

    public DoctorResponse createDoctor(CreateDoctorRequest createDoctorRequest) {
        if (doctorRepository.existsByEmailIgnoreCase(createDoctorRequest.getEmail().trim())) {
            throw new EmailExistException("Doctor with this email '" + createDoctorRequest.getEmail() + "' already exists");
        }
        String doctorCode = doctorUtils.generateDoctorCode();

        //step3 create doctor objects using builder
       Doctor doctor = Doctor.builder()
                .doctorCode(doctorCode)
                .firstName(createDoctorRequest.getFirstName())
                .lastName(createDoctorRequest.getLastName())
                .dateOfBirth(createDoctorRequest.getDateOfBirth())
                .gender(createDoctorRequest.getGender())
                .phone(createDoctorRequest.getPhone())
                .email(createDoctorRequest.getEmail())
               .qualification(createDoctorRequest.getQualification())
                .experienceYears(createDoctorRequest.getExperienceYears())
               .consultationFee(createDoctorRequest.getConsultationFee())
                .build();

        //step4 save that save object
        doctor = doctorRepository.save(doctor);
        //step5 retun doctorResponce
        return DoctorUtils.mapToDoctorResponse(doctor);
    }

    public  Page<DoctorResponse> searchDoctors(DoctorFilterDto doctorFilterDto, String search, Integer doctorId, Pageable pageable) {
        if(doctorId!=null){
                Optional<Doctor> doctorOptional = doctorRepository.findById(doctorId);
                if (doctorOptional.isEmpty()) {return Page.empty(pageable);}
                DoctorResponse response = DoctorUtils.mapToDoctorResponse(doctorOptional.get());
                return new PageImpl<>(List.of(response), pageable, 1);
            }

        Page<Doctor> doctors = doctorRepository.findAll(
                DoctorSpecification.search(doctorFilterDto,search),
                pageable
        );
        return doctors.map(DoctorUtils::mapToDoctorResponse);

    }

    public void deleteDoctorById(Integer id) {
       Doctor doctor = doctorRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Doctor with id '" + id + "' does not exist"));

        doctorRepository.delete(doctor);
    }



    public DoctorResponse updateDoctor(Integer doctorId, CreateDoctorRequest request) {

        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow(() ->
                        new ResourceNotFoundException("Doctor with id '" + doctorId + "' does not exist"));

        if (request.getEmail() != null) {String email = request.getEmail().trim();
            if (doctorRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
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
