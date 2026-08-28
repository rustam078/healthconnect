package in.healthconnect.repository;

import in.healthconnect.entity.Appointment;
import in.healthconnect.entity.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer>, JpaSpecificationExecutor<Appointment> {

    @Query("""
            SELECT COUNT(a) > 0
            FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.appointmentDate = :appointmentDate
              AND a.deleted = false
              AND a.status <> :cancelledStatus
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsOverlappingAppointment(@Param("doctorId") Integer doctorId, @Param("appointmentDate") LocalDate appointmentDate, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime, @Param("cancelledStatus") AppointmentStatus cancelledStatus);

    // Same overlap check, but blind to one appointment: when rescheduling, the row being
    // moved is still sitting in its old slot and would otherwise clash with itself.
    @Query("""
            SELECT COUNT(a) > 0
            FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.appointmentDate = :appointmentDate
              AND a.deleted = false
              AND a.id <> :excludedId
              AND a.status <> :cancelledStatus
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsOverlappingAppointmentExcluding(@Param("doctorId") Integer doctorId, @Param("appointmentDate") LocalDate appointmentDate, @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime, @Param("cancelledStatus") AppointmentStatus cancelledStatus, @Param("excludedId") Integer excludedId);

    Page<Appointment> findByDoctorIdAndAppointmentDate(Integer doctorId, LocalDate appointmentDate, Pageable pageable);

     Page<Appointment> findByDoctorId(Integer doctorId,Pageable pageable);
}