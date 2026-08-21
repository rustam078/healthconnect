package in.healthconnect.repository;

import in.healthconnect.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor,Integer> {

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDoctorCode(String doctorCode);


}
