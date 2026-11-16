package in.healthconnect.security.config;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

// Seeds a starter ADMIN (all permissions) + admin user, and DOCTOR / CUSTOMER roles, the
// first time the app runs on an empty database. Dev convenience only.
@Component
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
        roleRepository.save(portalRole("DOCTOR", AppModule.DOCTOR_PORTAL));
        roleRepository.save(portalRole("CUSTOMER", AppModule.CUSTOMER_PORTAL));
        userRepository.save(adminUser(admin));
        log.warn("Seeded admin user 'admin' / 'admin123' — change this in production.");
    }

    private Role adminRole() {
        return Role.builder().name("ADMIN").description("Full access").system(true)
                .permissions(allPermissions()).build();
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

    // A starter portal role: see the dashboard and its own portal.
    private Role portalRole(String name, AppModule portal) {
        Set<RolePermission> permissions = new LinkedHashSet<>();
        permissions.add(new RolePermission(AppModule.DASHBOARD, PermissionAction.VIEW));
        permissions.add(new RolePermission(portal, PermissionAction.VIEW));
        return Role.builder().name(name).description(name + " access").permissions(permissions).build();
    }

    private User adminUser(Role admin) {
        return User.builder().username("admin").passwordHash(passwordEncoder.encode("admin123"))
                .enabled(true).role(admin).build();
    }
}
