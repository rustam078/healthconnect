package in.healthconnect.security.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests for RequiredPermissionResolver: given an HTTP method + path, what authority is needed?
// Convention: path segment -> module, method -> action (GET=VIEW, POST=CREATE, PUT=UPDATE, DELETE=DELETE).
// Plus a few overrides for endpoints that break the convention.
class RequiredPermissionResolverTest {

    private final RequiredPermissionResolver resolver = new RequiredPermissionResolver();

    private String required(String method, String path) {
        return resolver.requiredAuthority(method, path).orElse(null);
    }

    // ---- convention ----

    @Test
    void getMapsToView() {
        assertEquals("PATIENTS_VIEW", required("GET", "/api/v1/patients"));
    }

    @Test
    void postMapsToCreate() {
        assertEquals("PATIENTS_CREATE", required("POST", "/api/v1/patients"));
    }

    @Test
    void putMapsToUpdate() {
        assertEquals("PATIENTS_UPDATE", required("PUT", "/api/v1/patients/5"));
    }

    @Test
    void deleteMapsToDelete() {
        assertEquals("DOCTORS_DELETE", required("DELETE", "/api/v1/doctors/3"));
    }

    @Test
    void queryStringIsIgnored() {
        assertEquals("PATIENTS_VIEW", required("GET", "/api/v1/patients?page=0&size=20"));
    }

    // ---- segment-to-module overrides (segments with no matching module path) ----

    @Test
    void modulesEndpointNeedsRolesView() {
        assertEquals("ROLES_VIEW", required("GET", "/api/v1/modules"));
    }

    @Test
    void boardsMapToDashboard() {
        assertEquals("DASHBOARD_VIEW", required("GET", "/api/v1/boards"));
    }

    @Test
    void aiKnowledgeMapsToWidgets() {
        assertEquals("WIDGETS_VIEW", required("GET", "/api/v1/ai/knowledge"));
        assertEquals("WIDGETS_CREATE", required("POST", "/api/v1/ai/examples"));
    }

    // ---- full-path action overrides (method would otherwise map wrongly) ----

    @Test
    void runningAWidgetIsAView() {
        assertEquals("WIDGETS_VIEW", required("POST", "/api/v1/widgets/active-doctors/data"));
    }

    @Test
    void integrationCallIsAView() {
        assertEquals("WIDGETS_VIEW", required("POST", "/api/v1/integration/active-doctors"));
    }

    @Test
    void doctorSearchIsAView() {
        assertEquals("DOCTORS_VIEW", required("POST", "/api/v1/doctors/search"));
    }

    @Test
    void creatingAGroupNeedsCreateButSendingAMessageIsAView() {
        assertEquals("COMMUNITIES_CREATE", required("POST", "/api/v1/communities"));
        assertEquals("COMMUNITIES_DELETE", required("DELETE", "/api/v1/communities/7"));
        assertEquals("COMMUNITIES_VIEW", required("POST", "/api/v1/communities/7/messages"));
        assertEquals("COMMUNITIES_VIEW", required("POST", "/api/v1/communities/7/join"));
        assertEquals("COMMUNITIES_VIEW", required("DELETE", "/api/v1/communities/7/members/3"));
        assertEquals("COMMUNITIES_UPDATE", required("POST", "/api/v1/communities/7/members"));
    }

    @Test
    void readingSpecialtiesOnlyNeedsALogin() {
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/specialties"));
        assertEquals("SPECIALTIES_CREATE", required("POST", "/api/v1/specialties"));
    }

    @Test
    void consultationAndStatusOnlyNeedALogin() {
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/appointments/7/consultation"));
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/appointments/7/consultation/pdf"));
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("POST", "/api/v1/appointments/7/consultation"));
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("PATCH", "/api/v1/appointments/7/status"));
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/appointments/7/detail"));
    }

    @Test
    void appointmentListNeedsAppointmentsView() {
        assertEquals("APPOINTMENTS_VIEW", required("GET", "/api/v1/appointments"));
    }

    @Test
    void fileApiOnlyNeedsALogin() {
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("POST", "/api/v1/files"));
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/files/42"));
    }

    @Test
    void authMeNeedsOnlyAuthentication() {
        assertEquals(RequiredPermissionResolver.AUTHENTICATED, required("GET", "/api/v1/auth/me"));
    }

    // ---- unknown / undeclared => deny (empty) ----

    @Test
    void unknownModuleIsDenied() {
        assertNull(required("GET", "/api/v1/something-random"));
    }

    @Test
    void unknownMethodIsDenied() {
        assertNull(required("TRACE", "/api/v1/patients"));
    }
}
