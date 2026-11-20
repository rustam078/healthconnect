package in.healthconnect.security.access;

import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// One entry point the interceptor calls: resolve the caller's scope, then run the matching
// resource guard's read or write check. All per-row ownership logic funnels through here.
@Slf4j
@Component
public class ResourceAccessGuard {

    private final CurrentUserProvider currentUser;
    private final Map<ResourceType, ResourceGuard> guards;

    public ResourceAccessGuard(CurrentUserProvider currentUser, List<ResourceGuard> guardList) {
        this.currentUser = currentUser;
        this.guards = guardList.stream().collect(Collectors.toMap(ResourceGuard::type, Function.identity()));
    }

    // Enforce ownership for one resolved route; a miss throws 404 from inside the guard.
    public void enforce(ResourceType type, Integer id, Access access) {
        ResourceGuard guard = guards.get(type);
        if (guard == null) {
            throw new IllegalStateException("No resource guard registered for " + type);
        }
        AccessScope scope = currentUser.currentScope();
        if (access == Access.READ) {
            guard.assertVisible(id, scope);
        } else {
            guard.assertEditable(id, scope);
        }
        log.debug("[FLOW 3 · resource] {} {} id={} allowed (scope={})", access, type, id, scope.scope());
    }
}
