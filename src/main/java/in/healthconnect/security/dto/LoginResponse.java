package in.healthconnect.security.dto;

import java.util.List;

// What a successful login returns: the token plus who the user is and what they can do.
public record LoginResponse(String token, UserResponse user, List<String> permissions) {
}
