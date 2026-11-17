package in.healthconnect.portal;

import in.healthconnect.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DepartmentQueryTest {

    @Autowired
    private AppointmentRepository appointments;

    @Test
    void returnsEmptyForASpecialtyNobodyHas() {
        assertThat(appointments.findDistinctPatientIdsByDoctorSpecialties(List.of(999_999))).isEmpty();
    }
}
