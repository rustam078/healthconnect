package in.healthconnect.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientReportRepository extends JpaRepository<PatientReport, Integer> {

    // Newest first, for a patient's report list.
    List<PatientReport> findByPatientIdOrderByCreatedAtDesc(Integer patientId);

    // Reports attached to one appointment (for a visit's record).
    List<PatientReport> findByAppointmentIdOrderByCreatedAtDesc(Integer appointmentId);
}
