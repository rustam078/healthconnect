package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

// Runs after Spring Security (which already checked the role's permission). For routes listed
// in ResourceAccessResolver it checks the logged-in user may touch THIS row - the id is read
// from the parsed URL, never trusted blindly. Unlisted routes pass straight through.
@Component
@RequiredArgsConstructor
public class ResourceAccessInterceptor implements HandlerInterceptor {

    private final ResourceAccessResolver resolver;
    private final ResourceAccessGuard guard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        resolver.resolve(request.getMethod(), request.getRequestURI())
                .ifPresent(rule -> guard.enforce(rule.type(), idFrom(request, rule.idVariable()), rule.access()));
        return true;
    }

    // Pull the resource id out of Spring's already-parsed {pathVariable} map.
    @SuppressWarnings("unchecked")
    private Integer idFrom(HttpServletRequest request, String variable) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String raw = vars instanceof Map<?, ?> ? ((Map<String, String>) vars).get(variable) : null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException | NullPointerException e) {
            throw new ResourceNotFoundException("Resource not found");
        }
    }
}
