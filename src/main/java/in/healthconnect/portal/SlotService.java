package in.healthconnect.portal;

import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// Builds the open appointment slots for a doctor on a date: a fixed-size grid across the
// day's window, minus the break, minus already-booked slots, minus times before a cutoff.
@Service
public class SlotService {

    public record SlotWindow(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd,
                             int slotMinutes) {
    }

    public record BookedRange(LocalTime start, LocalTime end) {
    }

    // notBefore: drop slots starting before it (used for "today"); null means no cutoff.
    public List<LocalTime> freeSlots(SlotWindow window, List<BookedRange> booked, LocalTime notBefore) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime start = window.start();
        while (!start.plusMinutes(window.slotMinutes()).isAfter(window.end())) {
            LocalTime end = start.plusMinutes(window.slotMinutes());
            if (isOpen(start, end, window, booked, notBefore)) {
                slots.add(start);
            }
            start = end;
        }
        return slots;
    }

    private boolean isOpen(LocalTime start, LocalTime end, SlotWindow window,
                           List<BookedRange> booked, LocalTime notBefore) {
        if (notBefore != null && start.isBefore(notBefore)) {
            return false;
        }
        if (overlapsBreak(start, end, window)) {
            return false;
        }
        return booked.stream().noneMatch(range -> overlaps(start, end, range.start(), range.end()));
    }

    private boolean overlapsBreak(LocalTime start, LocalTime end, SlotWindow window) {
        return window.breakStart() != null && window.breakEnd() != null
                && overlaps(start, end, window.breakStart(), window.breakEnd());
    }

    // Two [start, end) ranges overlap when each starts before the other ends.
    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
