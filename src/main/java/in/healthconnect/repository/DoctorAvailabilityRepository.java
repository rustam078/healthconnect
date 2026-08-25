package in.healthconnect.repository;

import in.healthconnect.entity.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface DoctorAvailabilityRepository
        extends JpaRepository<DoctorAvailability, Integer> {

    List<DoctorAvailability> findByDoctorIdAndDeletedFalseOrderByDayOfWeekAscStartTimeAsc(Integer doctorId);

    boolean existsByDoctorIdAndDayOfWeekAndStartTimeAndEndTimeAndDeletedFalse(
            Integer doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime);
}