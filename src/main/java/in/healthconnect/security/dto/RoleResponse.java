package in.healthconnect.security.dto;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.model.PermissionAction;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// A role as the UI shows it, with its data scope and permissions grouped per module.
public record RoleResponse(Integer id, String name, String description, boolean system,
                           DataScope dataScope, List<PermissionGroup> permissions) {

    public static RoleResponse from(Role role) {
        Map<AppModule, List<PermissionAction>> byModule = role.getPermissions().stream()
                .collect(Collectors.groupingBy(RolePermission::getModule,
                        Collectors.mapping(RolePermission::getAction, Collectors.toList())));
        List<PermissionGroup> groups = byModule.entrySet().stream()
                .map(entry -> new PermissionGroup(entry.getKey(), entry.getValue()))
                .toList();
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystem(),
                role.getDataScope(), groups);
    }
}
