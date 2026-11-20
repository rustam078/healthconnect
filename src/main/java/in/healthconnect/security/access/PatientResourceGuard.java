package in.healthconnect.security.access;

import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Ownership rules for a patient row: a patient sees only themselves, a doctor their department,
// an admin everyone. Delegates to the existing PatientAccessPolicy so the rule lives in one place.
@Component
@RequiredArgsConstructor
public class PatientResourceGuard implements ResourceGuard {

    private final PatientAccessPolicy patientAccessPolicy;

    @Override
    public ResourceType type() {
        return ResourceType.PATIENT;
    }

    @Override
    public void assertVisible(Integer id, AccessScope scope) {
        patientAccessPolicy.assertCanView(id);
    }

    @Override
    public void assertEditable(Integer id, AccessScope scope) {
        patientAccessPolicy.assertCanEdit(id);
    }
}
