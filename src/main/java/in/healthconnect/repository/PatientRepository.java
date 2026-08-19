package in.healthconnect.repository;

import in.healthconnect.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Integer> {

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPatientCode(String patientCode);
}