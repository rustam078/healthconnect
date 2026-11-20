package in.healthconnect.security.access;

import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

// The patient guard delegates to PatientAccessPolicy, which already encodes SELF/DEPARTMENT/ALL.
class PatientResourceGuardTest {

    private final PatientAccessPolicy policy = mock(PatientAccessPolicy.class);
    private final PatientResourceGuard guard = new PatientResourceGuard(policy);

    @Test
    void visibleDelegatesToCanView() {
        guard.assertVisible(7, new AccessScope(DataScope.DEPARTMENT, null, 3, List.of()));
        verify(policy).assertCanView(7);
    }

    @Test
    void editableDelegatesToCanEdit() {
        guard.assertEditable(7, new AccessScope(DataScope.ALL, null, null, List.of()));
        verify(policy).assertCanEdit(7);
    }
}
