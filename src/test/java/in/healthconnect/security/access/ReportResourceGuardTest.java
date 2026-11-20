package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import in.healthconnect.report.PatientReportRepository;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// The report guard maps a report to its owning patient, then defers to that patient's access rules.
class ReportResourceGuardTest {

    private final PatientReportRepository reports = mock(PatientReportRepository.class);
    private final PatientAccessPolicy policy = mock(PatientAccessPolicy.class);
    private final ReportResourceGuard guard = new ReportResourceGuard(reports, policy);

    private AccessScope scope() {
        return new AccessScope(DataScope.SELF, 10, null, List.of());
    }

    @Test
    void visibleChecksTheReportsPatient() {
        when(reports.findPatientIdById(5)).thenReturn(Optional.of(10));
        guard.assertVisible(5, scope());
        verify(policy).assertCanView(10);
    }

    @Test
    void missingReportIsNotFound() {
        when(reports.findPatientIdById(9)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> guard.assertVisible(9, scope()));
    }
}
