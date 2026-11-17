package in.healthconnect.portal;

import in.healthconnect.repository.DoctorSpecialtyRepository;
import in.healthconnect.security.entity.User;
import in.healthconnect.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Resolves the logged-in user and their linked doctor/patient id, so portal queries are
// always scoped to the person making the request - never to an id from the URL.
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;
    private final DoctorSpecialtyRepository doctorSpecialtyRepository;

    @Transactional(readOnly = true)
    public User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Current user not found: " + username));
    }

    public Integer requirePatientId() {
        Integer patientId = currentUser().getPatientId();
        if (patientId == null) {
            throw new IllegalArgumentException("This account is not linked to a patient.");
        }
        return patientId;
    }

    public Integer requireDoctorId() {
        Integer doctorId = currentUser().getDoctorId();
        if (doctorId == null) {
            throw new IllegalArgumentException("This account is not linked to a doctor.");
        }
        return doctorId;
    }

    // The caller's data reach: role scope + their own ids + (for a doctor) their specialty ids.
    @Transactional(readOnly = true)
    public AccessScope currentScope() {
        User user = currentUser();
        List<Integer> specialtyIds = user.getDoctorId() == null ? List.of()
                : doctorSpecialtyRepository.findByDoctorId(user.getDoctorId()).stream()
                        .map(map -> map.getSpecialty().getId()).toList();
        return new AccessScope(user.getRole().getDataScope(), user.getPatientId(),
                user.getDoctorId(), specialtyIds);
    }
}
