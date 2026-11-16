package in.healthconnect.security.model;

import java.util.Optional;

// What a user can do in a module. VIEW = see it; the rest gate write actions.
public enum PermissionAction {
    VIEW,
    CREATE,
    UPDATE,
    DELETE;

    // Map an HTTP method to the action it represents.
    public static Optional<PermissionAction> fromHttpMethod(String method) {
        if (method == null) {
            return Optional.empty();
        }
        return switch (method.toUpperCase()) {
            case "GET", "HEAD" -> Optional.of(VIEW);
            case "POST" -> Optional.of(CREATE);
            case "PUT", "PATCH" -> Optional.of(UPDATE);
            case "DELETE" -> Optional.of(DELETE);
            default -> Optional.empty();
        };
    }
}
