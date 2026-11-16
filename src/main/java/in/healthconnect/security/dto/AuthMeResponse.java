package in.healthconnect.security.dto;

import java.util.List;

// The current user + permissions, re-fetched on every app load so the UI never trusts
// stale permissions from storage.
public record AuthMeResponse(UserResponse user, List<String> permissions) {
}
