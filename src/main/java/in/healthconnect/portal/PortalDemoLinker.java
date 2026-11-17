package in.healthconnect.portal;

import in.healthconnect.entity.Doctor;
import in.healthconnect.entity.Patient;
import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Dev convenience: link the seeded 'doctor' / 'patient' users to a real Doctor / Patient row
// so their portals show data straight away. Real linking is done via the admin Users screen.
@Component
@Order(2)
@RequiredArgsConstructor
public class PortalDemoLinker implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Override
    public void run(String... args) {
        userRepository.findByUsername("doctor").ifPresent(user -> {
            if (user.getDoctorId() == null) {
                firstDoctorId().ifPresent(id -> save(user, u -> u.setDoctorId(id)));
            }
        });
        userRepository.findByUsername("patient").ifPresent(user -> {
            if (user.getPatientId() == null) {
                firstPatientId().ifPresent(id -> save(user, u -> u.setPatientId(id)));
            }
        });
    }

    private Optional<Integer> firstDoctorId() {
        return doctorRepository.findAll(PageRequest.of(0, 1)).stream().findFirst().map(Doctor::getId);
    }

    private Optional<Integer> firstPatientId() {
        return patientRepository.findAll(PageRequest.of(0, 1)).stream().findFirst().map(Patient::getId);
    }

    private void save(User user, java.util.function.Consumer<User> change) {
        change.accept(user);
        userRepository.save(user);
    }
}
