package in.healthconnect.utils;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class CommonUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public static void validateEmail(String email) {

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format: '" + email + "'");
        }
    }
}
