package in.healthconnect.utils;

import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.Patient;
import in.healthconnect.repository.PatientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Component
public class PatientUtils {
    @Autowired
    private PatientRepository patientRepository;

    public static PatientResponse mapToPatientResponse(Patient patient) {
        int age = Period.between(
                patient.getDateOfBirth(),
                LocalDate.now()
        ).getYears();

        return PatientResponse.builder()
                .patientCode(patient.getPatientCode())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .age(age)
                .gender(patient.getGender())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .address(patient.getAddress())
                .bloodGroup(patient.getBloodGroup())
                .build();
    }


    public  String generatePatientCode() {
        String patientCode;

        do {
            patientCode = "PAT-" +
                    UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();
        } while (patientRepository.existsByPatientCode(patientCode));
        return patientCode;
    }
}
