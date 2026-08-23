package in.healthconnect.repository;

import in.healthconnect.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialityRepository extends JpaRepository<Specialty, Integer > {
    boolean existsByNameIgnoreCase(String name);
}
