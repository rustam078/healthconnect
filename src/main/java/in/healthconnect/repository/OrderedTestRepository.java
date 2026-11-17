package in.healthconnect.repository;

import in.healthconnect.entity.OrderedTest;
import in.healthconnect.entity.enums.OrderedTestStatus;
import in.healthconnect.entity.projection.AppointmentLabOrderProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderedTestRepository extends JpaRepository<OrderedTest, Integer> {

    List<OrderedTest> findByAppointmentIdOrderByIdAsc(Integer appointmentId);

    // Appointment ids that have lab orders, pending ones first, oldest first. Optional id filter.
    @Query(value = """
            SELECT ot.appointmentId FROM OrderedTest ot
            WHERE (:appointmentId IS NULL OR ot.appointmentId = :appointmentId)
            GROUP BY ot.appointmentId
            ORDER BY MIN(CASE WHEN ot.status = :available THEN 1 ELSE 0 END) ASC, MIN(ot.createdAt) ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT ot.appointmentId) FROM OrderedTest ot
            WHERE (:appointmentId IS NULL OR ot.appointmentId = :appointmentId)
            """)
    Page<Integer> findAppointmentIdsWithOrders(@Param("appointmentId") Integer appointmentId,
                                               @Param("available") OrderedTestStatus available, Pageable pageable);


    @Query("""
    SELECT
        a.id AS appointmentId,
        p.id AS patientId,
        p.firstName AS firstName,
        p.lastName AS lastName,
        a.appointmentDate AS appointmentDate,
        p.dateOfBirth AS dateOfBirth,
        p.gender AS gender,
        p.bloodGroup AS bloodGroup,
        a.status AS appointmentStatus,
        ot.id AS orderedTestId,
        ot.reportId AS reportId,
        ot.category AS category,
        ot.status AS labStatus,
        ot.testName AS testName,
        r.originalFileName AS originalFileName,
        r.contentType AS contentType,
        r.fileSizeBytes AS fileSizeBytes,
        r.createdAt AS createdAt
    FROM OrderedTest ot
    JOIN Appointment a
        ON a.id = ot.appointmentId
    JOIN a.patient p
    LEFT JOIN PatientReport r
        ON r.id = ot.reportId
    WHERE a.id IN :appointmentIds
    ORDER BY a.id, ot.id
    """)
    List<AppointmentLabOrderProjection> findLabOrderDetails(@Param("appointmentIds") Collection<Integer> appointmentIds);
}
