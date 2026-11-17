package in.healthconnect.service;

import in.healthconnect.entity.DoctorAvailability;
import in.healthconnect.entity.enums.AppointmentStatus;
import in.healthconnect.portal.SlotService;
import in.healthconnect.portal.SlotService.BookedRange;
import in.healthconnect.portal.SlotService.SlotWindow;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.DoctorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// A doctor's free slots on a date, from their availability minus break, booked and past times.
@Service
@RequiredArgsConstructor
public class SlotAvailabilityService {

    private static final int SLOT_MINUTES = 30;

    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotService slotService;

    @Transactional(readOnly = true)
    public List<LocalTime> freeSlots(Integer doctorId, LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        LocalTime notBefore = date.isEqual(LocalDate.now()) ? LocalTime.now() : null;
        List<DoctorAvailability> windows = availabilityRepository.findByDoctorIdAndDayOfWeek(doctorId, day);
        if (windows.isEmpty()) {
            return List.of();
        }
        List<BookedRange> booked = bookedRanges(doctorId, date);
        List<LocalTime> slots = new ArrayList<>();
        for (DoctorAvailability window : windows) {
            SlotWindow slotWindow = new SlotWindow(window.getStartTime(), window.getEndTime(),
                    window.getBreakStartTime(), window.getBreakEndTime(), SLOT_MINUTES);
            slots.addAll(slotService.freeSlots(slotWindow, booked, notBefore));
        }
        return slots.stream().distinct().sorted().toList();
    }

    private List<BookedRange> bookedRanges(Integer doctorId, LocalDate date) {
        return appointmentRepository
                .findByDoctorIdAndAppointmentDateAndStatusNot(doctorId, date, AppointmentStatus.CANCELLED)
                .stream().map(a -> new BookedRange(a.getStartTime(), a.getEndTime())).toList();
    }
}
