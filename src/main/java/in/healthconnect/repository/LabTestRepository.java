package in.healthconnect.repository;

import in.healthconnect.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabTestRepository extends JpaRepository<LabTest, Integer> {

    List<LabTest> findByActiveTrueOrderByNameAsc();
}
