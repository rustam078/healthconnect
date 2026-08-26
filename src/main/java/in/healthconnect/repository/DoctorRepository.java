package in.healthconnect.repository;

import in.healthconnect.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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

    Optional<Doctor> findByIdAndDeletedFalse(Integer doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT d
        FROM Doctor d
        WHERE d.id = :doctorId
          AND d.deleted = false
        """)
    Optional<Doctor> findActiveDoctorForUpdate(@Param("doctorId") Integer doctorId);
}
