package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import in.healthconnect.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Ownership rules for an appointment (and anything nested under it, e.g. its consultation):
// a patient sees only their own, a doctor their own/department, an admin (ALL) everything.
@Component
@RequiredArgsConstructor
public class AppointmentResourceGuard implements ResourceGuard {

    private final AppointmentRepository appointments;
    private final PatientAccessPolicy patientAccessPolicy;

    @Override
    public ResourceType type() {
        return ResourceType.APPOINTMENT;
    }

    // Read: the appointment's own patient, any doctor who may view that patient, or an admin.
    @Override
    @Transactional(readOnly = true)
    public void assertVisible(Integer id, AccessScope scope) {
        switch (scope.scope()) {
            case ALL -> { }
            case SELF -> requireOwnedByPatient(id, scope.patientId());
            case DEPARTMENT -> patientAccessPolicy.assertCanView(patientIdOf(id));
        }
    }

    // Write: only the appointment's own doctor, or an admin. A patient can never write.
    @Override
    @Transactional(readOnly = true)
    public void assertEditable(Integer id, AccessScope scope) {
        switch (scope.scope()) {
            case ALL -> { }
            case SELF -> notFound(id);
            case DEPARTMENT -> requireOwnedByDoctor(id, scope.doctorId());
        }
    }

    private void requireOwnedByPatient(Integer id, Integer patientId) {
        if (patientId == null || !appointments.existsByIdAndPatientId(id, patientId)) {
            notFound(id);
        }
    }

    private void requireOwnedByDoctor(Integer id, Integer doctorId) {
        if (doctorId == null || !appointments.existsByIdAndDoctorId(id, doctorId)) {
            notFound(id);
        }
    }

    private Integer patientIdOf(Integer id) {
        return appointments.findPatientIdByAppointmentId(id).orElseThrow(() -> notFoundEx(id));
    }

    private void notFound(Integer id) {
        throw notFoundEx(id);
    }

    private ResourceNotFoundException notFoundEx(Integer id) {
        return new ResourceNotFoundException("Appointment not found: " + id);
    }
}
