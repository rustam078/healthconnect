package in.healthconnect.repository;

import in.healthconnect.entity.OrderedTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderedTestRepository extends JpaRepository<OrderedTest, Integer> {

    List<OrderedTest> findByAppointmentIdOrderByIdAsc(Integer appointmentId);
}
