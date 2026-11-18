package in.healthconnect.community.service;

import in.healthconnect.repository.DoctorRepository;
import in.healthconnect.repository.PatientRepository;
import in.healthconnect.security.entity.User;
import org.springframework.stereotype.Component;

// A friendly name to show in chat: the person's first + last name, falling back to their username.
@Component
public class DisplayNameResolver {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    public DisplayNameResolver(DoctorRepository doctorRepository, PatientRepository patientRepository) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
    }

    public String of(User user) {
        if (user.getDoctorId() != null) {
            return doctorRepository.findById(user.getDoctorId())
                    .map(d -> full(d.getFirstName(), d.getLastName())).orElse(user.getUsername());
        }
        if (user.getPatientId() != null) {
            return patientRepository.findById(user.getPatientId())
                    .map(p -> full(p.getFirstName(), p.getLastName())).orElse(user.getUsername());
        }
        return user.getUsername();
    }

    private String full(String first, String last) {
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }
}
