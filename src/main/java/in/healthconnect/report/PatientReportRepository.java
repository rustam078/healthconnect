package in.healthconnect.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientReportRepository extends JpaRepository<PatientReport, Integer> {

    // Newest first, for a patient's report list.
    List<PatientReport> findByPatientIdOrderByCreatedAtDesc(Integer patientId);

    // The owning patient of a report, for the resource guard (no entity load).
    @Query("select r.patientId from PatientReport r where r.id = :id")
    Optional<Integer> findPatientIdById(@Param("id") Integer id);

    // Reports attached to one appointment (for a visit's record).
    List<PatientReport> findByAppointmentIdOrderByCreatedAtDesc(Integer appointmentId);
}
