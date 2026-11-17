package in.healthconnect.security.dto;

import in.healthconnect.security.model.DataScope;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

// Create/update a role: a name, its data scope, and the permission grid (per-module allowed actions).
public record RoleRequest(@NotBlank String name, String description, DataScope dataScope,
                          List<PermissionGroup> permissions) {
}
