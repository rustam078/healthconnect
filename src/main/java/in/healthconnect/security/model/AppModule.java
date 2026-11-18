package in.healthconnect.security.model;

import java.util.Optional;

// Fixed catalog of app modules. Each maps to a URL path segment, so authorization can
// work out which module a request targets. Admins pick from these - they cannot invent one.
public enum AppModule {
    DASHBOARD("dashboard"),
    PATIENTS("patients"),
    DOCTORS("doctors"),
    SPECIALTIES("specialties"),
    APPOINTMENTS("appointments"),
    SETTINGS("settings"),
    WIDGETS("widgets"),
    REPORTS("reports"),
    ROLES("roles"),
    USERS("users"),
    CUSTOMER_PORTAL("customer-portal"),
    DOCTOR_PORTAL("doctor-portal"),
    COMMUNITIES("communities");

    private final String pathSegment;

    AppModule(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    // Map a URL path segment (e.g. "patients") to its module.
    public static Optional<AppModule> fromPathSegment(String segment) {
        if (segment == null) {
            return Optional.empty();
        }
        for (AppModule module : values()) {
            if (module.pathSegment.equals(segment)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }
}
