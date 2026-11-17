package in.healthconnect.service;

import in.healthconnect.dto.request.CreateDoctorRequest;
import in.healthconnect.dto.request.PortalLoginRequest;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.Specialty;
import in.healthconnect.entity.enums.Gender;
import in.healthconnect.repository.SpecialityRepository;
import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.entity.Role;
import in.healthconnect.security.repository.RoleRepository;
import in.healthconnect.security.repository.UserRepository;
import in.healthconnect.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DoctorCombinedCreateTest {

    @Autowired private DoctorService doctorService;
    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private SpecialityRepository specialties;

    private Integer doctorRoleId() {
        return roles.findByName("DOCTOR").map(Role::getId).orElseThrow();
    }

    private Integer newSpecialtyId() {
        Specialty s = specialties.save(Specialty.builder().name("Cardio-" + UUID.randomUUID()).build());
        return s.getId();
    }

    private CreateDoctorRequest doctorRequest(String emailTag) {
        CreateDoctorRequest r = new CreateDoctorRequest();
        r.setFirstName("Grace");
        r.setLastName("Hopper");
        r.setEmail(emailTag + "@example.com");
        r.setPhone("9876543210");
        r.setGender(Gender.FEMALE);
        r.setDateOfBirth(LocalDate.of(1980, 1, 1));
        r.setQualification("MD");
        r.setExperienceYears(10);
        r.setConsultationFee(new BigDecimal("500.00"));
        return r;
    }

    @Test
    void createsDoctorSpecialtiesAndLinkedLogin() {
        String username = "dr." + UUID.randomUUID();
        CreateDoctorRequest req = doctorRequest("grace-" + UUID.randomUUID());
        req.setSpecialtyIds(List.of(newSpecialtyId()));
        req.setLogin(new PortalLoginRequest(username, "Doctor@123", doctorRoleId()));

        DoctorResponse created = doctorService.createDoctor(req);

        assertThat(users.findByUsername(username)).get()
                .extracting(u -> u.getDoctorId()).isEqualTo(created.getId());
    }

    @Test
    void duplicateLoginUsernameIsRejected() {
        String username = "dup." + UUID.randomUUID();
        userService.create(new CreateUserRequest(username, "Doctor@123", doctorRoleId(), null, null, true));

        CreateDoctorRequest req = doctorRequest("dup-" + UUID.randomUUID());
        req.setLogin(new PortalLoginRequest(username, "Doctor@123", doctorRoleId()));

        assertThatThrownBy(() -> doctorService.createDoctor(req))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
