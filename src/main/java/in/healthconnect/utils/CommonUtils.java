package in.healthconnect.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Pattern;

@Component
public class CommonUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public static void validateEmail(String email) {

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: '" + email + "'");
        }
    }

    public static void validateDateAndTime(LocalDate appointmentDate, LocalTime startTime) {

        LocalDateTime appointmentDateTime = LocalDateTime.of(appointmentDate, startTime);

        if (appointmentDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment  time cannot be in the past");
        }
    }
}
