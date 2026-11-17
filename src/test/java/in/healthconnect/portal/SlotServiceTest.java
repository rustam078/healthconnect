package in.healthconnect.portal;

import in.healthconnect.portal.SlotService.BookedRange;
import in.healthconnect.portal.SlotService.SlotWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests for the slot engine: build the open slots for a doctor on a date, honouring the
// break, already-booked appointments, and (for today) the current time.
class SlotServiceTest {

    private final SlotService slotService = new SlotService();

    private SlotWindow window(String start, String end, String breakStart, String breakEnd) {
        return new SlotWindow(LocalTime.parse(start), LocalTime.parse(end),
                breakStart == null ? null : LocalTime.parse(breakStart),
                breakEnd == null ? null : LocalTime.parse(breakEnd), 30);
    }

    @Test
    void generatesSlotsAcrossTheWindow() {
        List<LocalTime> slots = slotService.freeSlots(window("09:00", "11:00", null, null), List.of(), null);
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30)), slots);
    }

    @Test
    void skipsSlotsInsideTheBreak() {
        List<LocalTime> slots = slotService.freeSlots(window("09:00", "12:00", "10:00", "10:30"), List.of(), null);
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 30),
                LocalTime.of(11, 0), LocalTime.of(11, 30)), slots);
    }

    @Test
    void excludesBookedSlots() {
        List<BookedRange> booked = List.of(new BookedRange(LocalTime.of(9, 30), LocalTime.of(10, 0)));
        List<LocalTime> slots = slotService.freeSlots(window("09:00", "11:00", null, null), booked, null);
        assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(10, 30)), slots);
    }

    @Test
    void dropsSlotsBeforeTheCutoff() {
        List<LocalTime> slots = slotService.freeSlots(window("09:00", "11:00", null, null), List.of(), LocalTime.of(10, 0));
        assertEquals(List.of(LocalTime.of(10, 0), LocalTime.of(10, 30)), slots);
    }
}
