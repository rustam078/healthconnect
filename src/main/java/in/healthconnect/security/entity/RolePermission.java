package in.healthconnect.security.entity;

import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;
import jakarta.persistence.*;
import lombok.*;

// One grant on a role: a module + an action (e.g. PATIENTS + CREATE).
// Stored in the role_permission table, one row per grant.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RolePermission {

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    private AppModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private PermissionAction action;

    // The Spring Security authority string, e.g. "PATIENTS_CREATE".
    public String authority() {
        return module.name() + "_" + action.name();
    }
}
