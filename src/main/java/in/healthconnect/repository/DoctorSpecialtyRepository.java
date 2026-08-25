package in.healthconnect.repository;

import in.healthconnect.entity.DoctorSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface DoctorSpecialtyRepository
        extends JpaRepository<DoctorSpecialty, Integer> {

    List<DoctorSpecialty> findByDoctorIdAndDeletedFalse(Integer doctorId);

    boolean existsByDoctorIdAndSpecialtyIdAndDeletedFalse(Integer doctorId, Integer specialtyId);

    void deleteByDoctorIdAndSpecialtyId(Integer doctorId, Integer specialtyId);
}