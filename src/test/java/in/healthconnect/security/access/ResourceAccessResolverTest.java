package in.healthconnect.security.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests for ResourceAccessResolver: given a method + path, which resource/id/access applies?
// Only listed routes are resource-scoped; everything else is left to the permission tier.
class ResourceAccessResolverTest {

    private final ResourceAccessResolver resolver = new ResourceAccessResolver();

    @Test
    void consultationReadIsGuardedAsAppointmentRead() {
        ResourceAccessResolver.Rule rule = resolver.resolve("GET", "/api/v1/appointments/7/consultation").orElseThrow();
        assertEquals(ResourceType.APPOINTMENT, rule.type());
        assertEquals("appointmentId", rule.idVariable());
        assertEquals(Access.READ, rule.access());
    }

    @Test
    void consultationPdfIsGuardedAsRead() {
        assertEquals(Access.READ,
                resolver.resolve("GET", "/api/v1/appointments/7/consultation/pdf").orElseThrow().access());
    }

    @Test
    void consultationWriteIsGuardedAsAppointmentWrite() {
        assertEquals(Access.WRITE,
                resolver.resolve("POST", "/api/v1/appointments/7/consultation").orElseThrow().access());
    }

    @Test
    void statusChangeIsGuardedAsAppointmentWrite() {
        ResourceAccessResolver.Rule rule = resolver.resolve("PATCH", "/api/v1/appointments/7/status").orElseThrow();
        assertEquals(Access.WRITE, rule.access());
        assertEquals("id", rule.idVariable());
    }

    @Test
    void detailIsGuardedAsAppointmentRead() {
        ResourceAccessResolver.Rule rule = resolver.resolve("GET", "/api/v1/appointments/7/detail").orElseThrow();
        assertEquals(ResourceType.APPOINTMENT, rule.type());
        assertEquals("id", rule.idVariable());
        assertEquals(Access.READ, rule.access());
    }

    @Test
    void patientDetailsIsGuardedAsPatientRead() {
        ResourceAccessResolver.Rule rule = resolver.resolve("GET", "/api/v1/patients/7/details").orElseThrow();
        assertEquals(ResourceType.PATIENT, rule.type());
        assertEquals(Access.READ, rule.access());
    }

    @Test
    void orderedTestsIsGuardedAsAppointmentRead() {
        ResourceAccessResolver.Rule rule = resolver.resolve("GET", "/api/v1/reports/appointments/7/tests").orElseThrow();
        assertEquals(ResourceType.APPOINTMENT, rule.type());
        assertEquals("appointmentId", rule.idVariable());
    }

    @Test
    void reportFileIsGuardedAsReportRead() {
        ResourceAccessResolver.Rule rule = resolver.resolve("GET", "/api/v1/reports/7/file").orElseThrow();
        assertEquals(ResourceType.REPORT, rule.type());
        assertEquals("id", rule.idVariable());
        assertEquals(Access.READ, rule.access());
    }

    @Test
    void queryStringIsIgnored() {
        assertTrue(resolver.resolve("PATCH", "/api/v1/appointments/7/status?status=COMPLETED").isPresent());
    }

    @Test
    void appointmentListIsNotResourceScoped() {
        assertTrue(resolver.resolve("GET", "/api/v1/appointments").isEmpty());
    }

    @Test
    void unlistedRouteIsNotGuarded() {
        assertTrue(resolver.resolve("GET", "/api/v1/patients/7").isEmpty());
    }
}
