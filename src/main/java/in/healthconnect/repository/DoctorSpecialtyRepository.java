package in.healthconnect.repository;

import in.healthconnect.entity.DoctorSpecialtyMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DoctorSpecialtyRepository
        extends JpaRepository<DoctorSpecialtyMap, Integer> {

    List<DoctorSpecialtyMap> findByDoctorId(Integer doctorId);

    // Doctors offering a given specialty (for availability-first patient search).
    List<DoctorSpecialtyMap> findBySpecialtyId(Integer specialtyId);


//    @Modifying
//    @Query("""
//    UPDATE DoctorSpecialtyMap ds
//    SET ds.deleted = true
//    WHERE ds.doctor.id = :doctorId
//      AND ds.specialty.id = :specialtyId
//      AND ds.deleted = false
//""")
    void deleteByDoctorIdAndSpecialtyId(Integer doctorId, Integer specialtyId);
}