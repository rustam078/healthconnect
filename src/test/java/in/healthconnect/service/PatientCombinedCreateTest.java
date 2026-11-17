package in.healthconnect.service;

import in.healthconnect.dto.request.CreatePatientRequest;
import in.healthconnect.dto.request.PortalLoginRequest;
import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.entity.enums.BloodGroup;
import in.healthconnect.entity.enums.Gender;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PatientCombinedCreateTest {

    @Autowired private PatientService patientService;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    private Integer customerRoleId() {
        return roles.findByName("CUSTOMER").map(Role::getId).orElseThrow();
    }

    @Test
    void createsPatientAndLinkedLogin() {
        String username = "pt." + UUID.randomUUID();
        CreatePatientRequest r = new CreatePatientRequest();
        r.setFirstName("Alan");
        r.setLastName("Turing");
        r.setDateOfBirth(LocalDate.of(1990, 5, 5));
        r.setGender(Gender.MALE);
        r.setPhone("9876500000");
        r.setEmail("alan-" + UUID.randomUUID() + "@example.com");
        r.setAddress("1 Test St");
        r.setBloodGroup(BloodGroup.O_POSITIVE);
        r.setLogin(new PortalLoginRequest(username, "Patient@123", customerRoleId()));

        PatientResponse created = patientService.createPatient(r);

        assertThat(users.findByUsername(username)).get()
                .extracting(u -> u.getPatientId()).isEqualTo(created.getId());
    }
}
