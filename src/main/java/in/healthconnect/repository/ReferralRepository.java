package in.healthconnect.repository;

import in.healthconnect.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Integer> {

    List<Referral> findByAppointmentIdOrderByIdAsc(Integer appointmentId);
}
