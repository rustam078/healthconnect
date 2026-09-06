# JobRunr — Complete Notes (background jobs in Spring Boot)

Beginner-friendly notes on **JobRunr**, the library you reach for when plain
`@Scheduled` isn't enough. Written for the HealthConnect HMS. Pairs with
[`scheduler.md`](scheduler.md) — read that first.

---

## 1. First: what's wrong with plain `@Scheduled`?

`@Scheduled` is great for a simple, fixed, in-app timer. But it has real limits.
These **disadvantages** are exactly what JobRunr fixes:

| Disadvantage of `@Scheduled` | Why it hurts |
|---|---|
| **No persistence** — jobs live only in memory | If the app is **down at 6 PM**, that run is **lost forever** — no catch-up |
| **No built-in retries** | If sending fails, the job just fails; you must hand-code retry logic |
| **No history / visibility** | You can't *see* what ran, what's queued, or what failed — only log files |
| **Fixed-time only** | It runs on a cron. You **can't say "run THIS for patient #42 in 2 days"** from your code at request time |
| **Hard to pass data** | The method takes no arguments — you can't easily hand it "which appointment" |
| **No load sharing across servers** | Two servers → it runs **twice** (need ShedLock, and even then only one runs — the others sit idle) |
| **No dynamic / on-demand jobs** | Can't enqueue a one-off background task ("email this receipt now, off the web thread") without extra plumbing |

> In short: `@Scheduled` is a **timer**. JobRunr is a **job system** — it *remembers*,
> *retries*, *distributes*, and *shows you* your background work.

---

## 2. What is JobRunr? (plain English)

**JobRunr is a library that runs background jobs in Java and saves them in your
database.** Because every job is stored, jobs **survive restarts**, **retry
automatically** when they fail, **spread across all your servers**, and show up on a
**built-in web dashboard** where you can watch, retry, or delete them.

**Analogy:** `@Scheduled` is an alarm clock. **JobRunr is a to-do list on the fridge
that the whole family shares** — anyone free picks the next task, ticks it off when
done, and if someone drops a task it goes back on the list to try again. The list is
written down (the database), so even if everyone leaves the house and comes back,
the tasks are still there.

JobRunr is free/open-source and is the Java cousin of **Sidekiq** (Ruby) and
**Hangfire** (.NET).

---

## 3. The three kinds of jobs

This is the core of JobRunr. You write the job as a normal Java **lambda** — JobRunr
records which method + arguments to call and stores it.

**1. Fire-and-forget** — "do this in the background, ASAP" (off the web thread):
```java
jobScheduler.enqueue(() -> emailService.sendConfirmation(appointmentId));
```

**2. Scheduled (once, in the future)** — "do this at a specific moment":
```java
// remind this specific patient 24h before their appointment
jobScheduler.schedule(reminderTime, () -> emailService.sendReminder(appointmentId));
```

**3. Recurring** — "do this on a repeating schedule" (like `@Scheduled`, but persisted
+ distributed + on the dashboard):
```java
@Recurring(id = "daily-reminders", cron = "0 0 18 * * *", zoneId = "Asia/Kolkata")
@Job(name = "Send tomorrow's reminders")
public void sendReminders() { ... }
```

> The magic: **fire-and-forget** and **scheduled** are things `@Scheduled` simply
> **can't do** — they let you create background work **on demand, with data**, from
> anywhere in your code (e.g. right after a booking).

---

## 4. Key terms (glossary)

| Term | What it means (simple) |
|---|---|
| **Job** | One unit of background work — a captured method call (a lambda). |
| **Job id** | A unique UUID for each job, so you can track/cancel it. |
| **Job data** (`jobAsJson`) | The job **saved as JSON**: which class + method + arguments to run, its labels, and its state history. This is what lets *any* server pick it up later. **Pass small values / IDs, not big objects.** |
| **JobDetails** | The part of the job data describing the method + arguments to invoke. |
| **State** | Where the job is in its life: SCHEDULED, ENQUEUED, PROCESSING, SUCCEEDED, FAILED, DELETED. |
| **BackgroundJobServer** | The worker living inside your app that **polls the database**, grabs due jobs, and runs them on a pool of threads. Run the app on 3 machines → 3 servers share the work. |
| **Worker** | A thread inside a BackgroundJobServer that actually executes a job. |
| **StorageProvider** | Where jobs are stored — usually your **SQL database** (also Mongo/Redis/Elasticsearch). |
| **Dashboard** | A built-in web UI (default `http://localhost:8000`) to see enqueued / processing / succeeded / failed jobs and retry or delete them. |
| **Recurring job** | A job definition with a cron, stored so it fires reliably and only once across the cluster. |
| **Retry** | JobRunr automatically re-runs a failed job (default **10 times**, with growing back-off) before marking it FAILED. |

---

## 5. Job lifecycle (the states)

Every job moves through states — this is JobRunr's "state machine":

```
                 enqueue()                     picked up by a worker
   (created) ─────────────────►  ENQUEUED ───────────────────────►  PROCESSING
      │  schedule(futureTime)        ▲                                  │
      └──────────►  SCHEDULED ───────┘ (when its time comes)           │
                                                                        ▼
                                                        ┌──────────────────────────┐
                                                success │                          │ throws
                                                        ▼                          ▼
                                                    SUCCEEDED                   FAILED
                                                        │                          │ (auto-retry
                                                        ▼                          │  up to 10×)
                                                     DELETED  ◄────────────────────┘
                                              (kept a while, then cleaned up)
```

- **SCHEDULED** → waiting for its future time.
- **ENQUEUED** → ready to run, waiting for a free worker.
- **PROCESSING** → a worker is running it right now.
- **SUCCEEDED** → done (kept briefly for the dashboard, then DELETED).
- **FAILED** → threw an error and used up its retries; **stays** so you can inspect it
  and **requeue** from the dashboard.

**Automatic retries:** a failing job doesn't just die — JobRunr retries it (default 10
attempts, exponential back-off), which is a huge win over `@Scheduled`. You can tune
it: `@Job(retries = 3)`.

---

## 6. The database tables JobRunr creates

JobRunr **auto-creates its tables** on first startup, using your existing DataSource
(your MySQL). You don't write these — but knowing them helps you understand it:

| Table | What it holds |
|---|---|
| **`jobrunr_jobs`** | The **main table** — one row per job: its id, current **state**, timestamps, and the **`jobAsJson`** (the full job data). Everything revolves around this. |
| **`jobrunr_recurring_jobs`** | Definitions of your **recurring** (cron) jobs. |
| **`jobrunr_backgroundjobservers`** | The list of running **BackgroundJobServers** (each app instance), with heartbeats, so they coordinate and share work. |
| **`jobrunr_metadata`** | Cluster/config metadata and dashboard notices. |
| **`jobrunr_jobs_stats`** | A **view** used by the dashboard to show counts per state. |
| **`jobrunr_migrations`** | Tracks JobRunr's own schema version (so upgrades migrate the tables). |

**Zoom into `jobrunr_jobs` (the important one):**

| Column (simplified) | Meaning |
|---|---|
| `id` | The job's UUID |
| `state` | SCHEDULED / ENQUEUED / PROCESSING / SUCCEEDED / FAILED / DELETED |
| `jobAsJson` | **The "job data"** — the whole job serialized to JSON (class, method, arguments, labels, state history) |
| `jobSignature` | A text signature of the method being called (used to prevent duplicates) |
| `createdAt` / `updatedAt` | When it was created / last changed |
| `scheduledAt` | When a SCHEDULED job should run |
| `recurringJobId` | Which recurring definition created this run (if any) |

> **What is "job data" exactly?** When you write `enqueue(() -> service.send(42))`,
> JobRunr records *"call `EmailService.send(int)` with argument `42`"* and stores it as
> JSON in `jobAsJson`. Later, **any** server reads that JSON, recreates the call, and
> runs it. That's why arguments must be **serializable and small** — **pass an ID like
> `42`, never a whole `Appointment` object.**

---

## 7. Setup — dependency & config

**Dependency (Spring Boot 3):**
```xml
<dependency>
  <groupId>org.jobrunr</groupId>
  <artifactId>jobrunr-spring-boot-3-starter</artifactId>
  <version>7.3.1</version> <!-- use the latest -->
</dependency>
```

**Config (`application.properties`):**
```properties
# turn on the worker that runs jobs (inside this app)
jobrunr.background-job-server.enabled=true
# turn on the web dashboard
jobrunr.dashboard.enabled=true
jobrunr.dashboard.port=8000
# JobRunr reuses your existing DataSource (MySQL) and auto-creates its tables
```

That's it — start the app, open **http://localhost:8000**, and you'll see the
dashboard. No table scripts to run (JobRunr creates them; on a locked-down DB you can
generate the SQL yourself with `jobrunr.database.skip-create=true`).

**Using it in a service (Spring):**
```java
@Service
@RequiredArgsConstructor
public class BookingService {
    private final JobScheduler jobScheduler;   // injected by JobRunr

    public void book(...) {
        // ... save appointment ...
        jobScheduler.enqueue(() -> emailService.sendConfirmation(appointmentId)); // background
    }
}
```

---

## 8. HealthConnect examples

**a) The 6 PM reminder as a recurring job** (persisted, retried, on the dashboard,
survives restart, distributed — **no ShedLock needed**):
```java
@Component
@RequiredArgsConstructor
public class ReminderJobs {
    private final ReminderService reminderService;

    @Recurring(id = "daily-reminders", cron = "0 0 18 * * *", zoneId = "Asia/Kolkata")
    @Job(name = "Send tomorrow's reminders", retries = 3)
    public void run() {
        reminderService.sendTomorrowReminders();   // keep logic in the service
    }
}
```

**b) Fire-and-forget confirmation email right after booking** (something `@Scheduled`
can't do — a one-off background task, with data):
```java
jobScheduler.enqueue(() -> emailService.sendConfirmation(appointmentId));
```

**c) Schedule a reminder for one specific appointment, 24h before:**
```java
Instant remindAt = appointment.startInstant().minus(24, ChronoUnit.HOURS);
jobScheduler.schedule(remindAt, () -> emailService.sendReminder(appointmentId));
```

**Note:** even with JobRunr, keep jobs **idempotent** (retries can run a job again) —
mark `reminderSentAt` and skip if already sent, just like in the scheduler notes.

---

## 9. When to use which

| Situation | Use |
|---|---|
| Simple fixed recurring job, one instance, don't care if a run is missed | **`@Scheduled`** |
| Need persistence, retries, a dashboard, **on-demand** background jobs, or run on many servers | **JobRunr** |
| Very complex triggers / calendars, or already deep in the Quartz ecosystem | **Quartz** |
| Huge bulk/ETL with restart & chunking | **Spring Batch** |

**JobRunr vs Quartz (quick take):** JobRunr is simpler, lambda-based, has a dashboard,
and shines at **fire-and-forget + on-demand + recurring** with automatic retries.
Quartz is older and has more powerful/complex trigger options but more config and no
built-in dashboard.

---

## 10. Disadvantages of JobRunr (be balanced)

- Adds a **dependency** and **~6 tables** to your database to manage/back up.
- **Overkill for a single tiny daily job** — if all you need is one in-memory timer,
  `@Scheduled` is lighter.
- **Job arguments must be serializable and small** (they become JSON) — pass IDs, not
  big objects; refactor if a job needs lots of data.
- The **dashboard** should be secured/turned off in production if exposed.
- Some **advanced features** (e.g. batches) are in the paid "Pro" edition — though the
  free edition covers everything above.
- One more moving part to learn and monitor vs a plain cron.

---

## 11. Cheat sheet

- **JobRunr = background jobs saved in the DB** → persistent, auto-retried,
  distributed, with a dashboard.
- **3 job types:** `enqueue()` (now), `schedule(time, …)` (once, later),
  `@Recurring(cron)` (repeating).
- **Job data (`jobAsJson`)** = the method + arguments stored as JSON → **pass IDs, not
  objects.**
- **States:** SCHEDULED → ENQUEUED → PROCESSING → SUCCEEDED / FAILED → DELETED.
- **Tables:** `jobrunr_jobs` (the main one) + `jobrunr_recurring_jobs`,
  `jobrunr_backgroundjobservers`, `jobrunr_metadata`, `jobrunr_jobs_stats`,
  `jobrunr_migrations` — **auto-created**.
- **Setup:** add the starter, set `background-job-server.enabled` +
  `dashboard.enabled`, reuse your DataSource. Dashboard at `:8000`.
- Still keep jobs **idempotent** (retries exist).

**One line to teach:** *`@Scheduled` is an alarm clock (forgets everything on
restart). JobRunr is a shared, written-down to-do list in the database — jobs survive
restarts, retry themselves, spread across servers, and you can watch them on a
dashboard.*
