# Patient & Doctor Portals + Reports — Design Spec

**Date:** 2026-11-17 · **Projects:** healthconnect (BE) + healtconnectfe (FE)
**Builds on:** the RBAC/JWT feature (roles, permissions, `CUSTOMER_PORTAL` / `DOCTOR_PORTAL` modules).

## Principles

1. **Availability-first booking.** A patient picks a **date + specialty**; we return only
   doctors who have a free slot that day (each with its open slots). No dead-end picks.
2. **Data scoped to the logged-in user.** Portal endpoints derive the patient/doctor id from
   the JWT user (`User.patientId` / `User.doctorId`) — **never from the URL**. A patient can't
   read another patient's data by changing an id; the backend scopes every query to *you*.
3. **Reports stored locally, metadata in DB, previewed inline.** Admin uploads a PDF for a
   patient; the file is saved on disk under a **unique** name (UUID) so same display names never
   collide; metadata lives in the DB; the exact file is fetched by id and **previewed inline**
   (no forced download).

## Backend (`in.healthconnect.portal` + `in.healthconnect.report`)

### Shared
- `CurrentUserProvider` — loads the `User` for the current `SecurityContext` username; exposes
  `requirePatientId()` / `requireDoctorId()` (400 if the account isn't linked).
- Add `AppModule.REPORTS` (path `reports`). Admin manages reports; portals view their own.

### Reports — `in.healthconnect.report`
- `PatientReport` (extends BaseEntity): `patientId`, `title`, `originalFileName`,
  `storedFileName` (unique on disk), `contentType`, `fileSizeBytes`, `uploadedByUserId`.
- `ReportStorageService` — saves bytes to `app.reports.dir` (default `uploads/reports`) as
  `{uuid}.pdf`; loads bytes by stored name. Pure path/IO, unit-tested for uniqueness.
- `ReportService` — `upload(patientId, title, MultipartFile)` (PDF only), `listByPatient`,
  `getFile(id)` (bytes + meta). Ownership checks live here.
- `ReportController` (`/api/v1/reports`, admin, `REPORTS_*`): `POST` (multipart), `GET ?patientId`,
  `GET /{id}/file` (Content-Disposition **inline**).

### Patient portal — `/api/v1/customer-portal` (`CUSTOMER_PORTAL_*`)
- `GET /available-doctors?specialtyId&date` → `[{ doctor, slots:[HH:mm] }]` (availability-first).
- `POST /appointments` `{ doctorId, date, startTime }` → books for **my** patientId (reuses
  `AppointmentService.create`, duration = slot size).
- `GET /appointments` → my appointments (reuses `findByPatientId…`). `DELETE /appointments/{id}`
  (cancel own — ownership checked).
- `GET /appointments/{id}/consultation` → my prescription/consultation (ownership checked);
  `GET /appointments/{id}/consultation/pdf` → inline PDF (reuses `ConsultationPdfService`).
- `GET /reports` → my reports; `GET /reports/{id}/file` → inline (scoped to me).

### Doctor portal — `/api/v1/doctor-portal` (`DOCTOR_PORTAL_*`)
- `GET /appointments?date` → my schedule (reuses `findByDoctorIdAndAppointmentDate`).
- `GET /patients/{patientId}/history` → that patient's past consultations — **only if** the
  patient has an appointment with me (ownership check).
- `POST /appointments/{appointmentId}/consultation` → write prescription (reuses
  `ConsultationService`; the appointment must be mine); `GET .../pdf` inline.
- `GET /patients/{patientId}/reports` + `/{id}/file` inline (scoped to my patients).

### Availability slot logic — `SlotService`
For a doctor + date: take their `DoctorAvailability` for `date.dayOfWeek`; step from start→end by
`SLOT_MINUTES` (default 30), skipping the break window; drop slots overlapping a non-cancelled
appointment (`findByDoctorIdAndAppointmentDate`); drop past times when the date is today. Pure
function, unit-tested.

### New DTOs (reuse the rest)
`AvailableDoctorResponse(DoctorResponse doctor, List<LocalTime> slots)`,
`BookAppointmentRequest(Integer doctorId, LocalDate date, LocalTime startTime)`,
`ReportResponse(id, patientId, title, originalFileName, contentType, fileSizeBytes, createdAt)`.

### Demo linker (dev)
`PortalDemoLinker` (CommandLineRunner, after the security seed): if the seeded `doctor` / `patient`
users are unlinked and a Doctor / Patient row exists, link them to the first one — so the portals
show data immediately. Real linking is done via the admin Users screen (`doctorId`/`patientId`).

## Frontend (`healtconnectfe`)
- Shared `PdfViewer` component: fetches the PDF via axios (Bearer) as a blob, makes an object URL,
  shows it in `<embed>` — inline **preview** (view), with an optional download link.
- **Customer portal** page → internal tabs: Find a Doctor (date+specialty → available doctors →
  slot → book), My Appointments, My Prescriptions (view PDF), My Reports (preview).
- **Doctor portal** page → tabs: My Schedule (today/by date), Patient History, Write Prescription
  (view PDF), (patient) Reports.
- **Admin Reports** screen (`REPORTS` module, new route + NAV): pick patient, upload PDF, list,
  preview inline.
- Portals reuse their existing routes/placeholder pages (just fill the content); only the admin
  Reports screen adds a route + NAV item.

## Security
Path-convention authorization already covers `/customer-portal/**`, `/doctor-portal/**`,
`/reports/**`. **Ownership** (report/appointment belongs to the current user) is enforced in the
services — module permission alone is not enough.

## Testing
Unit (test-first): `SlotService` (slot generation, break, booked-exclusion, past-time),
`ReportStorageService` (unique names, round-trip), report/appointment ownership scoping.

## Non-goals (later)
Public patient self-registration, doctor-uploaded reports, report categories, virtual/video visits.
