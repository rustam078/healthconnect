package in.healthconnect.community.exception;

import org.springframework.http.HttpStatus;

// A chat rule was broken (not a member, not an admin, etc.); carries the HTTP status to return.
public class CommunityException extends RuntimeException {

    private final HttpStatus status;

    public CommunityException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
