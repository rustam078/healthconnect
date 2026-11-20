package in.healthconnect.security.access;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.portal.AccessScope;
import in.healthconnect.portal.PatientAccessPolicy;
import in.healthconnect.report.PatientReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Ownership rules for a report: a report belongs to a patient, so visibility follows that
// patient's access (patient sees own, doctor their department, admin all).
@Component
@RequiredArgsConstructor
public class ReportResourceGuard implements ResourceGuard {

    private final PatientReportRepository reportRepository;
    private final PatientAccessPolicy patientAccessPolicy;

    @Override
    public ResourceType type() {
        return ResourceType.REPORT;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertVisible(Integer id, AccessScope scope) {
        patientAccessPolicy.assertCanView(patientIdOf(id));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertEditable(Integer id, AccessScope scope) {
        patientAccessPolicy.assertCanEdit(patientIdOf(id));
    }

    private Integer patientIdOf(Integer id) {
        return reportRepository.findPatientIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }
}
