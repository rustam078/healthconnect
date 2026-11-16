package in.healthconnect.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

// The single place that authorizes every request. It asks the resolver what the request
// needs, then checks the logged-in user's authorities. No endpoint carries its own rule.
@Component
public class PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RequiredPermissionResolver resolver;

    public PermissionAuthorizationManager(RequiredPermissionResolver resolver) {
        this.resolver = resolver;
    }

    // Called by Spring Security for every secured request.
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        boolean granted = isGranted(authentication.get(), request.getMethod(), request.getRequestURI());
        return new AuthorizationDecision(granted);
    }

    // The decision itself, kept simple so it can be unit-tested without a web request.
    public boolean isGranted(Authentication authentication, String method, String path) {
        Optional<String> required = resolver.requiredAuthority(method, path);
        if (required.isEmpty() || !isAuthenticated(authentication)) {
            return false;
        }
        if (RequiredPermissionResolver.AUTHENTICATED.equals(required.get())) {
            return true;
        }
        return hasAuthority(authentication, required.get());
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
