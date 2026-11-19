package in.healthconnect.service;

import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.repository.AppointmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentScheduler {

    private final AppointmentRepository appointmentRepository;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void markNoShowAppointments() {

        LocalDate today = LocalDate.now(IST);
        LocalTime currentTime = LocalTime.now(IST);

        int updatedCount = appointmentRepository.markMissedAppointments(AppointmentStatus.SCHEDULED, AppointmentStatus.NO_SHOW, today, currentTime);
        log.info("Appointment scheduler completed. {} appointments marked as NO_SHOW", updatedCount);
    }
}