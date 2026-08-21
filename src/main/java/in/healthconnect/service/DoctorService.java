package in.healthconnect.service;

import in.healthconnect.dto.request.CreateDoctorRequest;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.exception.EmailExistException;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.utils.DotorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DotorUtils  dotorUtils;

    public DoctorResponse createDoctor(CreateDoctorRequest createDoctorRequest) {
        if (doctorRepository.existsByEmailIgnoreCase(createDoctorRequest.getEmail().trim())) {
            throw new EmailExistException("Doctor with this email '" + createDoctorRequest.getEmail() + "' already exists");
        }
        String doctorCode = dotorUtils.generateDoctorCode();

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
        return dotorUtils.mapToDotorResponse(doctor);
    }
}
