package in.healthconnect.exception;

import in.healthconnect.wrapper.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);


    // 400 - Validation Error
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(Collectors.toMap(
                                FieldError::getField,
                                FieldError::getDefaultMessage,
                                (a, b) -> a
                        ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError(fieldErrors));
    }


    // 400 - Illegal Argument
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            IllegalArgumentException ex) {

        log.debug("Bad request: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String parameterName = ex.getName();
        String invalidValue = String.valueOf(ex.getValue());

        String message;

        if (ex.getRequiredType() != null &&
                ex.getRequiredType().isEnum()) {

            Object[] enumConstants =
                    ex.getRequiredType().getEnumConstants();

            String allowedValues = java.util.Arrays.stream(enumConstants)
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));

            message = String.format(
                    "Invalid value '%s' for parameter '%s'. Allowed values: %s",
                    invalidValue,
                    parameterName,
                    allowedValues
            );

        } else {

            message = String.format(
                    "Invalid value '%s' for parameter '%s'",
                    invalidValue,
                    parameterName
            );
        }

        log.debug("Method argument type mismatch: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {

        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType() != null
                && invalidFormatException.getTargetType().isEnum()) {

            String fieldName = "field";

            if (invalidFormatException.getPath() != null
                    && !invalidFormatException.getPath().isEmpty()) {

                fieldName = invalidFormatException
                        .getPath()
                        .get(0)
                        .getPropertyName();
            }

            String invalidValue =
                    String.valueOf(invalidFormatException.getValue());

            String allowedValues =
                    Arrays.stream(
                                    invalidFormatException
                                            .getTargetType()
                                            .getEnumConstants()
                            )
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));

            String message = String.format(
                    "Invalid value '%s' for field '%s'. Allowed values: %s",
                    invalidValue,
                    fieldName,
                    allowedValues
            );

            log.debug("Invalid enum value: {}", message);

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(message));
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request body"));
    }

    // 404 - Resource Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException ex) {

        log.debug("Not found: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }


    // 503 - a required application setting has not been configured yet
    @ExceptionHandler(SettingNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSettingNotConfigured(
            SettingNotConfiguredException ex) {

        log.error("Setting not configured: {}", ex.getMessage());

        // The message names the setting to add, so it is safe and useful to pass through.
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // 409 - Email Already Exists
    @ExceptionHandler(EmailExistException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailExist(
            EmailExistException ex) {

        log.debug("Email already exists: {}", ex.getMessage());

        List<String> errors = List.of(
                "Email already registered"
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        ex.getMessage(),
                        errors
                ));
    }


    // 409 - Appointment Already Exists
    @ExceptionHandler(AppointmentConflictException.class)
    public ResponseEntity<ApiResponse<Void>> AppointmentConflict(AppointmentConflictException ex) {
        log.debug("Appointment already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
    }



    // 405 - Method Not Allowed
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {

        System.out.println("🔥🔥🔥 405 HANDLER REACHED 🔥🔥🔥");

        String supported =
                ex.getSupportedHttpMethods() != null
                        && !ex.getSupportedHttpMethods().isEmpty()
                        ? ex.getSupportedHttpMethods()
                        .iterator()
                        .next()
                        .name()
                        : "unknown";

        String message = String.format(
                "Method not allowed. This endpoint requires %s.",
                supported
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(message));
    }


    // 500 - Generic Exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(
            Exception ex) {

        log.error("Unhandled error", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An error occurred"));
    }
}