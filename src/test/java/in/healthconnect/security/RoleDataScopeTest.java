package in.healthconnect.security;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleDataScopeTest {

    @Test
    void roleDefaultsToSelfScope() {
        assertThat(Role.builder().name("X").build().getDataScope()).isEqualTo(DataScope.SELF);
    }

    @Test
    void roleKeepsAssignedScope() {
        Role r = Role.builder().name("D").dataScope(DataScope.DEPARTMENT).build();
        assertThat(r.getDataScope()).isEqualTo(DataScope.DEPARTMENT);
    }
}
