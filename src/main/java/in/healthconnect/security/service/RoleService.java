package in.healthconnect.security.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.security.dto.PermissionGroup;
import in.healthconnect.security.dto.RoleRequest;
import in.healthconnect.security.dto.RoleResponse;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.entity.RolePermission;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Manage roles: list/get/create/update/delete, converting between the permission grid and
// the stored grants. System roles (like ADMIN) are protected from deletion.
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Integer id) {
        return RoleResponse.from(findRole(id));
    }

    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("A role named '" + request.name() + "' already exists.");
        }
        Role role = new Role();
        apply(role, request);
        return RoleResponse.from(roleRepository.save(role));
    }

    public RoleResponse update(Integer id, RoleRequest request) {
        Role role = findRole(id);
        apply(role, request);
        return RoleResponse.from(roleRepository.save(role));
    }

    public void delete(Integer id) {
        Role role = findRole(id);
        if (role.isSystem()) {
            throw new IllegalArgumentException("System roles cannot be deleted.");
        }
        roleRepository.delete(role);
    }

    private void apply(Role role, RoleRequest request) {
        role.setName(request.name());
        role.setDescription(request.description());
        role.setDataScope(request.dataScope() == null ? DataScope.SELF : request.dataScope());
        role.setPermissions(toGrants(request.permissions()));
    }

    // Flatten the per-module action lists into one grant per (module, action).
    private Set<RolePermission> toGrants(List<PermissionGroup> groups) {
        Set<RolePermission> grants = new LinkedHashSet<>();
        if (groups == null) {
            return grants;
        }
        for (PermissionGroup group : groups) {
            if (group.actions() == null) {
                continue;
            }
            group.actions().forEach(action -> grants.add(new RolePermission(group.module(), action)));
        }
        return grants;
    }

    private Role findRole(Integer id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }
}
