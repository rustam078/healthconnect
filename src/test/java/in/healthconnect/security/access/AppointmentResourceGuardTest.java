package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// The appointment ownership guard: admin sees all, a patient only their own, a doctor their own
// (write) / their department (read); a miss is a 404 (ResourceNotFound), never a 403.
class AppointmentResourceGuardTest {

    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final PatientAccessPolicy patientAccessPolicy = mock(PatientAccessPolicy.class);
    private final AppointmentResourceGuard guard = new AppointmentResourceGuard(appointments, patientAccessPolicy);

    private AccessScope scope(DataScope dataScope, Integer patientId, Integer doctorId) {
        return new AccessScope(dataScope, patientId, doctorId, List.of());
    }

    @Test
    void adminSeesAndEditsAnyAppointment() {
        guard.assertVisible(99, scope(DataScope.ALL, null, null));
        guard.assertEditable(99, scope(DataScope.ALL, null, null));
        verifyNoInteractions(appointments);
    }

    @Test
    void patientSeesOnlyTheirOwn() {
        when(appointments.existsByIdAndPatientId(5, 10)).thenReturn(true);
        guard.assertVisible(5, scope(DataScope.SELF, 10, null));

        when(appointments.existsByIdAndPatientId(6, 10)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class,
                () -> guard.assertVisible(6, scope(DataScope.SELF, 10, null)));
    }

    @Test
    void patientMayNeverWrite() {
        assertThrows(ResourceNotFoundException.class,
                () -> guard.assertEditable(5, scope(DataScope.SELF, 10, null)));
    }

    @Test
    void doctorWritesOnlyTheirOwnAppointment() {
        when(appointments.existsByIdAndDoctorId(5, 20)).thenReturn(true);
        guard.assertEditable(5, scope(DataScope.DEPARTMENT, null, 20));

        when(appointments.existsByIdAndDoctorId(7, 20)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class,
                () -> guard.assertEditable(7, scope(DataScope.DEPARTMENT, null, 20)));
    }

    @Test
    void doctorReadDefersToDepartmentPatientView() {
        when(appointments.findPatientIdByAppointmentId(5)).thenReturn(Optional.of(30));
        guard.assertVisible(5, scope(DataScope.DEPARTMENT, null, 20));
        verify(patientAccessPolicy).assertCanView(30);
    }

    @Test
    void doctorReadOnMissingAppointmentIs404() {
        when(appointments.findPatientIdByAppointmentId(8)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> guard.assertVisible(8, scope(DataScope.DEPARTMENT, null, 20)));
    }
}
