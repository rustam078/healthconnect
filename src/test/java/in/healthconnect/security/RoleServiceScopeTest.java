package in.healthconnect.security;

import in.healthconnect.security.dto.RoleRequest;
import in.healthconnect.security.dto.RoleResponse;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoleServiceScopeTest {

    @Autowired
    private RoleService roleService;

    @Test
    void createdRoleStoresDataScope() {
        RoleResponse r = roleService.create(
                new RoleRequest("WARD_NURSE", "desc", DataScope.DEPARTMENT, List.of()));
        assertThat(r.dataScope()).isEqualTo(DataScope.DEPARTMENT);
    }

    @Test
    void nullScopeDefaultsToSelf() {
        RoleResponse r = roleService.create(
                new RoleRequest("VOLUNTEER", "desc", null, List.of()));
        assertThat(r.dataScope()).isEqualTo(DataScope.SELF);
    }
}
