package in.healthconnect.repository;

import in.healthconnect.entity.DoctorSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface DoctorSpecialtyRepository
        extends JpaRepository<DoctorSpecialty, Integer> {

    List<DoctorSpecialty> findByDoctorIdAndDeletedFalse(Integer doctorId);

    boolean existsByDoctorIdAndSpecialtyIdAndDeletedFalse(Integer doctorId, Integer specialtyIds);

//    @Modifying
//    @Query("""
//    UPDATE DoctorSpecialty ds
//    SET ds.deleted = true
//    WHERE ds.doctor.id = :doctorId
//      AND ds.specialty.id = :specialtyId
//      AND ds.deleted = false
//""")
    void deleteByDoctorIdAndSpecialtyId(Integer doctorId, Integer specialtyId);
}