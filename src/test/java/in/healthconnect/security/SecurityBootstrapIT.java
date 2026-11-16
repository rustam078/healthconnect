package in.healthconnect.security;

import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Creates the security tables in real MySQL (ddl-auto=update, this run only) and checks the
// seed ran. Also proves the whole security context wires up.
//   ./mvnw test -Dtest=SecurityBootstrapIT
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class SecurityBootstrapIT {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void adminRoleAndUserAreSeeded() {
        assertTrue(roleRepository.findByName("ADMIN").isPresent(), "ADMIN role should be seeded");
        assertTrue(userRepository.findByUsername("admin").isPresent(), "admin user should be seeded");
    }
}
