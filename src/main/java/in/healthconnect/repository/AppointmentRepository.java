package in.healthconnect.repository;

import in.healthconnect.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Integer>,
                JpaSpecificationExecutor<Appointment> {
}