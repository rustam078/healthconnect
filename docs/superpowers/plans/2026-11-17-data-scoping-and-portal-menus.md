# Data Scoping & Menu-Driven Portals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each role its own *row-level* data visibility (patient→self, doctor→department, admin→all), turn the portals into real sidebar-menu pages, make the dashboard admin-only, and let an admin create a doctor/patient + their specialties + their login in one transactional save.

**Architecture:** Add a second access axis — `Role.dataScope` (ALL/DEPARTMENT/SELF) — enforced server-side by one `PatientAccessPolicy` component. "Department" = the doctor's Specialty mappings (no new entity). Row-scoping lives only in the portal endpoints; back-office CRUD stays full-access and is simply not granted to doctor/patient roles. The frontend mirrors both axes for UX only.

**Tech Stack:** Spring Boot 4 (Jackson 3 → `tools.jackson.databind`), Java 17, Spring Security 7, MySQL, Lombok, JUnit 5; React 19 + Vite + Ant Design 6 + axios + @tanstack/react-query + react-router-dom 7 + Redux Toolkit.

## Global Constraints

- Never `git commit`/`git push`; **stage only** (`git add`). Never stage `application.properties`, `package.json`, `package-lock.json`, `tsconfig.json`.
- No method > 25 lines; no method > 4 params (use a parameter object).
- Reuse existing services/classes first; new file only when necessary.
- Comments: one line, simple, only on important methods.
- Central authorization already exists — **no `@PreAuthorize`**; module×action is enforced by `PermissionAuthorizationManager`. This plan adds the *data* axis, not new endpoint annotations.
- Ownership/scope failures throw `ResourceNotFoundException` (404), never 403, so ids can't be probed.
- `ddl-auto=validate` is on; new columns need a one-off `ddl-auto=update` integration test (pattern: `@TestPropertySource(properties="spring.jpa.hibernate.ddl-auto=update")`) or explicit `ALTER TABLE`.
- Backend base package: `in.healthconnect`. FE base: `D:\HMS\healtconnectfe\src`.

---

## Phase A — Backend: the data-scope axis

### Task A1: `DataScope` enum + `Role.dataScope` field

**Files:**
- Create: `src/main/java/in/healthconnect/security/model/DataScope.java`
- Modify: `src/main/java/in/healthconnect/security/entity/Role.java`
- Test: `src/test/java/in/healthconnect/security/RoleDataScopeTest.java`

**Interfaces:**
- Produces: `enum DataScope { ALL, DEPARTMENT, SELF }`; `Role.getDataScope()/setDataScope(DataScope)`.

- [ ] **Step 1: Write the failing test**
```java
package in.healthconnect.security;

import in.healthconnect.security.entity.Role;
import in.healthconnect.security.model.DataScope;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoleDataScopeTest {
    @Test
    void roleDefaultsToSelfScope() {
        assertThat(Role.builder().name("X").build().getDataScope()).isEqualTo(DataScope.SELF);
    }
    @Test
    void roleKeepsAssignedScope() {
        Role r = Role.builder().name("D").dataScope(DataScope.DEPARTMENT).build();
        assertThat(r.getDataScope()).isEqualTo(DataScope.DEPARTMENT);
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./mvnw -o test -Dtest=RoleDataScopeTest` → compile error (no `DataScope`).

- [ ] **Step 3: Create the enum**
```java
package in.healthconnect.security.model;

// How many rows a role may see: everything, its department, or only its own.
public enum DataScope { ALL, DEPARTMENT, SELF }
```

- [ ] **Step 4: Add the field to `Role`** (after the `permissions` set):
```java
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 20)
    @Builder.Default
    private DataScope dataScope = DataScope.SELF;
```
Add import `import in.healthconnect.security.model.DataScope;`.

- [ ] **Step 5: Run, expect PASS** — `./mvnw -o test -Dtest=RoleDataScopeTest`.

- [ ] **Step 6: Stage** (never commit)
```bash
git add src/main/java/in/healthconnect/security/model/DataScope.java \
        src/main/java/in/healthconnect/security/entity/Role.java \
        src/test/java/in/healthconnect/security/RoleDataScopeTest.java
```

---

### Task A2: Carry `dataScope` through DTOs, service, and seed

**Files:**
- Modify: `security/dto/RoleRequest.java`, `security/dto/RoleResponse.java`, `security/service/RoleService.java`
- Modify: `security/config/SecurityDataInitializer.java`
- Test: `src/test/java/in/healthconnect/security/RoleServiceScopeTest.java`

**Interfaces:**
- Consumes: `RoleService.create(RoleRequest)`, `RoleResponse.from(Role)`.
- Produces: `RoleRequest(name, description, dataScope, permissions)`; `RoleResponse` includes `DataScope dataScope`.

- [ ] **Step 1: Failing test**
```java
package in.healthconnect.security;

import in.healthconnect.security.dto.RoleRequest;
import in.healthconnect.security.dto.RoleResponse;
import in.healthconnect.security.model.DataScope;
import in.healthconnect.security.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RoleServiceScopeTest {
    @Autowired RoleService roleService;

    @Test
    void createdRoleStoresDataScope() {
        RoleResponse r = roleService.create(
            new RoleRequest("WARD_NURSE", "desc", DataScope.DEPARTMENT, List.of()));
        assertThat(r.dataScope()).isEqualTo(DataScope.DEPARTMENT);
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./mvnw -o test -Dtest=RoleServiceScopeTest`.

- [ ] **Step 3: Update `RoleRequest`**
```java
public record RoleRequest(@NotBlank String name, String description,
                          DataScope dataScope, List<PermissionGroup> permissions) {
}
```
Add `import in.healthconnect.security.model.DataScope;`.

- [ ] **Step 4: Update `RoleResponse`** — add `DataScope dataScope` to the record components and pass `role.getDataScope()` in `from(...)`:
```java
public record RoleResponse(Integer id, String name, String description, boolean system,
                           DataScope dataScope, List<PermissionGroup> permissions) {
    public static RoleResponse from(Role role) {
        // ...existing byModule/groups code unchanged...
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.isSystem(), role.getDataScope(), groups);
    }
}
```
Add `import in.healthconnect.security.model.DataScope;`.

- [ ] **Step 5: Update `RoleService.apply(...)`** — add one line (default SELF when null):
```java
    private void apply(Role role, RoleRequest request) {
        role.setName(request.name());
        role.setDescription(request.description());
        role.setDataScope(request.dataScope() == null ? DataScope.SELF : request.dataScope());
        role.setPermissions(toGrants(request.permissions()));
    }
```
Add `import in.healthconnect.security.model.DataScope;`.

- [ ] **Step 6: Seed the scopes** in `SecurityDataInitializer`:
  - `adminRole()`: add `.dataScope(DataScope.ALL)` to the builder.
  - `portalRole("DOCTOR", DOCTOR_PORTAL)` → pass scope `DEPARTMENT`; `portalRole("CUSTOMER", CUSTOMER_PORTAL)` → `SELF`. Change `portalRole` signature to a parameter object or add a `DataScope` param (≤4 params ok):
```java
    Role doctor = roleRepository.save(portalRole("DOCTOR", AppModule.DOCTOR_PORTAL, DataScope.DEPARTMENT));
    Role customer = roleRepository.save(portalRole("CUSTOMER", AppModule.CUSTOMER_PORTAL, DataScope.SELF));
    // ...
    private Role portalRole(String name, AppModule portal, DataScope scope) {
        Set<RolePermission> permissions = new LinkedHashSet<>();
        permissions.add(new RolePermission(portal, PermissionAction.VIEW));
        return Role.builder().name(name).description(name + " access")
                .dataScope(scope).permissions(permissions).build();
    }
```
  **Also remove `DASHBOARD_VIEW` from the portal roles** (delete the `DASHBOARD` permission line) so the dashboard is admin-only. Add `import in.healthconnect.security.model.DataScope;`.

- [ ] **Step 7: Run, expect PASS** — `./mvnw -o test -Dtest=RoleServiceScopeTest`.

- [ ] **Step 8: Stage**
```bash
git add src/main/java/in/healthconnect/security/dto/RoleRequest.java \
        src/main/java/in/healthconnect/security/dto/RoleResponse.java \
        src/main/java/in/healthconnect/security/service/RoleService.java \
        src/main/java/in/healthconnect/security/config/SecurityDataInitializer.java \
        src/test/java/in/healthconnect/security/RoleServiceScopeTest.java
```

---

### Task A3: Department patient-id query + `AccessScope` + `CurrentUserProvider.currentScope()`

**Files:**
- Modify: `repository/AppointmentRepository.java`
- Create: `portal/AccessScope.java`
- Modify: `portal/CurrentUserProvider.java`
- Test: `src/test/java/in/healthconnect/portal/DepartmentQueryIT.java`

**Interfaces:**
- Produces:
  - `List<Integer> AppointmentRepository.findDistinctPatientIdsByDoctorSpecialties(Collection<Integer> specialtyIds)`
  - `record AccessScope(DataScope scope, Integer patientId, Integer doctorId, List<Integer> specialtyIds)`
  - `AccessScope CurrentUserProvider.currentScope()`

- [ ] **Step 1: Failing IT** (verifies the dept query returns patients seen by any doctor sharing a specialty)
```java
package in.healthconnect.portal;

import in.healthconnect.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DepartmentQueryIT {
    @Autowired AppointmentRepository appointments;
    @Test
    void methodExistsAndRunsForEmptyInput() {
        assertThat(appointments.findDistinctPatientIdsByDoctorSpecialties(List.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./mvnw -o test -Dtest=DepartmentQueryIT`.

- [ ] **Step 3: Add the query** to `AppointmentRepository` (import `java.util.Collection`):
```java
    // Distinct patients seen by any doctor who shares one of these specialties (a "department").
    @Query("""
            SELECT DISTINCT a.patient.id
            FROM Appointment a, DoctorSpecialtyMap m
            WHERE a.doctor.id = m.doctor.id
              AND m.specialty.id IN :specialtyIds
              AND a.deleted = false
            """)
    List<Integer> findDistinctPatientIdsByDoctorSpecialties(@Param("specialtyIds") Collection<Integer> specialtyIds);
```
Note: a JPQL `IN :empty` collection is valid and returns no rows, so the empty-list test passes.

- [ ] **Step 4: Create `AccessScope`**
```java
package in.healthconnect.portal;

import in.healthconnect.security.model.DataScope;
import java.util.List;

// The logged-in user's data reach, resolved once per request.
public record AccessScope(DataScope scope, Integer patientId, Integer doctorId, List<Integer> specialtyIds) {
}
```

- [ ] **Step 5: Add `currentScope()` to `CurrentUserProvider`** (inject `DoctorSpecialtyRepository`):
```java
    private final DoctorSpecialtyRepository doctorSpecialtyRepository;

    // The caller's scope: role dataScope + their own ids + (for a doctor) their specialty ids.
    @Transactional(readOnly = true)
    public AccessScope currentScope() {
        User user = currentUser();
        List<Integer> specialtyIds = user.getDoctorId() == null ? List.of()
                : doctorSpecialtyRepository.findByDoctorId(user.getDoctorId()).stream()
                    .map(m -> m.getSpecialty().getId()).toList();
        return new AccessScope(user.getRole().getDataScope(), user.getPatientId(),
                user.getDoctorId(), specialtyIds);
    }
```
Add imports: `in.healthconnect.repository.DoctorSpecialtyRepository`, `java.util.List`.

- [ ] **Step 6: Run, expect PASS** — `./mvnw -o test -Dtest=DepartmentQueryIT`.

- [ ] **Step 7: Stage**
```bash
git add src/main/java/in/healthconnect/repository/AppointmentRepository.java \
        src/main/java/in/healthconnect/portal/AccessScope.java \
        src/main/java/in/healthconnect/portal/CurrentUserProvider.java \
        src/test/java/in/healthconnect/portal/DepartmentQueryIT.java
```

---

### Task A4: `PatientAccessPolicy` — the single row-visibility gate

**Files:**
- Create: `portal/PatientAccessPolicy.java`
- Test: `src/test/java/in/healthconnect/portal/PatientAccessPolicyTest.java`

**Interfaces:**
- Consumes: `CurrentUserProvider.currentScope()`, `AppointmentRepository.findDistinctPatientIdsByDoctorSpecialties(...)`, `AppointmentRepository.existsByDoctorIdAndPatientId(...)`.
- Produces: `List<Integer> visiblePatientIds()` (null = unrestricted), `void assertCanView(Integer)`, `void assertCanEdit(Integer)`.

- [ ] **Step 1: Failing unit test** (mock the provider + repo)
```java
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
    void allScopeViewsAndEditsAnything() {
        when(provider.currentScope()).thenReturn(new AccessScope(DataScope.ALL, null, null, List.of()));
        assertThat(policy.visiblePatientIds()).isNull();
        assertThatCode(() -> policy.assertCanEdit(123)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `./mvnw -o test -Dtest=PatientAccessPolicyTest`.

- [ ] **Step 3: Implement the policy** (each method ≤25 lines)
```java
package in.healthconnect.portal;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

// One place that decides which patient rows the caller may see or change. Audited here only.
@Component
@RequiredArgsConstructor
public class PatientAccessPolicy {

    private final CurrentUserProvider currentUser;
    private final AppointmentRepository appointments;

    // The patient ids the caller may read; null means "no restriction" (ALL scope).
    public List<Integer> visiblePatientIds() {
        AccessScope s = currentUser.currentScope();
        return switch (s.scope()) {
            case ALL -> null;
            case SELF -> s.patientId() == null ? List.of() : List.of(s.patientId());
            case DEPARTMENT -> appointments.findDistinctPatientIdsByDoctorSpecialties(s.specialtyIds());
        };
    }

    // Read guard: 404 (not 403) so out-of-scope ids can't be probed.
    public void assertCanView(Integer patientId) {
        List<Integer> visible = visiblePatientIds();
        if (visible != null && !visible.contains(patientId)) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }
    }

    // Write guard: a doctor may only write for a patient with an appointment assigned to them.
    public void assertCanEdit(Integer patientId) {
        AccessScope s = currentUser.currentScope();
        boolean allowed = switch (s.scope()) {
            case ALL -> true;
            case SELF -> patientId.equals(s.patientId());
            case DEPARTMENT -> s.doctorId() != null
                    && appointments.existsByDoctorIdAndPatientId(s.doctorId(), patientId);
        };
        if (!allowed) {
            throw new ResourceNotFoundException("Patient not found: " + patientId);
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS** — `./mvnw -o test -Dtest=PatientAccessPolicyTest`.

- [ ] **Step 5: Stage**
```bash
git add src/main/java/in/healthconnect/portal/PatientAccessPolicy.java \
        src/test/java/in/healthconnect/portal/PatientAccessPolicyTest.java
```

---

### Task A5: Doctor portal — department patient list + profile, switch read guards to the policy

**Files:**
- Modify: `portal/DoctorPortalService.java`, `portal/DoctorPortalController.java`
- Add a summaries helper: `service/PatientService.java` (reuse existing mapping)
- Test: `src/test/java/in/healthconnect/portal/DoctorPortalScopeIT.java`

**Interfaces:**
- Consumes: `PatientAccessPolicy`, `PatientService.findSummaries(Collection<Integer>)`, `PatientService.getPatientDetails(Integer)` (existing).
- Produces: `DoctorPortalService.departmentPatients()`, `DoctorPortalService.patientProfile(Integer)`; `GET /api/v1/doctor-portal/patients`, `GET /api/v1/doctor-portal/patients/{id}`.

- [ ] **Step 1: Add `findSummaries` to `PatientService`** (reuse the mapper already used by its search method; map `patientRepository.findAllById(ids)` to `PatientResponse`):
```java
    // Patient summaries for a set of ids (used by the doctor's department list).
    @Transactional(readOnly = true)
    public List<PatientResponse> findSummaries(Collection<Integer> ids) {
        return patientRepository.findAllById(ids).stream().map(PatientResponse::from).toList();
    }
```
If the existing code maps via a util rather than `PatientResponse.from`, use that same mapper. Add imports as needed.

- [ ] **Step 2: Failing IT** — seed a Cardiology doctor+patient and an Ortho-only patient, assert the Cardiology doctor's `/patients` contains the first and not the second, and a write to the Ortho patient 404s. (Use the existing IT seeding style; `@SpringBootTest`, authenticate as the doctor via the security test support already in the repo. If no MockMvc security helper exists, call `DoctorPortalService` with a stubbed `SecurityContext` as other portal ITs do.)
```java
// Pseudocode skeleton — fill with the repo's existing seeding + auth helpers:
// 1. create Specialty CARD, ORTHO
// 2. doctor D1 mapped to CARD; appointment D1–P1 (CARD patient)
// 3. doctor D2 mapped to ORTHO; appointment D2–P2 (ORTHO-only patient)
// 4. auth as user linked to D1
// assertThat(service.departmentPatients()).extracting(PatientResponse::getId).contains(P1).doesNotContain(P2);
// assertThatThrownBy(() -> service.writeConsultation(apptOfP2, req)).isInstanceOf(ResourceNotFoundException.class);
```

- [ ] **Step 3: Run, expect FAIL** — `./mvnw -o test -Dtest=DoctorPortalScopeIT`.

- [ ] **Step 4: Edit `DoctorPortalService`**
  - Inject `PatientAccessPolicy patientAccessPolicy;` and `PatientService patientService;`.
  - Replace both `assertMyPatient(patientId)` calls in `patientHistory`, `patientReports`, `patientReportFile` with `patientAccessPolicy.assertCanView(patientId)`.
  - Delete the now-unused `assertMyPatient` method.
  - **Keep** `assertMyAppointment` for `writeConsultation` (own-appointment write). Add a department-write guard is unnecessary because `assertMyAppointment` already means "assigned to me".
  - Add:
```java
    // Every patient in the doctor's department (read scope).
    @Transactional(readOnly = true)
    public List<PatientResponse> departmentPatients() {
        List<Integer> ids = patientAccessPolicy.visiblePatientIds();
        return ids == null ? List.of() : patientService.findSummaries(ids);
    }

    // One patient's profile, if the doctor may see them.
    @Transactional(readOnly = true)
    public PatientDetailResponse patientProfile(Integer patientId) {
        patientAccessPolicy.assertCanView(patientId);
        return patientService.getPatientDetails(patientId);
    }
```
Add imports for `PatientResponse`, `PatientDetailResponse`, `PatientService`.

- [ ] **Step 5: Edit `DoctorPortalController`** — add:
```java
    @GetMapping("/patients")
    public ResponseEntity<ApiResponse<List<PatientResponse>>> departmentPatients() {
        return ResponseEntity.ok(ApiResponse.success(service.departmentPatients()));
    }

    @GetMapping("/patients/{patientId}")
    public ResponseEntity<ApiResponse<PatientDetailResponse>> patientProfile(@PathVariable Integer patientId) {
        return ResponseEntity.ok(ApiResponse.success(service.patientProfile(patientId)));
    }
```
Add imports for `PatientResponse`, `PatientDetailResponse`.
**Routing note:** `/doctor-portal/patients` resolves to module `DOCTOR_PORTAL` via the existing segment convention (first segment `doctor-portal`), so no `RequiredPermissionResolver` change is needed. Verify by checking the resolver maps the `doctor-portal` segment to `DOCTOR_PORTAL`; if it maps by a different key, add the override.

- [ ] **Step 6: Run, expect PASS** — `./mvnw -o test -Dtest=DoctorPortalScopeIT`, then the full suite `./mvnw -o test`.

- [ ] **Step 7: Stage**
```bash
git add src/main/java/in/healthconnect/portal/DoctorPortalService.java \
        src/main/java/in/healthconnect/portal/DoctorPortalController.java \
        src/main/java/in/healthconnect/service/PatientService.java \
        src/test/java/in/healthconnect/portal/DoctorPortalScopeIT.java
```

---

## Phase B — Backend: one-page combined create

### Task B1: Doctor + specialties + optional login in one transaction

**Files:**
- Create: `dto/request/PortalLoginRequest.java`
- Modify: `dto/request/CreateDoctorRequest.java`, `service/DoctorService.java`
- Test: `src/test/java/in/healthconnect/service/DoctorCombinedCreateIT.java`

**Interfaces:**
- Consumes: `DoctorSpecialtyService.assignSpecialties(Integer, List<Integer>)`, `UserService.create(CreateUserRequest)`.
- Produces: `CreateDoctorRequest` gains `List<Integer> specialtyIds` and `PortalLoginRequest login`; `DoctorService.createDoctor` orchestrates all three.

- [ ] **Step 1: Create `PortalLoginRequest`**
```java
package in.healthconnect.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Optional login to create alongside a doctor/patient, on the same form.
public record PortalLoginRequest(@NotBlank String username, @NotBlank String password, @NotNull Integer roleId) {
}
```

- [ ] **Step 2: Add optional fields to `CreateDoctorRequest`** (two new properties; they stay null on update):
```java
    private java.util.List<Integer> specialtyIds;
    private in.healthconnect.dto.request.PortalLoginRequest login;
```
(Keep them unannotated/optional so existing update flow is unaffected.)

- [ ] **Step 3: Failing IT**
```java
package in.healthconnect.service;

import in.healthconnect.dto.request.*;
import in.healthconnect.dto.response.DoctorResponse;
import in.healthconnect.entity.enums.Gender;
import in.healthconnect.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class DoctorCombinedCreateIT {
    @Autowired DoctorService doctorService;
    @Autowired UserRepository users;
    // inject SpecialtyService + RoleRepository to make a specialty + fetch DOCTOR role id in setup

    @Test
    void createsDoctorSpecialtiesAndLinkedLogin() {
        // Integer specId = ...create a specialty...; Integer doctorRoleId = ...DOCTOR role id...
        CreateDoctorRequest req = new CreateDoctorRequest(/* name/email/etc */);
        // req.setSpecialtyIds(List.of(specId));
        // req.setLogin(new PortalLoginRequest("dr.smith", "Doctor@123", doctorRoleId));
        DoctorResponse created = doctorService.createDoctor(req);
        assertThat(users.findByUsername("dr.smith")).get()
            .extracting(u -> u.getDoctorId()).isEqualTo(created.getId());
    }
}
```

- [ ] **Step 4: Run, expect FAIL** — `./mvnw -o test -Dtest=DoctorCombinedCreateIT`.

- [ ] **Step 5: Orchestrate in `DoctorService.createDoctor`** (keep ≤25 lines; extract helpers). Make the method `@Transactional`; after saving the doctor, map specialties and optionally the login:
```java
    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        DoctorResponse saved = /* existing save logic returning DoctorResponse */;
        assignSpecialtiesIfAny(saved.getId(), request.getSpecialtyIds());
        createLoginIfAny(saved.getId(), request.getLogin());
        return saved;
    }

    private void assignSpecialtiesIfAny(Integer doctorId, List<Integer> specialtyIds) {
        if (specialtyIds != null && !specialtyIds.isEmpty()) {
            doctorSpecialtyService.assignSpecialties(doctorId, specialtyIds);
        }
    }

    private void createLoginIfAny(Integer doctorId, PortalLoginRequest login) {
        if (login != null) {
            userService.create(new CreateUserRequest(
                login.username(), login.password(), login.roleId(), doctorId, null, true));
        }
    }
```
Inject `DoctorSpecialtyService` and `UserService`. Add imports for `PortalLoginRequest`, `CreateUserRequest`, `List`, `Transactional`.

- [ ] **Step 6: Run, expect PASS** — `./mvnw -o test -Dtest=DoctorCombinedCreateIT`.

- [ ] **Step 7: Stage**
```bash
git add src/main/java/in/healthconnect/dto/request/PortalLoginRequest.java \
        src/main/java/in/healthconnect/dto/request/CreateDoctorRequest.java \
        src/main/java/in/healthconnect/service/DoctorService.java \
        src/test/java/in/healthconnect/service/DoctorCombinedCreateIT.java
```

---

### Task B2: Patient + optional login in one transaction

**Files:**
- Modify: `dto/request/CreatePatientRequest.java`, `service/PatientService.java`
- Test: `src/test/java/in/healthconnect/service/PatientCombinedCreateIT.java`

**Interfaces:**
- Consumes: `UserService.create(CreateUserRequest)`.
- Produces: `CreatePatientRequest` gains `PortalLoginRequest login`; `PatientService.createPatient` links `User.patientId`.

- [ ] **Step 1: Add `private PortalLoginRequest login;`** to `CreatePatientRequest` (import it).

- [ ] **Step 2: Failing IT** — mirror B1: create patient with `login` (CUSTOMER role id), assert `users.findByUsername(...).getPatientId()` equals the new patient id.

- [ ] **Step 3: Run, expect FAIL** — `./mvnw -o test -Dtest=PatientCombinedCreateIT`.

- [ ] **Step 4: Orchestrate in `PatientService.createPatient`** — make it `@Transactional`; after saving, `createLoginIfAny(saved.getId(), request.getLogin())` using the same helper shape as B1 but with `patientId`:
```java
    private void createLoginIfAny(Integer patientId, PortalLoginRequest login) {
        if (login != null) {
            userService.create(new CreateUserRequest(
                login.username(), login.password(), login.roleId(), null, patientId, true));
        }
    }
```
Inject `UserService`. (Rule of three: if a third caller appears, extract a shared `PortalLoginFactory`; two is fine to duplicate for now.)

- [ ] **Step 5: Run, expect PASS** — `./mvnw -o test -Dtest=PatientCombinedCreateIT`, then full `./mvnw -o test`.

- [ ] **Step 6: Stage**
```bash
git add src/main/java/in/healthconnect/dto/request/CreatePatientRequest.java \
        src/main/java/in/healthconnect/service/PatientService.java \
        src/test/java/in/healthconnect/service/PatientCombinedCreateIT.java
```

---

## Phase C — Frontend

FE verification each task: `npm run build` (expect `✓ built`) and `npm run lint` (expect clean), run in `D:\HMS\healtconnectfe`. The repo has no FE unit harness, so correctness is build+lint+the described manual check. Stage all changed `.jsx/.js`; never stage `package.json`/`package-lock.json`.

### Task C1: Role form — Data scope dropdown

**Files:** Modify `src/features/roles/RoleFormModal.jsx`, `src/features/roles/rolesApi.js` (if it shapes the payload).

- [ ] **Step 1:** Add a `Form.Item name="dataScope"` with an AntD `Select` of `ALL / DEPARTMENT / SELF` (label "Data scope", default `SELF`), placed above the permission matrix.
- [ ] **Step 2:** Ensure create/update payload includes `dataScope` and the edit modal initializes it from `role.dataScope`.
- [ ] **Step 3:** Build + lint. Manual: open Roles → create a role → scope persists on reopen.
- [ ] **Step 4:** Stage `git add src/features/roles/RoleFormModal.jsx src/features/roles/rolesApi.js`.

### Task C2: Doctor form — specialties multi-select + Create-login section

**Files:** Modify `src/features/doctors/DoctorFormModal.jsx`, `src/features/doctors/doctorsApi.js`; read roles via existing `rolesHooks`.

- [ ] **Step 1:** Add a `Select mode="multiple"` bound to `specialtyIds` (options from the specialties list hook already used elsewhere).
- [ ] **Step 2:** Add a `Switch`/`Checkbox` "Create login account". When on, reveal `username` (default from email via `form.setFieldsValue` on email change, editable), `password` (`Input.Password`), and a `Select` "Role" defaulted to the DOCTOR role id (from roles hook), changeable.
- [ ] **Step 3:** On submit, when the toggle is on, include `login: { username, password, roleId }` and always include `specialtyIds`; when off, omit `login`. (Edit mode: hide the login section — creation only.)
- [ ] **Step 4:** Build + lint. Manual: create a doctor with 2 specialties + login → new doctor appears, and the created user can log in and lands on the doctor menu.
- [ ] **Step 5:** Stage `git add src/features/doctors/DoctorFormModal.jsx src/features/doctors/doctorsApi.js`.

### Task C3: Patient form — Create-login section

**Files:** Modify `src/features/patients/PatientFormModal.jsx`, `src/features/patients/patientsApi.js`.

- [ ] **Step 1:** Mirror C2's login section (role defaulted to CUSTOMER role id, no specialties). Creation only.
- [ ] **Step 2:** Submit includes `login` when toggled on.
- [ ] **Step 3:** Build + lint. Manual: create a patient + login → user logs in → lands on My Health menu, sees only their own data.
- [ ] **Step 4:** Stage `git add src/features/patients/PatientFormModal.jsx src/features/patients/patientsApi.js`.

### Task C4: Menu-driven routes, grouped sidebar, admin-only dashboard, role-home redirect

**Files:** Modify `src/app/router.jsx`, `src/components/layout/AppLayout.jsx`; create `src/app/Home.jsx`; create the doctor/patient page files used below.

- [ ] **Step 1: `Home.jsx`** — role-aware redirect:
```jsx
import { Navigate } from 'react-router-dom'
import { usePermission } from '../features/auth/permissions.js'

// Send each role to its own landing page; dashboard is admin-only.
export default function Home() {
  const { canView } = usePermission()
  if (canView('DASHBOARD')) return <DashboardHome />   // render dashboard inline or Navigate to a /dashboard route
  if (canView('DOCTOR_PORTAL')) return <Navigate to="/doctor/schedule" replace />
  if (canView('CUSTOMER_PORTAL')) return <Navigate to="/me/appointments" replace />
  return <Navigate to="/login" replace />
}
```
(Keep the dashboard at `/` for admin by rendering `DashboardPage` when `canView('DASHBOARD')`, otherwise redirect.)

- [ ] **Step 2: `router.jsx`** — replace the two portal routes with the menu routes:
```jsx
// remove: customer-portal, doctor-portal single routes
{ index: true, element: <Home /> },
{ path: 'doctor/schedule', element: gated('DOCTOR_PORTAL', <DoctorSchedulePage />) },
{ path: 'doctor/patients', element: gated('DOCTOR_PORTAL', <DoctorPatientsPage />) },
{ path: 'doctor/patients/:patientId', element: gated('DOCTOR_PORTAL', <DoctorPatientDetailPage />) },
{ path: 'me/book', element: gated('CUSTOMER_PORTAL', <BookAppointmentPage />) },
{ path: 'me/appointments', element: gated('CUSTOMER_PORTAL', <MyAppointmentsPage />) },
{ path: 'me/prescriptions', element: gated('CUSTOMER_PORTAL', <MyPrescriptionsPage />) },
{ path: 'me/reports', element: gated('CUSTOMER_PORTAL', <MyReportsPage />) },
{ path: 'me/profile', element: gated('CUSTOMER_PORTAL', <MyProfilePage />) },
```

- [ ] **Step 3: `AppLayout.jsx`** — turn the flat `NAV` into grouped, role-aware items using AntD `Menu` groups. Keep the `canView(module)` filter; drop `Dashboard` for non-admins automatically (it is gated `DASHBOARD`, which doctors/patients no longer have). Group shape:
```jsx
const NAV_GROUPS = [
  { key: 'admin', label: 'Administration', items: [
    { key: '/', title: 'Dashboard', icon: <DashboardOutlined />, module: 'DASHBOARD' },
    { key: '/patients', title: 'Patients', icon: <TeamOutlined />, module: 'PATIENTS' },
    /* doctors, specialties, appointments, reports, roles, users, settings */
  ]},
  { key: 'doctor', label: 'Doctor', items: [
    { key: '/doctor/schedule', title: 'My Schedule', icon: <CalendarOutlined />, module: 'DOCTOR_PORTAL' },
    { key: '/doctor/patients', title: 'My Patients', icon: <TeamOutlined />, module: 'DOCTOR_PORTAL' },
  ]},
  { key: 'me', label: 'My Health', items: [
    { key: '/me/book', title: 'Book Appointment', icon: <CalendarOutlined />, module: 'CUSTOMER_PORTAL' },
    { key: '/me/appointments', title: 'My Appointments', icon: <SolutionOutlined />, module: 'CUSTOMER_PORTAL' },
    { key: '/me/prescriptions', title: 'My Prescriptions', icon: <MedicineBoxOutlined />, module: 'CUSTOMER_PORTAL' },
    { key: '/me/reports', title: 'My Reports', icon: <FileTextOutlined />, module: 'CUSTOMER_PORTAL' },
    { key: '/me/profile', title: 'My Profile', icon: <IdcardOutlined />, module: 'CUSTOMER_PORTAL' },
  ]},
]
```
Build `menuItems` by filtering each group's items with `canView`, dropping empty groups, and emitting AntD `{ type: 'group', label, children: [...] }`. Keep the existing `selectedKey` longest-prefix logic (it already handles nested paths).

- [ ] **Step 4:** Build + lint. Manual: log in as admin (Administration group only), doctor (Doctor group, no Dashboard), patient (My Health group only); `/` redirects each correctly.

- [ ] **Step 5:** Stage `git add src/app/router.jsx src/app/Home.jsx src/components/layout/AppLayout.jsx`.

### Task C5: Doctor pages (re-home existing tab bodies)

**Files:** Create `src/features/portal/doctor/DoctorSchedulePage.jsx`, `DoctorPatientsPage.jsx`, `DoctorPatientDetailPage.jsx`. Reuse `ScheduleTab`, `PatientHistoryTab`, `PatientReportsTab`, `ConsultationModal`, `PdfViewer`, and `doctorPortalApi/Hooks`.

- [ ] **Step 1: `DoctorSchedulePage`** — thin wrapper rendering the existing `ScheduleTab` body (same hooks/calls).
- [ ] **Step 2: `DoctorPatientsPage`** — table from new `GET /doctor-portal/patients` (add `useDepartmentPatients` to `doctorPortalApi/Hooks`); each row links to `/doctor/patients/:patientId`.
- [ ] **Step 3: `DoctorPatientDetailPage`** — header from `GET /doctor-portal/patients/:id`; sub-sections **Overview · Visits/History · Prescriptions · Reports** as an in-page segmented control or sub-tabs, bodies reusing `PatientHistoryTab`/`PatientReportsTab` scoped to `patientId`; "Write prescription" opens `ConsultationModal` for a selected visit.
- [ ] **Step 4:** Build + lint. Manual as doctor: schedule loads; My Patients lists department patients; opening one shows history/reports; can prescribe only for own appointments (others 404 → show "not found").
- [ ] **Step 5:** Stage the three new files + the hooks file.

### Task C6: Patient pages (re-home existing tab bodies)

**Files:** Create `src/features/portal/customer/BookAppointmentPage.jsx`, `MyAppointmentsPage.jsx`, `MyPrescriptionsPage.jsx`, `MyReportsPage.jsx`, `MyProfilePage.jsx`. Reuse `FindDoctorTab`, `MyAppointmentsTab`, `PrescriptionModal`, `MyReportsTab`, `PdfViewer`, `customerPortalApi/Hooks`.

- [ ] **Step 1:** Each page is a thin wrapper around the matching existing tab body: Book→`FindDoctorTab`, Appointments→`MyAppointmentsTab`, Reports→`MyReportsTab`. Prescriptions→a list reusing the consultation fetch (or `PrescriptionModal` launched from the appointments list). Profile→`GET /me/profile` if added in backend, else show the data already on `/auth/me`.
- [ ] **Step 2:** Delete the now-unused `CustomerPortalPage.jsx`/`DoctorPortalPage.jsx` tab shells (keep the inner tab components — they're reused).
- [ ] **Step 3:** Build + lint. Manual as patient: book (availability-first), see own appointments/prescriptions/reports only, never another patient's.
- [ ] **Step 4:** Stage the new files and the deletions (`git add -A` within `src/features/portal`).

---

## Self-Review

**Spec coverage:**
- Two axes / `Role.dataScope` → A1, A2. ✔
- `AccessScope` + `CurrentUserProvider` → A3. ✔
- `PatientAccessPolicy` (view/edit/visible, 404) → A4. ✔
- Department = Specialty query → A3; dept read / own write → A4, A5. ✔
- Scoping only in portal endpoints → A5 (back-office untouched; dashboard/patients modules just not granted). ✔
- Endpoints `/doctor-portal/patients[/{id}]`, read guards switched → A5. ✔
- Schema `data_scope` column → A1 (field) + verified live by A2's `@SpringBootTest` (runs against MySQL; if `validate` rejects the new column, add the one-off `ddl-auto=update` IT noted in Global Constraints before A2). ✔
- Combined create (doctor+specialties+login, patient+login, transactional, links ids, replaces PortalDemoLinker reliance) → B1, B2. ✔
- Menu routes, grouped sidebar, admin-only dashboard, role-home redirect → C4; doctor/patient pages → C5, C6. ✔
- Role-form data-scope dropdown → C1; combined-create forms → C2, C3. ✔

**Placeholder scan:** The two ITs in A5/B1 use clearly-labelled skeletons because they depend on the repo's existing seeding/auth test helpers, which the implementer must match; every production code block is complete. No "TODO/add validation/handle edge cases" left.

**Type consistency:** `DataScope` (ALL/DEPARTMENT/SELF) used identically A1→A4. `visiblePatientIds()` returns `List<Integer>` (null=unrestricted) consistently A4→A5. `PortalLoginRequest(username,password,roleId)` consistent B1→B2→C2/C3. `CreateUserRequest(username,password,roleId,doctorId,patientId,enabled)` matches the real record read from source.

**Note for executor:** if the live `ddl-auto=validate` blocks A2's `@SpringBootTest`, run a one-off `ddl-auto=update` IT (as used previously for `patient_report`) to add `role.data_scope`, then re-seed scopes, then continue.
