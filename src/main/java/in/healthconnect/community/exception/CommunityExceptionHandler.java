package in.healthconnect.community.exception;

import in.healthconnect.wrapper.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns a CommunityException into the app's standard ApiResponse error, with its own status.
// Ordered ahead of the global catch-all so the specific status wins.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "in.healthconnect.community")
public class CommunityExceptionHandler {

    @ExceptionHandler(CommunityException.class)
    public ResponseEntity<ApiResponse<Void>> handle(CommunityException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
    }
}
