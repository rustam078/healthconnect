package in.healthconnect.portal;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientAccessPolicyTest {

    private final CurrentUserProvider provider = mock(CurrentUserProvider.class);
    private final AppointmentRepository appts = mock(AppointmentRepository.class);
    private final PatientAccessPolicy policy = new PatientAccessPolicy(provider, appts);

    @Test
    void selfCanViewOnlyOwnId() {
        when(provider.currentScope()).thenReturn(new AccessScope(DataScope.SELF, 7, null, List.of()));
        assertThatCode(() -> policy.assertCanView(7)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertCanView(8)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void departmentCanViewDeptPatientButEditsOnlyOwnAppointmentPatient() {
        when(provider.currentScope()).thenReturn(new AccessScope(DataScope.DEPARTMENT, null, 3, List.of(10)));
        when(appts.findDistinctPatientIdsByDoctorSpecialties(List.of(10))).thenReturn(List.of(5, 6));
        when(appts.existsByDoctorIdAndPatientId(3, 5)).thenReturn(false);

        assertThatCode(() -> policy.assertCanView(5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertCanView(9)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> policy.assertCanEdit(5)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void departmentWithNoSpecialtiesSeesNobody() {
        when(provider.currentScope()).thenReturn(new AccessScope(DataScope.DEPARTMENT, null, 3, List.of()));
        assertThat(policy.visiblePatientIds()).isEmpty();
        verify(appts, never()).findDistinctPatientIdsByDoctorSpecialties(anyList());
    }

    @Test
    void allScopeViewsAndEditsAnything() {
        when(provider.currentScope()).thenReturn(new AccessScope(DataScope.ALL, null, null, List.of()));
        assertThat(policy.visiblePatientIds()).isNull();
        assertThatCode(() -> policy.assertCanView(123)).doesNotThrowAnyException();
        assertThatCode(() -> policy.assertCanEdit(123)).doesNotThrowAnyException();
    }
}
