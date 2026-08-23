package in.healthconnect.repository;

import in.healthconnect.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor,Integer>, JpaSpecificationExecutor<Doctor> {

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDoctorCode(String doctorCode);

    @Query(value = """
        SELECT COUNT(*)
        FROM doctors
        WHERE id <> :doctorId
          AND LOWER(email) = LOWER(:email)
          AND is_deleted = false
        """, nativeQuery = true)
    long countByEmailIgnoreCaseAndDoctorIdNot(
            @Param("email") String email,
            @Param("doctorId") Integer doctorId
    );

}
