package in.healthconnect.security.config;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.model.PermissionAction;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

// Seeds a starter ADMIN (all permissions) + admin user, and DOCTOR / CUSTOMER roles, the
// first time the app runs on an empty database. Dev convenience only.
@Component
@Order(1)
@RequiredArgsConstructor
public class SecurityDataInitializer implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        Role admin = roleRepository.save(adminRole());
        Role doctor = roleRepository.save(portalRole("DOCTOR", AppModule.DOCTOR_PORTAL, DataScope.DEPARTMENT));
        Role customer = roleRepository.save(portalRole("CUSTOMER", AppModule.CUSTOMER_PORTAL, DataScope.SELF));
        userRepository.save(user("admin", "Admin@123", admin));
        userRepository.save(user("doctor", "Doctor@123", doctor));
        userRepository.save(user("patient", "Patient@123", customer));
        log.warn("Seeded users: admin/Admin@123, doctor/Doctor@123, patient/Patient@123 — change in production.");
    }

    private Role adminRole() {
        return Role.builder().name("ADMIN").description("Full access").system(true)
                .dataScope(DataScope.ALL).permissions(allPermissions()).build();
    }

    private Set<RolePermission> allPermissions() {
        Set<RolePermission> permissions = new LinkedHashSet<>();
        for (AppModule module : AppModule.values()) {
            for (PermissionAction action : PermissionAction.values()) {
                permissions.add(new RolePermission(module, action));
            }
        }
        return permissions;
    }

    // A starter portal role: full use of its own portal (view/book/update/cancel); data is
    // still limited to the user's own rows by the role's data scope. The dashboard is admin-only.
    private Role portalRole(String name, AppModule portal, DataScope scope) {
        Set<RolePermission> permissions = new LinkedHashSet<>();
        for (PermissionAction action : PermissionAction.values()) {
            permissions.add(new RolePermission(portal, action));
        }
        return Role.builder().name(name).description(name + " access")
                .dataScope(scope).permissions(permissions).build();
    }

    private User user(String username, String rawPassword, Role role) {
        return User.builder().username(username).passwordHash(passwordEncoder.encode(rawPassword))
                .enabled(true).role(role).build();
    }
}
