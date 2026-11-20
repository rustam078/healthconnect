package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Optional;

// Runs after Spring Security (which already checked the role's permission). For routes listed
// in ResourceAccessResolver it checks the logged-in user may touch THIS row - the id is read
// from the parsed URL, never trusted blindly. Unlisted routes pass straight through.
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceAccessInterceptor implements HandlerInterceptor {

    private final ResourceAccessResolver resolver;
    private final ResourceAccessGuard guard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        Optional<ResourceAccessResolver.Rule> rule = resolver.resolve(method, uri);
        if (rule.isEmpty()) {
            log.debug("[FLOW 3 · resource] {} {} -> no row-level guard (permission tier only)", method, uri);
            return true;
        }
        ResourceAccessResolver.Rule matched = rule.get();
        Integer id = idFrom(request, matched.idVariable());
        log.debug("[FLOW 3 · resource] {} {} -> {} guard, id={}, {}", method, uri, matched.type(), id, matched.access());
        guard.enforce(matched.type(), id, matched.access());
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
