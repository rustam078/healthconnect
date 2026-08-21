package in.healthconnect.repository;

import in.healthconnect.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DoctorRepository extends JpaRepository<Doctor,Integer>, JpaSpecificationExecutor<Doctor> {

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDoctorCode(String doctorCode);


}
