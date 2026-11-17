# Data Scoping & Menu-Driven Portals — Design

Date: 2026-11-17
Status: Approved (chat), ready for implementation plan
Repos: `D:\HMS\healthconnect` (backend), `D:\HMS\healtconnectfe` (frontend)

## Problem

The current RBAC controls **which modules/actions** a role has, but every user with a
module sees **all rows** of it. We need row-level visibility so that:

- A **patient** sees only their own data — never another patient's.
- A **doctor** sees every patient in their **department** (read), but can only **write**
  (prescriptions/consultations) for patients actually assigned to them via an appointment.
- **Admin/staff** see everything.

We also need the portals to feel like a real product: each feature is its own **sidebar
menu item + route** (not tabs under one "Portal" page), and the **Dashboard is admin-only**.

## Core concept: two orthogonal access axes

Access is decided on two independent axes, both assigned by the admin per role:

| Axis | Question | How assigned | Enforced by |
|---|---|---|---|
| Capability (exists) | Which modules & actions? | module × action matrix | `PermissionAuthorizationManager` |
| Data scope (new) | Which rows? | one dropdown per role | `PatientAccessPolicy` |

Both are enforced **server-side**; the frontend mirrors them only for UX. No data leak,
because the server is the real gate on both axes.

### Data scope values (`Role.dataScope`)

- `ALL` — every row. Seed: ADMIN.
- `DEPARTMENT` — every patient in the user's department(s). Seed: DOCTOR.
- `SELF` — only the user's own rows. Seed: CUSTOMER.

"Department" = **Specialty** (reuse existing `DoctorSpecialtyMap`). No new entity.
A patient is "in my department" when they have at least one appointment under a doctor
who shares one of my specialties.

## Backend design

### 1. `Role.dataScope`
- New enum `DataScope { ALL, DEPARTMENT, SELF }`.
- New field on `Role` entity + `data_scope` column (not null, default `SELF` for safety).
- Exposed in `RoleRequest` / `RoleResponse`; editable in the admin Role form.
- Seed update: ADMIN=`ALL`, DOCTOR=`DEPARTMENT`, CUSTOMER=`SELF`.

### 2. `AccessScope` (per-request)
Extend `CurrentUserProvider` to expose the resolved scope of the logged-in user:
- `dataScope` (from role)
- `selfPatientId`, `selfDoctorId`
- `specialtyIds` — the doctor's specialty ids (empty for non-doctors)

### 3. `PatientAccessPolicy` — single source of truth for row visibility
One component owns the rule so there is exactly one place to audit for leaks (mirrors how
`PermissionAuthorizationManager` centralizes authorization). Methods:

- `List<Integer> visiblePatientIds()`
  - `SELF` → `[selfPatientId]`
  - `DEPARTMENT` → distinct patient ids from appointments under any doctor sharing my
    specialties
  - `ALL` → `null` sentinel meaning "no filter"
- `void assertCanView(Integer patientId)` — throws `ResourceNotFoundException` (404, not
  403) when the patient is not visible, so ids can't be probed.
- `void assertCanEdit(Integer patientId)` — doctor must have an appointment with this
  patient as the treating doctor; otherwise 404. (`ALL` always passes; `SELF` only self.)

Backed by new repository queries:
- `AppointmentRepository`:
  - `List<Integer> findDistinctPatientIdsByDoctorSpecialties(Collection<Integer> specialtyIds)`
  - reuse existing `existsByDoctorIdAndPatientId` for the edit check.

### 4. Where scoping is enforced
Row scoping lives **only in the portal endpoints**. The back-office CRUD modules
(`/api/v1/patients`, `/doctors`, `/appointments`) stay full-access and are simply **not
granted** to doctor/customer roles — so we avoid retrofitting scope into every admin query.
Doctors and patients reach clinical data exclusively through scope-enforced portal
endpoints.

### 5. Endpoints
Doctor portal (`/api/v1/doctor-portal`):
- `GET /patients` — department patient list (new; uses `visiblePatientIds()`).
- `GET /patients/{id}` — patient profile (new; `assertCanView`).
- Existing `patients/{id}/history`, `patients/{id}/reports`, reports `.../file`,
  consultation read: switch guard from "my patient only" to `assertCanView`.
- Consultation **write** (create/update prescription): keep `assertCanEdit` (own
  appointment).

Patient portal (`/api/v1/customer-portal`) — already `SELF`-scoped; no rule change.
Optionally add `GET /me/profile` and a dedicated `GET /me/prescriptions` if the menu needs
them as standalone pages (reuse existing consultation data).

### 6. Schema change
`role` table gains `data_scope VARCHAR`. Because `ddl-auto=validate`, apply the column via
the established approach (a one-off `ddl-auto=update` integration test or an explicit
`ALTER TABLE`), then re-seed scopes on existing seed roles.

### 7. Combined create: Doctor/Patient + specialties + login in one request
Admin creates everything on a single page / single transaction, minimizing navigation.

`POST /api/v1/doctors` accepts one command (parameter object, ≤4 top-level params):
- doctor fields
- `specialtyIds: List<Integer>` — mapped to `DoctorSpecialtyMap` (a doctor has many
  specialties)
- optional `login` block: `{ username, password, roleId }` — present only when the admin
  ticked "Create login".

`DoctorService.createDoctor(command)` (one `@Transactional`):
1. save `Doctor`
2. save specialty maps for each `specialtyId`
3. if `login` present → delegate to `UserService.create(...)` with `doctorId = newDoctor.id`
   and `roleId` (defaults to the DOCTOR role, admin-changeable). Reuses existing user
   creation + unique-username validation; password is hashed by the existing encoder.

Partial failure rolls back the whole thing (no orphan doctor without its login, no login
without its doctor). `POST /api/v1/patients` mirrors this with `patientId` and the CUSTOMER
role default.

This replaces the guesswork in `PortalDemoLinker`: real admin-created doctors/patients are
linked to their login **at creation**. `PortalDemoLinker` stays only as a dev convenience
for the three seed users on an empty DB.

## Frontend design

### Navigation: tabs → menu items + routes
Replace the single tabbed "Doctor Portal" / "Customer Portal" pages with real routes, and
group the sidebar by audience. Reuse existing tab bodies as page bodies (re-home, not
rewrite): `ScheduleTab`, `FindDoctorTab`, `MyAppointmentsTab`, `PrescriptionModal`,
`ConsultationModal`, `PatientHistoryTab`, `PatientReportsTab`, `PdfViewer`.

Doctor group (gated `DOCTOR_PORTAL`):
- **My Schedule** → `/doctor/schedule`
- **My Patients** → `/doctor/patients` → `/doctor/patients/:id`
  (detail sub-sections: Overview · Visits/History · Prescriptions · Reports)

My Health group (gated `CUSTOMER_PORTAL`):
- **Book Appointment** → `/me/book` (availability-first)
- **My Appointments** → `/me/appointments`
- **My Prescriptions** → `/me/prescriptions`
- **My Reports** → `/me/reports`
- **My Profile** → `/me/profile`

Administration group (gated per existing module): Dashboard, Patients, Doctors,
Specialties, Appointments, Reports, Roles, Users, Settings.

### Dashboard is admin-only
Remove `DASHBOARD` from doctor & customer seed roles. The `/` index route becomes a
**role-aware redirect**: admin→`/` dashboard, doctor→`/doctor/schedule`,
patient→`/me/appointments`. Implemented by a small `<Home>` component choosing by the
highest-priority permission the user holds.

### Menu grouping
`AppLayout` `NAV` becomes grouped, role-aware (AntD `Menu` groups/`type:'group'`), each
item still filtered by `canView(module)`.

### Role admin form
Add a **Data scope** dropdown (ALL / DEPARTMENT / SELF) to the Role create/edit form,
wired to `Role.dataScope`.

### Combined create forms (one page, fewer admin visits)
Doctor create form gains:
- **Specialties** multi-select (many-to-many) → `specialtyIds`.
- A **"Create login account"** toggle. When on, reveals: username (auto-suggested from
  email, editable), initial password (admin types it inline), and a **Role** select
  defaulted to `DOCTOR` but changeable. Submits as the `login` block of the one create
  request above.

Patient create form mirrors this (role default `CUSTOMER`, no specialties). The standalone
Users screen remains for admin-only accounts and later edits. Net effect: creating a
working doctor/patient with a portal login is **one form, one save**.

## Decisions / scope boundaries

- Department = Specialty (no new Department entity).
- Doctor visibility: department-wide **read**, own-appointment **write**.
- Portal permission stays coarse (`DOCTOR_PORTAL` / `CUSTOMER_PORTAL` light up the whole
  group); per-item portal toggles are deferred (YAGNI).
- Admin back-office modules are not scope-retrofitted; they're simply not granted to
  doctor/patient roles.

## Non-goals (deferred)
- A separate organizational Department entity.
- Fine-grained per-menu-item portal permissions.
- Patient self-registration, doctor-uploaded reports (already deferred earlier).

## Testing
- Unit: `PatientAccessPolicy` for each scope (SELF/DEPARTMENT/ALL), view vs edit, 404 on
  out-of-scope ids.
- Repository: department patient-id query returns exactly the specialty's patients.
- Integration: doctor A (Cardiology) can view but not prescribe for a Cardiology patient
  seen by doctor B; cannot view an Ortho-only patient; patient sees only self.
- Integration: combined create — Doctor + specialties + login in one call links
  `User.doctorId`; a failing login step (duplicate username) rolls back the doctor too.
- Frontend: build + lint; role-based menu renders the right groups; index redirects by role.
