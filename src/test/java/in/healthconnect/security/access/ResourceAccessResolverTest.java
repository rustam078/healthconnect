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
    void queryStringIsIgnored() {
        assertTrue(resolver.resolve("PATCH", "/api/v1/appointments/7/status?status=COMPLETED").isPresent());
    }

    @Test
    void unlistedRouteIsNotGuarded() {
        assertTrue(resolver.resolve("GET", "/api/v1/patients/7").isEmpty());
    }
}
