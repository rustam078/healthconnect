package in.healthconnect.exception;

// Thrown when server-side code needs an application setting that has not been set up yet
// (missing row, switched off, or blank value) - for example the NIM API key.
//
// This is a SERVER configuration problem, not a bad request, so it maps to 503. It has its
// own type so GlobalExceptionHandler can pass the message through: the message names the
// exact setting to add, which is the whole point of raising it.
public class SettingNotConfiguredException extends RuntimeException {
    public SettingNotConfiguredException(String message) {
        super(message);
    }
}
