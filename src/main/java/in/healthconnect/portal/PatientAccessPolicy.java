package in.healthconnect.portal;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// One place that decides which patient rows the caller may see or change. Audited here only.
@Component
@RequiredArgsConstructor
public class PatientAccessPolicy {

    private final CurrentUserProvider currentUser;
    private final AppointmentRepository appointments;

    // The patient ids the caller may read; null means "no restriction" (ALL scope).
    public List<Integer> visiblePatientIds() {
        AccessScope scope = currentUser.currentScope();
        return switch (scope.scope()) {
            case ALL -> null;
            case SELF -> scope.patientId() == null ? List.of() : List.of(scope.patientId());
            case DEPARTMENT -> departmentPatientIds(scope);
        };
    }

    // Read guard: 404 (not 403) so out-of-scope ids cannot be probed.
    public void assertCanView(Integer patientId) {
        List<Integer> visible = visiblePatientIds();
        if (visible != null && !visible.contains(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }
    }

    // Write guard: a doctor may only write for a patient with an appointment assigned to them.
    public void assertCanEdit(Integer patientId) {
        AccessScope scope = currentUser.currentScope();
        boolean allowed = switch (scope.scope()) {
            case ALL -> true;
            case SELF -> patientId.equals(scope.patientId());
            case DEPARTMENT -> scope.doctorId() != null
                    && appointments.existsByDoctorIdAndPatientId(scope.doctorId(), patientId);
        };
        if (!allowed) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }
    }

    private List<Integer> departmentPatientIds(AccessScope scope) {
        if (scope.specialtyIds().isEmpty()) {
            return List.of();
        }
        return appointments.findDistinctPatientIdsByDoctorSpecialties(scope.specialtyIds());
    }
}
