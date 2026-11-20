package in.healthconnect.security.access;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;

// The allow-list of resource-scoped routes: method + path -> which resource, where its id sits
// in the URL, and whether the route reads or writes. The Layer-2 twin of
// RequiredPermissionResolver's OVERRIDES. Add one line here to put a new endpoint under
// per-row ownership control; routes not listed here rely on the permission tier alone.
@Component
public class ResourceAccessResolver {

    private final AntPathMatcher matcher = new AntPathMatcher();

    private static final List<Rule> RULES = List.of(
            // A consultation is read by the visit's patient, a doctor who may view that patient,
            // or an admin; it is written only by the visit's own doctor (or an admin).
            new Rule("GET", "/api/v1/appointments/*/consultation", ResourceType.APPOINTMENT, "appointmentId", Access.READ),
            new Rule("GET", "/api/v1/appointments/*/consultation/pdf", ResourceType.APPOINTMENT, "appointmentId", Access.READ),
            new Rule("POST", "/api/v1/appointments/*/consultation", ResourceType.APPOINTMENT, "appointmentId", Access.WRITE),
            // Changing a visit's status (e.g. COMPLETED) is a write on that appointment.
            new Rule("PATCH", "/api/v1/appointments/*/status", ResourceType.APPOINTMENT, "id", Access.WRITE),
            // The full visit record (consultation, tests, reports) is a read on that appointment.
            new Rule("GET", "/api/v1/appointments/*/detail", ResourceType.APPOINTMENT, "id", Access.READ));

    // The matching rule for this request, if the route is resource-scoped.
    public Optional<Rule> resolve(String method, String path) {
        String clean = stripQuery(path);
        return RULES.stream()
                .filter(rule -> rule.method().equalsIgnoreCase(method) && matcher.match(rule.pattern(), clean))
                .findFirst();
    }

    private String stripQuery(String path) {
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }

    // One scoped route: how to find its resource and whether it reads or writes it.
    public record Rule(String method, String pattern, ResourceType type, String idVariable, Access access) {
    }
}
