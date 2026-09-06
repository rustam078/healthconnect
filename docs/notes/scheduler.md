# Scheduler — Complete Notes (Spring Boot)

Beginner-friendly notes on **schedulers** in Spring Boot, with runnable
`System.out.println` examples for every common problem. Written while building the
HealthConnect HMS (the daily patient-reminder job).

---

## 1. What is a scheduler?

Normally backend code runs **because someone calls it** — a user hits an API, the
controller runs, a response goes back. That's **request-driven**.

A **scheduler** runs code **automatically at set times, with nobody clicking
anything**. Think of it as an **alarm clock for your code**: "every day at 6 PM,
run this method."

**Everyday examples**
- The bank emails your statement on the 1st of every month.
- Netflix charges your card on your billing date.
- A backup runs every night at 2 AM.

**In HealthConnect**
- Every day at 6 PM, email tomorrow's patients a reminder.
- Every night, delete temp export files older than 24h.
- Every Monday, generate last week's report.
- Every 10 minutes, retry failed calendar syncs.

A task that runs on a schedule is called a **job** / **scheduled task**.

---

## 2. Two ways to describe "when"

**A. Interval-based** — "every N seconds/minutes" (repeat on a gap).
> e.g. every 30 minutes, every 10 seconds.

**B. Calendar-based (cron)** — "at this clock time / date" (fire at exact moments).
> e.g. at 6:00 PM daily, at 9 AM on the 1st, every Monday.

Spring supports both.

---

## 3. Spring Boot setup

**Step 1 — turn it on** (once, on the main class or a `@Configuration`):
```java
@SpringBootApplication
@EnableScheduling
public class HealthConnectApplication { }
```

**Step 2 — mark a method** with `@Scheduled`. The method must be **void**, take
**no parameters**, and live in a Spring bean (`@Component` / `@Service`):
```java
@Component
public class ReminderJob {

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
    public void sendReminders() {
        // runs every day at 6:00 PM IST
    }
}
```

A tiny helper used in the examples below — prints the **time** and **thread**:
```java
static void log(String msg) {
    System.out.println(java.time.LocalTime.now().withNano(0)
        + " [" + Thread.currentThread().getName() + "] " + msg);
}
```

### Dependencies (what to add to `pom.xml`)

- **Basic scheduling** (`@Scheduled`, `@EnableScheduling`, cron, `fixedRate`/`fixedDelay`,
  thread pool) → **nothing extra**. It's part of Spring core, already pulled in by any
  starter such as `spring-boot-starter-web`. ✅ No pom change.

- **ShedLock** — only if you run **multiple instances** (Problem 6):
  ```xml
  <dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.16.0</version> <!-- use the latest -->
  </dependency>
  <dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>5.16.0</version>
  </dependency>
  ```
  Then enable it and create the lock table:
  ```java
  @Configuration
  @EnableScheduling
  @EnableSchedulerLock(defaultLockAtMostFor = "5m")
  class SchedulerConfig {
      @Bean
      LockProvider lockProvider(DataSource ds) {
          return new JdbcTemplateLockProvider(ds);
      }
  }
  ```
  ```sql
  CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
  );
  ```

- **Quartz** — only if you need **persistent / clustered** jobs (the level-up):
  ```xml
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
  </dependency>
  ```
  (No version tag — Spring Boot manages it.)

> The reminder job also *sends email*, which needs `spring-boot-starter-mail` — but
> that's the email feature, not the scheduler.

---

## 4. Interval options

| Attribute | Meaning | Example |
|---|---|---|
| `fixedRate` | New run **every N ms**, measured from the **start** of the previous run | `@Scheduled(fixedRate = 60000)` → every 60s |
| `fixedDelay` | Wait N ms **after the previous run finishes**, then run again | `@Scheduled(fixedDelay = 60000)` → 60s gap between runs |
| `initialDelay` | Wait this long before the **first** run | `@Scheduled(fixedRate = 60000, initialDelay = 10000)` |

**fixedRate vs fixedDelay (the key difference):**
```
fixedRate = 10s, one run takes 4s:
| run(4s) | idle 6s | run(4s) | idle 6s |   ← runs START exactly every 10s

fixedDelay = 10s, one run takes 4s:
| run(4s) | idle 10s | run(4s) | idle 10s |  ← 10s gap AFTER each finishes
```
- **`fixedDelay`** → jobs that must **not overlap** (cleanup, polling).
- **`fixedRate`** → steady cadence regardless of run time (needs enough threads).

Nicer forms:
```java
@Scheduled(fixedDelayString = "PT30S")            // ISO-8601 duration: 30 seconds
@Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
```

---

## 5. Cron expressions (calendar-based)

**Spring cron has 6 fields** (Linux cron has 5 — Spring adds **seconds** first):
```
┌──────────── second (0–59)
│ ┌────────── minute (0–59)
│ │ ┌──────── hour   (0–23)
│ │ │ ┌────── day of month (1–31)
│ │ │ │ ┌──── month (1–12 or JAN–DEC)
│ │ │ │ │ ┌── day of week (0–7 or MON–SUN; 0 & 7 = Sunday)
│ │ │ │ │ │
0 0 18 * * *
```

**Special characters:** `*` every · `,` list (`MON,WED,FRI`) · `-` range (`9-17`) ·
`/` step (`*/15`) · `?` no-specific-value · `L` last.

**Common patterns:**

| Cron | Meaning |
|---|---|
| `0 0 18 * * *` | Every day at 6:00 PM |
| `0 0 9 * * MON-FRI` | 9 AM on weekdays |
| `0 */15 * * * *` | Every 15 minutes |
| `0 0 0 1 * *` | Midnight on the 1st of every month |
| `0 0 2 * * *` | 2 AM every day |
| `0 30 8 * * MON` | 8:30 AM every Monday |
| `0 0 0 L * *` | Midnight on the last day of the month |

Macros also exist: `@daily`, `@hourly`, `@monthly`, `@midnight`, `@yearly`.

**Timezone — don't skip it.** Cron uses the **server's** timezone unless you set
`zone`. Always set it for business-time jobs:
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
```
Tip — keep the cron in properties so you can change it without recompiling:
```java
@Scheduled(cron = "${reminder.cron:0 0 18 * * *}", zone = "${app.zone:Asia/Kolkata}")
```

---

## 6. Threading + `@Scheduled` vs `@Async`

**Gotcha:** by default Spring runs **all** scheduled tasks on **one** thread, so a
slow job blocks the others (see Problem 1).

- `@Scheduled` decides **WHEN** a method runs (the timer).
- `@Async` runs a method on **a background thread** so the caller isn't blocked.
- They're independent. A scheduled job may call `@Async` helpers, or you simply
  size the scheduler's thread pool.

---

## 7. Problems & Solutions (with `System.out.println`)

Drop any of these into a `@Component` (with `@EnableScheduling` on) and run.

### Problem 1 — One slow job freezes the others (single-thread default)

**❌ Problem**
```java
@Scheduled(fixedRate = 2000)
public void slowJob() {
    log("slowJob START");
    try { Thread.sleep(5000); } catch (InterruptedException e) {}   // takes 5s
    log("slowJob END");
}

@Scheduled(fixedRate = 2000)
public void quickJob() {
    log("quickJob ran");   // wants to run every 2s
}
```
```
10:00:00 [scheduling-1] slowJob START
10:00:05 [scheduling-1] slowJob END
10:00:05 [scheduling-1] quickJob ran      <-- 5s late, only ONE thread
```

**✅ Solution — give the scheduler a thread pool**
```properties
# application.properties
spring.task.scheduling.pool.size=5
```
```
10:00:00 [sched-1] slowJob START
10:00:02 [sched-2] quickJob ran           <-- runs on time, different thread
10:00:05 [sched-1] slowJob END
```
**Takeaway:** more than one scheduled job → set a pool size.

---

### Problem 2 — `fixedRate` piles up when a run is slow

**❌ Problem**
```java
@Scheduled(fixedRate = 1000)   // every 1s
public void job() {
    log("START");
    try { Thread.sleep(3000); } catch (InterruptedException e) {}  // takes 3s
    log("END");
}
```
```
10:00:00 [sched-1] START
10:00:03 [sched-1] END
10:00:03 [sched-2] START   <-- runs "due" during the 3s overlap/queue
```

**✅ Solution — use `fixedDelay` (waits until finished)**
```java
@Scheduled(fixedDelay = 1000)   // 1s gap AFTER each finishes
public void job() { /* ... */ }
```
```
10:00:00 [sched-1] START
10:00:03 [sched-1] END
10:00:04 [sched-1] START        <-- clean 1s gap, never overlaps
```
**Takeaway:** non-overlapping work → `fixedDelay`. Steady cadence → `fixedRate` (+ threads).

---

### Problem 3 — One bad record kills the whole batch

**❌ Problem**
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void sendReminders() {
    for (String patient : List.of("Aarav", "BAD", "Meera")) {
        if (patient.equals("BAD")) throw new RuntimeException("email server down");
        log("Reminder sent to " + patient);
    }
}
```
```
18:00:00 [sched-1] Reminder sent to Aarav
Exception in scheduled task ... email server down
// Meera NEVER got her reminder
```

**✅ Solution — catch per item, keep going**
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void sendReminders() {
    for (String patient : List.of("Aarav", "BAD", "Meera")) {
        try {
            if (patient.equals("BAD")) throw new RuntimeException("email server down");
            log("Reminder sent to " + patient);
        } catch (Exception ex) {
            log("FAILED for " + patient + ": " + ex.getMessage());
        }
    }
}
```
```
18:00:00 [sched-1] Reminder sent to Aarav
18:00:00 [sched-1] FAILED for BAD: email server down
18:00:00 [sched-1] Reminder sent to Meera   <-- Meera still gets hers
```
**Takeaway:** in a bulk job, wrap each item in try/catch.

---

### Problem 4 — The same job processes the same data twice (idempotency)

**Clear up the confusion first:** restarting the app does **not** replay a past
job — a plain `@Scheduled` has no "catch-up"; on start it just waits for the next
scheduled time. So restart is *not* the cause.

**The real reasons a job touches the same data twice:**
1. **A frequent job** — e.g. reminders run **every 30 min** (to catch new same-day
   bookings), so it processes an overlapping list many times a day.
2. **A crash + manual re-run** — it emailed 3 of 5, errored; an admin re-runs it →
   the first 3 get a **second** email.
3. **A retry** after failure re-processes done rows.
4. **Two servers** (see Problem 6) both run it.

**Idempotent** = "no matter how many times it runs over the same data, the real
effect (the email) happens **once**."

**❌ Problem — no memory of who was done** (runs every 30 min; 5s here so you can watch)
```java
@Scheduled(fixedRate = 5000)
public void sendReminders() {
    for (String p : List.of("Aarav", "Meera")) {   // tomorrow's patients
        log("Emailing " + p);
    }
}
```
```
10:00:00 Emailing Aarav
10:00:00 Emailing Meera
10:00:05 Emailing Aarav    <-- same people, AGAIN → spam
10:00:05 Emailing Meera
```

**✅ Solution — mark each as done and skip it**
```java
static Set<String> reminded = new HashSet<>();   // real app: reminderSentAt column in DB

@Scheduled(fixedRate = 5000)
public void sendReminders() {
    for (String p : List.of("Aarav", "Meera")) {
        if (reminded.contains(p)) { log("Skip " + p + " (already reminded)"); continue; }
        log("Emailing " + p);
        reminded.add(p);
    }
}
```
```
10:00:00 Emailing Aarav
10:00:00 Emailing Meera
10:00:05 Skip Aarav (already reminded)
10:00:05 Skip Meera (already reminded)   <-- runs many times, emails once ✅
```
**In the real app** don't use an in-memory `Set` (it empties on restart) — use a
**DB column** like `reminderSentAt`, and only fetch rows `WHERE reminderSentAt IS
NULL`, so already-reminded ones are never even loaded.

**Takeaway:** jobs *do* run more than once (frequent schedules, retries, crashes,
two servers) — record what's already done, in the database, and skip it.

---

### Problem 5 — Job fires at the wrong hour (timezone)

**❌ Problem**
```java
@Scheduled(cron = "0 0 18 * * *")   // no zone!
public void reminders() {
    log("Running 6 PM reminder");    // on a UTC server this is 11:30 PM IST
}
```

**✅ Solution — always set `zone`**
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void reminders() { log("Running 6 PM IST reminder"); }
```
**Takeaway:** any "at X o'clock" job needs an explicit `zone`.

---

### Problem 6 — Two servers = the job runs twice (multiple instances)

**❌ Problem** — deploy 2 copies for reliability; both fire the 6 PM job → patients
get 2 emails.
```
[instance-A] 18:00:00 Emailing all patients...
[instance-B] 18:00:00 Emailing all patients...   <-- duplicate run
```

**✅ Solution — a distributed lock** so only one instance runs it. **ShedLock** uses
your DB: the first instance grabs the lock, the second skips.
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
@SchedulerLock(name = "dailyReminder", lockAtMostFor = "5m")
public void reminders() { log("Only ONE instance prints this"); }
```
```
[instance-A] 18:00:00 Only ONE instance prints this
[instance-B]           (skipped — lock held by A)
```
> **Note — does the scheduler use a DB?** The `@Scheduled` timer itself is
> in-memory and uses **no** database. ShedLock is the exception: it stores the
> lock in one small **`shedlock`** table so the instances can agree on who runs.
> That table is only for the lock, not for the scheduling.

**Takeaway:** running more than one copy of the app → lock cluster-wide jobs.

---

### Problem 7 — You can't test a job that only runs at 6 PM

**❌ Problem** — logic lives inside the `@Scheduled` method, so a test would have to
wait for 6 PM.
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void reminders() {
    // ... 40 lines of logic ...   // a test can't trigger this
}
```

**✅ Solution — keep the scheduled method thin; put logic in a service**
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
public void trigger() {
    reminderService.sendTomorrowReminders();   // thin wrapper
}
// test: call reminderService.sendTomorrowReminders() directly — no waiting
```
**Takeaway:** `@Scheduled` method = "when"; a plain service method = "what". Separate
them so the logic is testable.

---

## 8. HealthConnect example (the 6 PM reminder)

Everything above, applied: 6 PM IST, one query for tomorrow's scheduled
appointments, **bulk** send, **idempotent**, **per-item** error handling.

```java
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final AppointmentRepository repo;
    private final EmailService email;

    // Thin trigger — real work is in a testable service method
    @Scheduled(cron = "${reminder.cron:0 0 18 * * *}", zone = "Asia/Kolkata")
    public void trigger() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        int sent = 0;
        for (Appointment a : repo.findTomorrowScheduledNotReminded(tomorrow)) {
            try {
                email.sendReminder(a);
                a.setReminderSentAt(Instant.now());   // idempotency
                sent++;
            } catch (Exception ex) {
                System.out.println("Reminder failed for appt " + a.getId() + ": " + ex.getMessage());
            }
        }
        System.out.println("Reminder job done: " + sent + " sent");
    }
}
```
```java
@Query("""
   SELECT a FROM Appointment a
   WHERE a.appointmentDate = :date
     AND a.status = 'SCHEDULED'
     AND a.reminderSentAt IS NULL
""")
List<Appointment> findTomorrowScheduledNotReminded(LocalDate date);
```
Then add **ShedLock** (Problem 6) so it stays "once" across multiple instances.

---

## 9. When `@Scheduled` isn't enough → level up

| Need | Tool |
|---|---|
| Jobs that **survive restart**, misfire handling, persistence, clustering | **Quartz Scheduler** |
| **Dynamic** schedules created/changed at runtime | Spring `TaskScheduler` API / Quartz |
| Heavy **bulk/ETL** with restart + chunking | **Spring Batch** (often triggered by a scheduler) |
| Run once at an exact future moment ("in 2 days") | Delayed message queue (RabbitMQ/SQS) or a `scheduled_tasks` table polled each minute |
| Cluster-wide "run once" | ShedLock, Quartz cluster, or a cloud cron (e.g. Kubernetes CronJob) |

Dynamic scheduling example (cron from settings):
```java
taskScheduler.schedule(
    () -> reminderService.sendTomorrowReminders(),
    new CronTrigger(settings.get("reminder.cron"), ZoneId.of("Asia/Kolkata")));
```

---

## 10. Cheat sheet

- Turn on: `@EnableScheduling`. Mark methods: `@Scheduled` (void, no args, in a bean).
- **Interval:** `fixedDelay` (gap after finish, non-overlapping) · `fixedRate` (steady) · `initialDelay` (first-run wait).
- **Calendar:** `cron = "sec min hour dom month dow"` — Spring has **6 fields**. Always set `zone`.
- Default is **one thread** → set `spring.task.scheduling.pool.size`.
- Always: **handle exceptions per item**, be **idempotent**, keep the method **thin** (call a testable service), and add a **lock** if you run multiple instances.
- Outgrow it → **Quartz** (persistence/cluster), **Spring Batch** (bulk), or a queue for one-off future events.

**One line to teach:** *A scheduler is an alarm clock for your code. In Spring:
`@EnableScheduling` + `@Scheduled(cron=…, zone=…)`. Then remember five things —
enough threads, catch errors per item, be safe to run twice (idempotent), set the
timezone, and lock it if you run two servers.*
