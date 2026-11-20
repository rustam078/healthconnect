package in.healthconnect.security.web;

import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Works out which authority a request needs, in one place, so no endpoint carries its own
// security annotation. Convention: path segment -> module, HTTP method -> action. A few
// overrides cover endpoints that break the convention.
@Component
public class RequiredPermissionResolver {

    // Sentinel: the request only needs a logged-in user, no specific permission.
    public static final String AUTHENTICATED = "AUTHENTICATED";

    private static final String API_PREFIX = "/api/v1/";
    private final AntPathMatcher matcher = new AntPathMatcher();

    // Segments whose name does not match a module's path segment.
    private static final Map<String, AppModule> SEGMENT_MODULES = Map.of(
            "ai", AppModule.WIDGETS,
            "integration", AppModule.WIDGETS,
            "modules", AppModule.ROLES,
            "boards", AppModule.DASHBOARD);

    // Endpoints where the HTTP method would map to the wrong action.
    private static final List<Override> OVERRIDES = List.of(
            new Override("GET", "/api/v1/auth/me", AUTHENTICATED),
            new Override("POST", "/api/v1/widgets/*/data", "WIDGETS_VIEW"),
            new Override("POST", "/api/v1/integration/**", "WIDGETS_VIEW"),
            // Doctor search is a POST but only reads, so VIEW is enough.
            new Override("POST", "/api/v1/doctors/search", "DOCTORS_VIEW"),
            // Chat actions a member performs are reads, not group create/delete.
            new Override("POST", "/api/v1/communities/*/messages", "COMMUNITIES_VIEW"),
            new Override("DELETE", "/api/v1/communities/*/messages/*", "COMMUNITIES_VIEW"),
            new Override("POST", "/api/v1/communities/*/join", "COMMUNITIES_VIEW"),
            new Override("POST", "/api/v1/communities/*/read", "COMMUNITIES_VIEW"),
            new Override("DELETE", "/api/v1/communities/*/members/*", "COMMUNITIES_VIEW"),
            new Override("POST", "/api/v1/communities/*/members", "COMMUNITIES_UPDATE"),
            // The file API authorizes per file inside the controller, so it only needs a login here.
            new Override("POST", "/api/v1/files/**", AUTHENTICATED),
            new Override("GET", "/api/v1/files/**", AUTHENTICATED),
            // Any logged-in user may set their own profile photo (not a USERS admin action).
            new Override("POST", "/api/v1/users/me/avatar", AUTHENTICATED),
            // Specialties are shared reference data: any logged-in role may read the list
            // (create/update/delete stay SPECIALTIES_* admin by convention).
            new Override("GET", "/api/v1/specialties/**", AUTHENTICATED));

    public Optional<String> requiredAuthority(String method, String path) {
        String cleanPath = stripQuery(path);
        Optional<String> override = matchOverride(method, cleanPath);
        if (override.isPresent()) {
            return override;
        }
        return byConvention(method, cleanPath);
    }

    // Build MODULE_ACTION from the first path segment and the HTTP method.
    private Optional<String> byConvention(String method, String path) {
        Optional<AppModule> module = firstSegment(path).flatMap(this::moduleFor);
        Optional<PermissionAction> action = PermissionAction.fromHttpMethod(method);
        if (module.isEmpty() || action.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(module.get().name() + "_" + action.get().name());
    }

    private Optional<String> matchOverride(String method, String path) {
        return OVERRIDES.stream()
                .filter(rule -> rule.method.equalsIgnoreCase(method) && matcher.match(rule.pattern, path))
                .map(rule -> rule.authority)
                .findFirst();
    }

    private Optional<AppModule> moduleFor(String segment) {
        return AppModule.fromPathSegment(segment)
                .or(() -> Optional.ofNullable(SEGMENT_MODULES.get(segment)));
    }

    private Optional<String> firstSegment(String path) {
        if (!path.startsWith(API_PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(API_PREFIX.length());
        int slash = rest.indexOf('/');
        String segment = slash >= 0 ? rest.substring(0, slash) : rest;
        return segment.isBlank() ? Optional.empty() : Optional.of(segment);
    }

    private String stripQuery(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    private record Override(String method, String pattern, String authority) {
    }
}
