package in.healthconnect.repository;

import in.healthconnect.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<Patient, Integer>, JpaSpecificationExecutor<Patient> {


    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPatientCode(String patientCode);

}