package in.healthconnect.repository;

import in.healthconnect.entity.Consultation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Integer> {

    // Guards the "one consultation per appointment" rule before we try to save a second one.
    boolean existsByAppointmentId(Integer appointmentId);

    // medicines + appointment pulled in one shot so the read never fires an N+1 storm and
    // the response mapping runs off already-loaded data.
    @EntityGraph(attributePaths = {"medicines", "appointment"})
    Optional<Consultation> findByAppointmentId(Integer appointmentId);
}
