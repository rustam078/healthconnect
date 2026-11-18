package in.healthconnect.repository;

import in.healthconnect.entity.DoctorSpecialtyMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface DoctorSpecialtyRepository
        extends JpaRepository<DoctorSpecialtyMap, Integer> {

    List<DoctorSpecialtyMap> findByDoctorId(Integer doctorId);

    // All specialty links for several doctors in one query (specialty fetched to avoid N+1).
    @Query("select m from DoctorSpecialtyMap m join fetch m.specialty where m.doctor.id in :doctorIds")
    List<DoctorSpecialtyMap> findByDoctorIdIn(List<Integer> doctorIds);

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