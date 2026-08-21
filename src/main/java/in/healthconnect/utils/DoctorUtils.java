package in.healthconnect.utils;

import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.Doctor;
import in.healthconnect.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class DoctorUtils {
    @Autowired
    private DoctorRepository doctorRepository;

    public static DoctorResponse mapToDoctorResponse(Doctor doctor) {
        int age = Period.between(
                doctor.getDateOfBirth(),
                LocalDate.now()
        ).getYears();

        return DoctorResponse.builder()
                .id(doctor.getId())
                .doctorCode(doctor.getDoctorCode())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .dateOfBirth(doctor.getDateOfBirth())
                .age(age)
                .gender(doctor.getGender())
                .phone(doctor.getPhone())
                .email(doctor.getEmail())
                .qualification(doctor.getQualification())
                .experienceYears(doctor.getExperienceYears())
                .consultationFee(doctor.getConsultationFee())
                .createdAt(doctor.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .updatedAt(doctor.getUpdatedAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime())
                .build();
    }


    public  String generateDoctorCode() {
        String doctorCode;

        do {
            doctorCode = "DOCT-" +
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();
        } while (doctorRepository.existsByDoctorCode(doctorCode));
        return doctorCode;
    }
}
