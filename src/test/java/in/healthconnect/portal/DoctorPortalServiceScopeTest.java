package in.healthconnect.portal;

import in.healthconnect.dto.response.PatientResponse;
import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.report.ReportService;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.repository.ConsultationRepository;
import in.healthconnect.repository.OrderedTestRepository;
import in.healthconnect.repository.ReferralRepository;
import in.healthconnect.service.SlotAvailabilityService;
import in.healthconnect.service.SpecialityService;
import in.healthconnect.service.AppointmentService;
import in.healthconnect.service.ConsultationPdfService;
import in.healthconnect.service.ConsultationService;
import in.healthconnect.service.PatientService;
import in.healthconnect.service.TestOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// Verifies the doctor portal routes every patient read through the access policy, and builds
// the department list from the policy's visible ids.
class DoctorPortalServiceScopeTest {

    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final AppointmentService appointmentService = mock(AppointmentService.class);
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final ConsultationService consultationService = mock(ConsultationService.class);
    private final ConsultationPdfService consultationPdfService = mock(ConsultationPdfService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final PatientAccessPolicy policy = mock(PatientAccessPolicy.class);
    private final PatientService patientService = mock(PatientService.class);
    private final TestOrderService testOrderService = mock(TestOrderService.class);
    private final ConsultationRepository consultationRepository = mock(ConsultationRepository.class);
    private final OrderedTestRepository orderedTestRepository = mock(OrderedTestRepository.class);
    private final SlotAvailabilityService slotAvailabilityService = mock(SlotAvailabilityService.class);
    private final ReferralRepository referralRepository = mock(ReferralRepository.class);
    private final SpecialityService specialityService = mock(SpecialityService.class);

    private final DoctorPortalService service = new DoctorPortalService(currentUser, appointmentService,
            appointmentRepository, consultationService, consultationPdfService, reportService, policy,
            patientService, testOrderService, consultationRepository, orderedTestRepository,
            slotAvailabilityService, referralRepository, specialityService);

    @Test
    void departmentPatientsComeFromThePolicysVisibleIds() {
        when(policy.visiblePatientIds()).thenReturn(List.of(5, 6));
        List<PatientResponse> summaries = List.of(new PatientResponse());
        when(patientService.findSummaries(List.of(5, 6))).thenReturn(summaries);

        assertThat(service.departmentPatients()).isSameAs(summaries);
    }

    @Test
    void departmentPatientsIsEmptyForUnrestrictedScope() {
        when(policy.visiblePatientIds()).thenReturn(null);

        assertThat(service.departmentPatients()).isEmpty();
        verify(patientService, never()).findSummaries(any());
    }

    @Test
    void patientReportsAreGuardedByThePolicy() {
        doThrow(new ResourceNotFoundException("Patient not found: 9")).when(policy).assertCanView(9);

        assertThatThrownBy(() -> service.patientReports(9)).isInstanceOf(ResourceNotFoundException.class);
        verify(reportService, never()).listByPatient(9);
    }
}
