package in.healthconnect.portal;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

// A doctor who has at least one open slot on the chosen date, with those slots.
public record AvailableDoctorResponse(Integer doctorId, String name, String qualification,
                                      BigDecimal consultationFee, List<LocalTime> slots) {
}
