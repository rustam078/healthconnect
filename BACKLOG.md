# HealthConnect — Backend Learning Backlog

A step-by-step backlog that turns the HealthConnect HMS into a guided tour of the
concepts a backend engineer is expected to know. **Do the tasks in order** — each
one builds on the last. Every task explains *what*, *why*, *how* (in plain
English), what "done" means, and shows a short code snippet using **modern Java /
Spring Boot**.

> New to a concept? Each task has a **"What you'll learn"** line and a **Concepts**
> list with a one-line meaning. Google the concept name + "spring boot example",
> build the smallest version that passes the acceptance criteria, then move on.

---

## How to use this file

- **Difficulty:** 🟢 Easy · 🟡 Medium · 🔴 Hard
- **One ticket = one small pull request** with a test that proves the acceptance criteria.
- Ticket ids are `HC-1 … HC-26`. Phases are the recommended build order.

## Tech choices (use these — they're current & beginner-friendly)

| Need | Use | Why |
|---|---|---|
| Language | **Java 21** (records, text blocks, virtual threads) | Modern, less boilerplate |
| Framework | **Spring Boot 3.2+** | Latest, supports virtual threads |
| DB migrations | **Flyway** | Version your schema & SQL views |
| HTML templates (email/PDF) | **Handlebars.java** or **commons-text `StringSubstitutor`** | Plain HTML + `{{placeholders}}`, **no Thymeleaf** |
| HTML → PDF | **openhtmltopdf** | Renders HTML+CSS to PDF, actively maintained |
| Excel | **Apache POI (SXSSF)** | Streams big files without running out of memory |
| Caching | **Caffeine** | Fast in-memory cache |
| Resilience | **Resilience4j** | Retry / circuit breaker for 3rd-party calls |
| Scheduling lock | **ShedLock** | Run a scheduled job once across many servers |
| Secrets | **Jasypt** | Encrypt passwords/keys stored in DB |
| Payments (test) | **Razorpay** or **Stripe** test mode | Free sandbox, fake cards, no real money |

> **Modern async tip:** In Spring Boot 3.2+ with Java 21 you can turn on virtual
> threads with `spring.threads.virtual.enabled=true`. Combined with `@Async` this
> makes background work cheap. Prefer **Java `record`** types for all DTOs.

---

# PHASE 1 — Make appointments correct & complete

The appointment is the heart of the app. Before adding email/payments, make its
rules solid.

## HC-1 · Stop double-booking (conflict & availability check) 🟡

**What & why:** Right now two patients can book the same doctor at the same time,
and an appointment can be booked outside the doctor's working hours or during a
break. We'll block that. We'll also fix a bug where `endTime` can roll past
midnight.

**Steps (flow):**
1. When booking, load the doctor's availability for that weekday and their
   existing appointments for that date.
2. Check the new time is inside working hours and **not** inside a break.
3. Check it does **not overlap** any existing appointment for that doctor.
4. Compute `endTime = startTime + durationMinutes`; reject if it crosses midnight.

**Acceptance criteria:**
- [ ] Overlapping slot for the same doctor → **409 Conflict** with a clear message.
- [ ] Outside working hours or inside a break → **400** with the reason.
- [ ] `endTime` never wraps past midnight.
- [ ] Booking a free, valid slot still works.

**Validation:** Two time ranges overlap when `A.start < B.end AND B.start < A.end`.

**Concepts:**
- *Interval overlap* — the standard formula for "do these two time ranges clash?"
- *Custom exception → HTTP status* — throw `ConflictException`, map it to 409 in the global handler.

**Code snippet:**
```java
boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
}
// endTime fix: reject if start + duration goes to the next day
LocalTime end = start.plusMinutes(duration);
if (!end.isAfter(start)) throw new BadRequestException("Appointment cannot cross midnight");
```

**What you'll learn:** turning business rules into validation, and mapping errors to the right HTTP status.

---

## HC-2 · Appointment status flow — mark COMPLETED 🟡

**What & why:** An appointment moves through states: `SCHEDULED → COMPLETED` or
`SCHEDULED → CANCELLED`. Some moves are illegal (you can't un-complete an
appointment). We put these rules in **one place** using the **State pattern**
idea, instead of `if` checks scattered around.

**Steps:**
1. Define which transitions are allowed in a single map.
2. Add `PATCH /api/v1/appointments/{id}/status` that takes the target status.
3. If the move isn't allowed → 409.

**Acceptance criteria:**
- [ ] `PATCH .../status` changes SCHEDULED → COMPLETED / CANCELLED.
- [ ] Illegal move (e.g. COMPLETED → SCHEDULED) → **409**.
- [ ] Transition rules live in one place.

**Concepts:**
- *State pattern* — an object behaves differently based on its current state; here, which next-states are legal.
- *`@PatchMapping`* — HTTP PATCH for a partial update (just the status).

**Code snippet:**
```java
static final Map<Status, Set<Status>> ALLOWED = Map.of(
    Status.SCHEDULED, EnumSet.of(Status.COMPLETED, Status.CANCELLED),
    Status.COMPLETED, EnumSet.noneOf(Status.class),
    Status.CANCELLED, EnumSet.noneOf(Status.class));

void changeStatus(Appointment a, Status target) {
    if (!ALLOWED.get(a.getStatus()).contains(target))
        throw new ConflictException("Cannot move from " + a.getStatus() + " to " + target);
    a.setStatus(target);
}
```

**What you'll learn:** modelling a small state machine cleanly.

---

## HC-3 · Cancel an appointment (with a reason) 🟢

**What & why:** Cancelling shouldn't delete the row — we keep it for history. We
store *why* and *when*, free the slot, and (later) trigger an email + calendar
removal via an event.

**Steps:**
1. Add `cancelReason` and `cancelledAt` columns.
2. Endpoint sets status = CANCELLED, fills those fields.
3. Publish `AppointmentCancelledEvent` (used in Phase 4).

**Acceptance criteria:**
- [ ] Cancel stores reason + timestamp; status becomes CANCELLED.
- [ ] The slot is bookable again.
- [ ] A cancellation event is published.

**Concepts:** *Soft state change* (keep the record, change its status) · *domain event* (announce "this happened").

**What you'll learn:** why we prefer status changes over hard deletes for audit/history.

---

## HC-4 · Reschedule safely (optimistic locking) 🔴

**What & why:** Two admins open the same appointment and both edit it. Without
protection, the second save silently overwrites the first ("lost update").
**Optimistic locking** makes the second save fail so nothing is lost.

**Steps:**
1. Add a `@Version` field to the appointment entity.
2. Reschedule endpoint changes date/time/duration and **re-runs HC-1's checks**.
3. If two saves race, the stale one throws — return 409.

**Acceptance criteria:**
- [ ] Reschedule re-validates the new slot.
- [ ] Concurrent edit → the later save gets **409** (stale version).

**Concepts:**
- *Optimistic locking* — every row has a version number; on save, JPA checks the version hasn't changed. If it has, it fails instead of overwriting.

**Code snippet:**
```java
@Version
private Long version;   // add to Appointment
// In the global handler: catch ObjectOptimisticLockingFailureException -> 409
```

**What you'll learn:** how databases + JPA prevent two users clobbering each other's changes.

---

## HC-5 · Admin comments on an appointment 🟢

**What & why:** Staff want to jot notes on an appointment ("patient called to
confirm"). This is a classic **one-to-many** relationship.

**Steps:**
1. New entity `AppointmentComment` (id, appointment, authorName, text, createdAt).
2. `POST .../{id}/comments` to add, `GET .../{id}/comments` to list (newest first, paginated).

**Acceptance criteria:**
- [ ] Add a comment to an appointment.
- [ ] List comments newest-first with pagination.
- [ ] Comment on a missing appointment → 404.

**Concepts:** *`@ManyToOne` / `@OneToMany`* (a comment belongs to one appointment; an appointment has many comments) · *`Pageable`* (page/size/sort).

**What you'll learn:** entity relationships and pagination done right.

---

# PHASE 2 — Walk-in patient at booking time

## HC-6 · Find a patient by their code 🟢

**What & why:** Reception often has the patient's card/id (the `patientCode`).
Let them book by that code — we look up the patient first.

**Acceptance criteria:**
- [ ] Booking accepts `patientCode` and finds the active patient.
- [ ] Unknown / deleted code → 404 with a clear message.
- [ ] Booking by `patientId` still works.

**Concepts:** *Derived query* (Spring builds the SQL from the method name) · *`Optional`* (a value that may or may not be present).

**Code snippet:**
```java
Optional<Patient> findByPatientCodeIgnoreCase(String code);
// service: .orElseThrow(() -> new ResourceNotFoundException("No patient with code " + code));
```

---

## HC-7 · Book + create a brand-new walk-in patient (in one transaction) 🔴

**What & why:** A new patient walks in. We shouldn't force reception to create the
patient first, then book. Let the booking request carry either a `patientId`, a
`patientCode`, **or** a small `newPatient` object. If new, we create the patient
(and generate their code) **and** the appointment **together** — if anything
fails, both roll back.

**Steps (flow):**
1. Change the request DTO (a Java `record`) to accept one of the three inputs.
2. In the service, inside **one `@Transactional` method**:
   - if `patientId`/`patientCode` → load it;
   - else look up by email/phone (reuse if the person already exists);
   - else create a new patient.
3. Then validate the slot (HC-1) and save the appointment.

**Acceptance criteria:**
- [ ] Walk-in booking creates the patient (with code) **and** appointment atomically.
- [ ] If the appointment fails, the new patient is **not** saved (rollback).
- [ ] If the email/phone already exists, reuse that patient (no duplicates).
- [ ] Response tells you whether the patient was **created** or **matched**.

**Validation:** exactly one of `{patientId, patientCode, newPatient}` — enforce with a small custom class-level validator.

**Concepts:**
- *`@Transactional` atomicity* — everything in the method commits together or not at all.
- *Find-or-create* — reuse an existing record if it matches, else insert.
- *Class-level Bean Validation* — validate a rule that spans several fields.

**Code snippet:**
```java
public record BookRequest(Integer patientId, String patientCode,
                          NewPatient newPatient,   // minimal: name, phone, gender, dob
                          Integer doctorId, LocalDate date, LocalTime start, Integer duration) {}

@Transactional
public BookResult book(BookRequest r) {
    Patient p = resolveOrCreate(r);     // reuse by code/email/phone, else insert
    validateSlot(r);                    // HC-1
    Appointment saved = appointmentRepo.save(build(r, p));
    return new BookResult(saved, p.isNew());
}
```

**What you'll learn:** transactions across two inserts, and the find-or-create pattern (a lightweight Factory).

---

# PHASE 3 — Settings (needed by email, payments, Google)

## HC-8 · A settings store with encrypted secrets 🔴

**What & why:** Email SMTP, payment keys, and Google keys shouldn't be hard-coded.
Store them in the DB behind a simple service, and **encrypt** the secret ones so
they're safe at rest.

**Steps:**
1. `Setting` entity (key, value, type, isSecret).
2. `SettingsService` **facade** with typed getters (`getInt`, `getBool`, `getSecret`).
3. Encrypt secret values with Jasypt; never return them in plain text via the API.
4. Cache settings; an update refreshes the cache.

**Acceptance criteria:**
- [ ] SMTP / payment / reminder-time editable at runtime.
- [ ] Secret values stored encrypted; API never returns them raw.
- [ ] Reads are cached; updating a setting refreshes it.

**Concepts:**
- *Facade pattern* — one simple class hides the messy details (decrypt, cache, type-convert).
- *Encryption at rest* — secrets in the DB are unreadable without the key.

**Code snippet:**
```java
int hour = settings.getInt("reminder.hour", 18);
String key = settings.getSecret("razorpay.keySecret");   // decrypts on read
```

**What you'll learn:** configuration that changes without a redeploy, and keeping secrets safe.

---

# PHASE 4 — Events + Email (HTML templates, no Thymeleaf)

## HC-9 · Domain events that fire *after commit*, on a background thread 🔴

**What & why:** Booking an appointment should also send an email and (later) create
a Google Calendar event. We **don't** want to do those inside the booking method —
if the email server is slow, the user waits; if the DB save rolls back, we must
**not** send an email. The fix: publish an **event**, and handle it **after the
transaction commits**, on a **separate thread**.

**Steps:**
1. Publish `AppointmentBookedEvent` (and Cancelled/Completed) with
   `ApplicationEventPublisher`.
2. Write listeners annotated with `@Async` +
   `@TransactionalEventListener(phase = AFTER_COMMIT)`.
3. Enable async with `@EnableAsync` and a thread pool (or virtual threads).

**Acceptance criteria:**
- [ ] Side effects run **only after** a successful commit.
- [ ] A rollback → no email, no calendar event.
- [ ] The listener runs off the web thread (check the thread name in logs).
- [ ] A failing listener does **not** break the booking.

**Concepts:**
- *Observer pattern* — publishers announce events; listeners react. They don't know about each other.
- *`AFTER_COMMIT`* — run only once the DB change is safely committed.
- *`@Async`* — run on another thread so the user isn't kept waiting.

**Code snippet:**
```java
// publish (inside the service)
publisher.publishEvent(new AppointmentBookedEvent(appointmentId));

// react (a separate @Component)
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onBooked(AppointmentBookedEvent e) {
    emailService.sendConfirmation(e.appointmentId());
}

// config
@Configuration @EnableAsync
class AsyncConfig {
    @Bean ThreadPoolTaskExecutor taskExecutor() { /* core/max pool sizes */ }
}
```

**What you'll learn:** the single most important pattern for clean side effects — event-driven design.

---

## HC-10 · HTML email templates (stored in DB) + JavaMail 🟡

**What & why:** Emails should be **HTML**, and editable **without a redeploy**.
Store each template as an HTML string with `{{placeholders}}` in the DB, fill the
placeholders with a tiny template engine (**not Thymeleaf**), and send with
JavaMail. Different email types (confirmation, reminder, cancellation) are chosen
with the **Strategy pattern**.

**Steps:**
1. `EmailTemplate` entity (code, subject, htmlBody).
2. Render with **Handlebars.java** or commons-text `StringSubstitutor`.
3. Send the rendered HTML with `JavaMailSender` + `MimeMessageHelper` (html = true).
4. SMTP config comes from Settings (HC-8).

**Acceptance criteria:**
- [ ] Templates are DB rows; `{{patientName}}`, `{{doctorName}}`, `{{time}}` get filled.
- [ ] Email arrives as HTML.
- [ ] Confirmation / reminder / cancellation each resolve their own template.
- [ ] Missing template → safe fallback + a warning log.

**Concepts:**
- *Strategy pattern* — one class per email type behind a common interface; adding a new type is one new class, no `switch`.
- *Templating* — HTML with holes you fill at runtime.

**Code snippet (latest, no Thymeleaf):**
```java
// Option A: commons-text (simplest)
String html = new StringSubstitutor(Map.of(
        "patientName", p.getFirstName(),
        "time", "10:00 AM"), "{{", "}}").replace(template.getHtmlBody());

// Option B: Handlebars.java (supports loops/conditionals)
Template t = new Handlebars().compileInline(template.getHtmlBody());
String html = t.apply(model);

// send
MimeMessage msg = mailSender.createMimeMessage();
new MimeMessageHelper(msg, true, "UTF-8").setText(html, true);   // true = HTML
mailSender.send(msg);
```

**What you'll learn:** HTML email, DB-driven content, and the Strategy pattern.

---

## HC-11 · Confirmation & cancellation emails (wire it together) 🟢

**What & why:** Connect Phase-4 pieces: booking → confirmation email;
cancellation → cancellation email. All via the events from HC-9.

**Acceptance criteria:**
- [ ] Booking sends an HTML confirmation with the appointment details.
- [ ] Cancellation sends a notice.
- [ ] Emails go out after commit, on a background thread.

**What you'll learn:** composing small pieces (events + templates + mail) into a feature.

---

# PHASE 5 — Scheduling & bulk reminders

## HC-12 · Every day at 6 PM IST, remind tomorrow's patients (bulk) 🟡

**What & why:** A daily job runs at 18:00 India time, finds **tomorrow's**
scheduled appointments, and emails each patient a reminder — **once**.

**Steps:**
1. `@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")`.
2. One query for tomorrow's SCHEDULED appointments.
3. Send reminders; set a `reminderSentAt` flag so re-runs don't double-send.
4. Keep going if one email fails (collect failures).

**Acceptance criteria:**
- [ ] Runs at 18:00 IST; only tomorrow + SCHEDULED.
- [ ] Each patient reminded exactly once (idempotent).
- [ ] Running it again the same evening sends nothing new.
- [ ] One failed email doesn't stop the batch.

**Concepts:**
- *Cron + timezone* — schedule with an explicit zone; never trust the server's clock zone.
- *Idempotency* — safe to run twice; the flag prevents duplicates.
- *Bulk processing* — handle many rows in one pass (in chunks).

**Code snippet:**
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void remindTomorrow() {
    LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
    for (Appointment a : repo.findTomorrowScheduledNotReminded(tomorrow)) {
        try { email.sendReminder(a); a.setReminderSentAt(Instant.now()); }
        catch (Exception ex) { log.warn("reminder failed for {}", a.getId(), ex); }
    }
}
```

**What you'll learn:** scheduled jobs, timezones, and writing jobs that are safe to re-run.

---

## HC-13 · Run the job once across many servers (ShedLock) 🔴

**What & why:** If you deploy 2+ copies of the app, the reminder job would run
twice. **ShedLock** makes only one instance run it.

**Acceptance criteria:**
- [ ] With N instances, the job body runs on exactly one.
- [ ] If a node dies mid-run, the lock releases.

**Concepts:** *Distributed lock* — a shared "only one at a time" flag (in the DB) across servers.

**What you'll learn:** a real production concern most tutorials skip.

---

# PHASE 6 — Caching

## HC-14 · Cache the data that's read a lot 🟡

**What & why:** Specialties and doctor availability are read constantly but change
rarely. Cache them so we don't hit the DB every time. The hard part is
**invalidation** — clearing the cache when the data changes.

**Acceptance criteria:**
- [ ] Repeated reads are served from cache (prove it via a log/metric).
- [ ] A write **evicts** the affected entry (not the whole cache).
- [ ] Entries expire after a sensible TTL.

**Concepts:** *Spring Cache abstraction* (`@Cacheable` / `@CacheEvict`) · *cache invalidation* (the famously hard part).

**Code snippet:**
```java
@Cacheable(value = "availability", key = "#doctorId")
public List<Availability> forDoctor(Integer doctorId) { ... }

@CacheEvict(value = "availability", key = "#doctorId")   // on any write
public void updateAvailability(Integer doctorId, ...) { ... }
```

**What you'll learn:** caching and — more importantly — how to invalidate it correctly.

---

# PHASE 7 — Reporting with database views

## HC-15 · Create SQL views for reporting (via migration) 🟡

**What & why:** Let the database do the heavy counting. A **view** is a saved query
you can read like a table. Create them with a Flyway migration.

**Acceptance criteria:**
- [ ] Views created through a versioned migration.
- [ ] Mapped **read-only** (no writing to a view).

**Concepts:** *SQL view* · *Flyway migration* (versioned `.sql` files) · *`@Immutable` read-only entity*.

**Code snippet:**
```sql
-- V2__reporting_views.sql
CREATE VIEW v_doctor_monthly_load AS
SELECT doctor_id,
       DATE_FORMAT(appointment_date, '%Y-%m') AS ym,
       COUNT(*)                       AS appt_count,
       COUNT(DISTINCT patient_id)     AS patients
FROM appointments
WHERE status <> 'CANCELLED'
GROUP BY doctor_id, ym;
```

**What you'll learn:** pushing aggregation into the DB and versioning schema changes.

---

## HC-16 · Dashboard summary endpoint 🟡

**What & why:** One endpoint that returns the numbers a dashboard shows: today's
appointments, counts by status, totals, top specialties — in a few queries, not
dozens.

**Acceptance criteria:**
- [ ] Returns today's count, status breakdown, patient/doctor totals, top specialties.
- [ ] Built with a handful of efficient queries.

**Concepts:** *JPA interface projections* (map query rows straight to a small read-only interface) · *aggregate queries*.

**What you'll learn:** efficient read models for dashboards.

---

## HC-17 · Monthly doctor load report 🟢

**What & why:** "How many patients did each doctor see this month?" — read straight
from the view (HC-15).

**Acceptance criteria:**
- [ ] `GET /api/v1/reports/doctors/monthly?month=YYYY-MM` returns per-doctor rows.
- [ ] Sortable by appointment count; paginated. (Export it in Phase 8.)

**What you'll learn:** reading from a view and shaping report responses.

---

# PHASE 8 — Documents (HTML → PDF, Excel, async)

## HC-18 · Async Excel export with a job status 🔴

**What & why:** Big exports take time. Don't make the user's request hang. Instead:
submit the export → get a **job id** immediately → poll until it's ready →
download.

**Steps:**
1. `POST /exports/appointments` returns **202** + `jobId`.
2. A background method (`@Async`) builds the `.xlsx` with POI's **streaming**
   workbook and marks the job READY / FAILED.
3. `GET /exports/{jobId}` returns status; `GET /exports/{jobId}/file` downloads.

**Acceptance criteria:**
- [ ] Submit returns 202 + jobId right away.
- [ ] Status goes PENDING → READY / FAILED.
- [ ] Large exports don't run out of memory (use `SXSSFWorkbook`).

**Concepts:** *`@Async` + `CompletableFuture`* · *job-status pattern* (submit → poll → download) · *streaming Excel*.

**Code snippet:**
```java
@Async
public CompletableFuture<Void> buildXlsx(String jobId, ReportQuery q) {
    try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {  // keep 100 rows in memory
        // write rows...
        store(jobId, wb); jobs.markReady(jobId);
    } catch (Exception e) { jobs.markFailed(jobId, e.getMessage()); }
    return CompletableFuture.completedFuture(null);
}
```

**What you'll learn:** the correct pattern for slow/large work behind an HTTP API.

---

## HC-19 · HTML → PDF slips & reports 🟡

**What & why:** Generate an appointment slip and the monthly report as PDF. We
**design the PDF as an HTML page** (easy to style with CSS) and convert it with
**openhtmltopdf** — the same HTML-template approach as our emails, so you reuse
what you learned in HC-10.

**Acceptance criteria:**
- [ ] Appointment slip PDF (patient, doctor, date/time, fee).
- [ ] Monthly report PDF from the view data.
- [ ] Generated on a background thread (reuse HC-18's job pattern for big ones).

**Concepts:** *HTML → PDF* (author in HTML+CSS, render to PDF) · reuse of the templating from HC-10.

**Code snippet (openhtmltopdf, no Thymeleaf):**
```java
String html = render("slip.html", model);   // same {{placeholder}} engine as emails
try (var out = new FileOutputStream(file)) {
    var b = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
    b.withHtmlContent(html, null);
    b.toStream(out);
    b.run();
}
```

**What you'll learn:** producing PDFs the easy way — by writing HTML.

---

# PHASE 9 — Payments (Razorpay / Stripe, test mode)

> **Free for developers?** Yes. **Stripe test mode** (test card `4242 4242 4242 4242`,
> any future expiry & any CVC), **Razorpay test mode** (test cards + test UPI), and
> **PayPal Sandbox** are all free — no real money moves. Since HealthConnect is
> India/INR-focused, **Razorpay** is a natural fit; **Stripe** has the best docs.
> We'll support **both** behind one interface using the **Strategy pattern**.

## HC-20 · One payment interface, many gateways (Strategy + Factory) 🟡

**What & why:** Don't hard-wire one payment provider. Define a `PaymentGateway`
interface; write `RazorpayGateway` and `StripeGateway`; pick which to use from
Settings (HC-8). Adding a third gateway later is one new class.

**Acceptance criteria:**
- [ ] A `PaymentGateway` interface with `createOrder(...)` and `verify(...)`.
- [ ] Razorpay and Stripe implementations.
- [ ] The active gateway is chosen from settings at runtime.

**Concepts:**
- *Strategy pattern* — interchangeable algorithms (gateways) behind one interface.
- *Factory* — pick the right implementation by name/config.

**Code snippet:**
```java
public interface PaymentGateway {
    String name();                                   // "razorpay" | "stripe"
    PaymentOrder createOrder(long amountPaise, String currency, String refId);
    boolean verify(PaymentCallback cb);              // signature check
}

@Component
class PaymentGatewayFactory {
    private final Map<String, PaymentGateway> byName;  // Spring injects all impls
    PaymentGateway active(String cfgName) { return byName.get(cfgName); }
}
```

**What you'll learn:** the Strategy + Factory combo — the textbook way to support pluggable providers.

---

## HC-21 · Create a payment order for the consultation fee 🟡

**What & why:** When an appointment is booked (or from a "Pay" action), create a
payment **order** for the doctor's consultation fee via the active gateway, and
store a `Payment` row (status = CREATED).

**Steps:**
1. `POST /appointments/{id}/pay` → gateway `createOrder(fee, "INR", appointmentRef)`.
2. Save a `Payment` (appointmentId, gateway, orderId, amount, status).
3. Return the order id + key so the frontend can open the checkout.

**Acceptance criteria:**
- [ ] Creates a real **test-mode** order via Razorpay/Stripe.
- [ ] A `Payment` row is saved as CREATED.
- [ ] Amount matches the doctor's consultation fee (in the smallest unit — paise/cents).

**Validation:** amount > 0; currency matches; appointment exists and isn't already paid.

**Concepts:** *money in the smallest unit* (store paise/cents as integers, never floats) · *SDK usage* (Razorpay/Stripe Java client).

**Code snippet (Razorpay test mode):**
```java
RazorpayClient client = new RazorpayClient(keyId, keySecret);   // test keys
JSONObject req = new JSONObject()
    .put("amount", feePaise)      // e.g. 50000 = ₹500.00
    .put("currency", "INR")
    .put("receipt", "appt_" + appointmentId);
Order order = client.orders.create(req);
```

**What you'll learn:** integrating a real payment SDK and modelling money correctly.

---

## HC-22 · Verify the payment + handle the webhook 🔴

**What & why:** After the user pays, the gateway calls back (and also sends a
**webhook**). We must **verify the signature** (so nobody fakes a payment), mark
the `Payment` PAID, mark the appointment paid, and publish a `PaymentReceivedEvent`
(email receipt via Phase 4). Webhooks can arrive twice → handle **idempotently**.

**Acceptance criteria:**
- [ ] Signature verified before trusting any callback/webhook.
- [ ] On success: Payment = PAID, appointment marked paid, receipt event published.
- [ ] Duplicate webhook doesn't double-process (idempotent by order/payment id).
- [ ] Failed/invalid signature → 400, nothing changes.

**Concepts:**
- *Webhook* — the provider POSTs to your server when something happens.
- *Signature verification (HMAC)* — proves the callback really came from the gateway.
- *Idempotency* — the same webhook twice = one effect.

**Code snippet (Razorpay signature check):**
```java
String expected = HmacUtils.hmacSha256Hex(keySecret, orderId + "|" + paymentId);
boolean ok = MessageDigest.isEqual(
        expected.getBytes(UTF_8), receivedSignature.getBytes(UTF_8));
if (!ok) throw new BadRequestException("Payment signature mismatch");
```

**What you'll learn:** the security-critical parts of payments — signatures, webhooks, idempotency.

---

# PHASE 10 — Google Calendar integration (3rd-party)

## HC-23 · Connect to Google from Settings 🟡

**What & why:** Configure the Google API client using a **service-account** key
stored in Settings (HC-8). A "test connection" endpoint proves it works.

**Acceptance criteria:**
- [ ] Client authenticates with the stored service account.
- [ ] Test endpoint reports success/failure clearly.

**Concepts:** *service account / OAuth2* (server-to-server auth) · *secure secret storage*.

**What you'll learn:** authenticating to a big third-party API.

---

## HC-24 · One Google Calendar per doctor 🟡

**What & why:** Give each doctor their own Google Calendar and save its
`googleCalendarId`. Wrap the Google SDK behind an **Adapter** so the rest of the
app never touches Google types directly.

**Acceptance criteria:**
- [ ] Each doctor ends up with a stored `googleCalendarId`.
- [ ] Idempotent — never creates a second calendar for the same doctor.

**Concepts:** *Adapter pattern* — translate our world ↔ Google's API · *idempotency* · *storing external ids*.

**What you'll learn:** isolating a third-party SDK behind your own clean interface.

---

## HC-25 · Sync bookings to Google Calendar (resilient) 🔴

**What & why:** Driven by the events from HC-9: booking → create a calendar event
in the doctor's calendar; cancel → delete it; reschedule → update it. Google might
be slow or down — that must **never** fail the booking. Use **retry + circuit
breaker**, store the `googleEventId`, and keep it idempotent.

**Acceptance criteria:**
- [ ] Booking creates a matching calendar event; cancel removes it.
- [ ] A Google failure is retried; the booking still succeeds (eventual consistency).
- [ ] Retries don't create duplicate events.

**Concepts:**
- *Resilience4j retry / circuit breaker* — retry transient failures; stop hammering a dead service.
- *Eventual consistency* — the calendar catches up shortly after; it isn't part of the booking transaction.

**Code snippet:**
```java
@Retry(name = "gcal")
@CircuitBreaker(name = "gcal", fallbackMethod = "queueForLater")
public String createEvent(String calendarId, AppointmentView a) { ... }
```

**What you'll learn:** calling flaky external services without letting them break your app.

---

# PHASE 11 — Bulk operations

## HC-26 · Bulk import patients from CSV/Excel 🔴

**What & why:** Upload a file of patients; validate every row; insert the good ones
in **batches**; return a report saying which rows worked and exactly why the rest
failed.

**Acceptance criteria:**
- [ ] Partial success — valid rows import even if some fail.
- [ ] Per-row errors (line number + reason).
- [ ] Inserts use **JDBC batching**; a big file doesn't blow up memory.

**Concepts:** *file parsing* (Apache POI / OpenCSV) · *JDBC batch insert* (send many inserts at once) · *chunked transactions*.

**What you'll learn:** processing data at volume, the professional way.

---

# Design patterns map

You'll apply these to real problems in this backlog — not memorise them:

| Pattern | Plain meaning | Where you use it |
|---|---|---|
| **Observer** | Announce an event; others react, decoupled | HC-9 (domain events) |
| **State** | Behaviour depends on current state | HC-2 (status transitions) |
| **Strategy** | Swap interchangeable implementations | HC-10 (email types), HC-20 (payment gateways) |
| **Factory** | Pick the right implementation at runtime | HC-7 (resolve-or-create), HC-20 (gateway) |
| **Adapter** | Wrap a third-party API in your own interface | HC-24/25 (Google) |
| **Facade** | One simple door over a messy subsystem | HC-8 (SettingsService) |
| **Template Method** | Shared skeleton, fill in the steps | HC-18/19 (document generation) |
| **Builder** | Readable object construction (already in use) | Entities & DTOs |

---

# Suggested rhythm

1. Read the ticket → understand the **acceptance criteria** first.
2. Learn just enough of the concept to build the smallest version.
3. Write a test that proves the acceptance criteria.
4. Open a small PR. Repeat.

> Tip: the frontend already has **Update / Cancel** buttons sitting disabled in the
> appointment details drawer — finishing **HC-2 / HC-3 / HC-4** lets you switch
> them on. And once **HC-21/22** (payments) are done, the booking screen can show a
> "Pay ₹fee" step.

_HealthConnect learning backlog · 11 phases · 26 tickets · build in order._
