package in.healthconnect.security.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.security.dto.PermissionGroup;
import in.healthconnect.security.dto.RoleRequest;
import in.healthconnect.security.dto.RoleResponse;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.model.PermissionAction;
import in.healthconnect.security.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for RoleService: the permission grid maps to grants, names are unique, system roles
// cannot be deleted.
class RoleServiceTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleService service = new RoleService(roleRepository);

    private RoleRequest request() {
        return new RoleRequest("NURSE", "Ward staff", DataScope.DEPARTMENT, List.of(
                new PermissionGroup(AppModule.PATIENTS, List.of(PermissionAction.VIEW, PermissionAction.CREATE))));
    }

    @Test
    void createMapsPermissionGridToGrants() {
        when(roleRepository.existsByName("NURSE")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(call -> call.getArgument(0));

        RoleResponse response = service.create(request());

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getPermissions().size()); // VIEW + CREATE on PATIENTS
        assertEquals("NURSE", response.name());
        assertEquals(List.of(PermissionAction.VIEW, PermissionAction.CREATE),
                response.permissions().get(0).actions());
    }

    @Test
    void createRejectsDuplicateName() {
        when(roleRepository.existsByName("NURSE")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(request()));
    }

    @Test
    void deleteRejectsSystemRole() {
        Role admin = Role.builder().name("ADMIN").system(true).build();
        admin.setId(1);
        when(roleRepository.findById(1)).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class, () -> service.delete(1));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesNonSystemRole() {
        Role nurse = Role.builder().name("NURSE").system(false).build();
        nurse.setId(2);
        when(roleRepository.findById(2)).thenReturn(Optional.of(nurse));

        service.delete(2);
        verify(roleRepository).delete(nurse);
    }

    @Test
    void getThrowsWhenMissing() {
        when(roleRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
    }
}
