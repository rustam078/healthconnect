# HealthConnect — Learning Roadmap (simple → advanced)

Point-wise notes: what you can learn from this project, in the order a developer grows.
Skim the early parts you already know; focus where the ✅/🧪 tells you it's here or worth building.

**Legend:** ✅ already in this codebase (read it, trace it) · 🧪 not here yet — build it to level up.

---

## 0. Prerequisites (you likely know these — skip if so)
- Java language: OOP, classes/interfaces, collections, generics, exceptions.
- HTTP & REST basics: methods, status codes, JSON, client ↔ server.
- SQL basics: tables, joins, keys, indexes.
- Git: branch, commit, diff.

## 1. Java 8+ features ✅
- Lambdas & method references (`::`), functional interfaces.
- Streams: `map / filter / reduce / collect`, `Collectors.toMap`.
- `Optional` instead of nulls (`orElseThrow`, `map`, `ifPresent`).
- `record` for immutable DTOs (used everywhere — see `dto/`).
- Enhanced `switch` expressions (see the resource guards).
- `var`, `java.time` (LocalDate / LocalTime / Instant).

## 2. Build & tooling ✅
- Maven: `pom.xml`, dependencies, `mvnw` wrapper, `mvn test`.
- Lombok: `@Getter/@Builder/@RequiredArgsConstructor/@Slf4j` (less boilerplate).
- Project layout by feature/package.

## 3. Spring core (IoC / DI) ✅
- Beans & dependency injection; **constructor injection** (`@RequiredArgsConstructor`).
- Stereotypes: `@Component / @Service / @Repository / @Configuration`.
- Inject a `List<Interface>` to auto-collect strategies (see `ResourceAccessGuard`).

## 4. Spring Boot ✅
- Auto-configuration, starters, embedded Tomcat.
- `application.properties`, profiles, logging levels.
- `CommandLineRunner` seeders (`SecurityDataInitializer`, `LabTestSeeder`).

## 5. Spring MVC (web layer) ✅
- `@RestController`, `@GetMapping/@PostMapping/...`, `@PathVariable/@RequestParam/@RequestBody`.
- Bean validation `@Valid`.
- A generic response envelope (`ApiResponse<T>`).
- Central error handling: `@RestControllerAdvice` (`GlobalExceptionHandler`) → clean 400/403/404/409.
- `HandlerInterceptor` + `WebMvcConfigurer` (see `ResourceAccessInterceptor`, `WebMvcConfig`).

## 6. Data layer — Spring Data JPA + Hibernate ✅
- Entities & mappings: `@Entity`, `@ManyToOne`, `@JoinColumn`, `@Enumerated`, a `BaseEntity` superclass.
- Repositories: derived queries, `@Query` (JPQL), projections.
- **Pagination & sorting:** `Pageable`, `@PageableDefault`, `Page<T>`.
- **Dynamic filters:** JPA `Specification` + `JpaSpecificationExecutor` (see `AppointmentSpecifications`).
- Performance: `@EntityGraph` (avoid N+1), lazy vs eager, `spring.jpa.open-in-view=false`.
- MySQL + HikariCP connection pool (pool size, Little's Law — see `COMMUNITY_CHAT_GUIDE.md`).

## 7. Transactions ✅
- `@Transactional` (98 uses!): atomic units, `readOnly=true`, boundaries.
- `LazyInitializationException` — when it happens and why `@Transactional` fixes it.
- `@TransactionalEventListener(AFTER_COMMIT)` — act only after commit.

## 8. Security (the heart of this project) ✅
- Authentication vs authorization (two different jobs).
- **JWT** stateless auth: `JwtAuthenticationFilter`, Bearer token, `SecurityContext`, BCrypt hashing.
- Spring Security **filter chain**: `SecurityConfig`, dispatcher-type matchers.
- **Central permission** (no per-endpoint annotations): custom `AuthorizationManager`
  (`PermissionAuthorizationManager`) + convention resolver (`RequiredPermissionResolver`).
- **Resource / row-level access** (ownership): interceptor + `ResourceGuard` strategies
  (patient→SELF, doctor→DEPARTMENT, admin→ALL); 404-not-403 to stop id probing.
- Role / permission model: module × action, data scope. See `docs/REQUEST_FLOW.md`.

## 9. Real-time ✅
- Server-Sent Events (`SseEmitter`): push to the browser without polling.
- In-memory subscriber registry, presence, heartbeat via `@Scheduled`.
- Why SSE can't carry files; how attachments are handled separately.

## 10. Scheduling ✅
- `@EnableScheduling` + `@Scheduled` (fixed-rate heartbeat, cron `markMissedAppointments`).

## 11. Design & craft ✅
- SOLID, **SRP** (one class one job), **Strategy** (`ResourceGuard` impls), **Facade**
  (`ResourceAccessGuard`), **Convention over configuration**, **Parameter object**,
  **DTO / Repository** patterns, **rule of three**.
- API rules: controller→service→repository layering, DTO in/out (never expose entities),
  query params for filters, consistent errors, methods ≤25 lines / ≤4 params.

## 12. Testing ✅
- JUnit 5 + Mockito: pure unit tests for resolvers, guards, services.
- Run: `mvn test` (200+ tests). Red → green discipline.

## 13. File storage & uploads ✅
- **Multipart upload**: `MultipartFile`, `@RequestParam`/multipart endpoints, no hardcoded
  `Content-Type` (let the browser set the boundary).
- Save to **local disk** + metadata row in DB (`StoredFile`, `FileService`, `FileCategory`).
- Validation: allowed types & size per category; reupload deletes the old file.
- **Serving** by id with auth: browsers can't attach a JWT to `<img>`/`<a>`, so fetch → Blob
  (see `AuthedFile`, `PdfViewer`); files stay permission/guard protected, not public.
- Used for: profile avatars, chat attachments (image/PDF/doc), patient report PDFs.

## 14. Frontend integration (bonus) ✅
- React + Vite + AntD, `@tanstack/react-query`, Redux Toolkit.
- Axios interceptor (Bearer, 401→logout, response unwrap), authenticated blob file fetch.

---

## 🧪 Level up — build these (not in the project yet)

### A. Concurrency & async
- `@EnableAsync` + `@Async`, `CompletableFuture` (run work off the request thread).
- `ExecutorService` / `ThreadPoolTaskExecutor` — parallelism, thread pools, `Future`.
- Pitfalls: thread safety, `SecurityContext`/transaction not crossing threads.

### B. Scheduled job + email + multithreading (concrete task)
- Cron job: every night fetch **tomorrow's SCHEDULED appointments**, build a reminder list.
- Send emails via **Spring Mail** (`JavaMailSender`) + an HTML template.
- Fan the sending out across an `ExecutorService` (many mails in parallel), collect results.
- Make it safe to re-run (idempotent), log successes/failures.

### C. Durable background jobs
- **JobRunr** (or Quartz): persistent jobs, automatic retries, scheduling, a dashboard —
  vs plain `@Scheduled` (in-memory, lost on restart). When to use which.

### D. Payments (real-world integration)
- **Razorpay** test mode: create order → checkout → **verify webhook signature** (never trust the client).
- **Wallet**: balance per user, top-up, pay-from-wallet.
- **Transaction history**: an immutable ledger (append-only) of every credit/debit.
- **Refunds**: full/partial, status tracking, idempotency keys.
- Keep money operations inside `@Transactional`; never double-charge on retry.

### E. Cloud file storage — move uploads to S3
- Today files sit on **local disk** (breaks with multiple instances / container restarts).
- Put them in **AWS S3** (or MinIO / Cloudflare R2): AWS SDK v2, bucket, object keys.
- **Abstract storage** behind one interface (`StorageService` → `LocalStorage` | `S3Storage`)
  so the swap is config-only and the rest of the app doesn't change.
- **Pre-signed URLs**: let the browser upload/download straight from S3 (keeps big files off
  the server), with short expiry — still authorize who may get a URL.
- Think about: content-type, size limits, private buckets, lifecycle/cleanup, CDN in front.

### F. Scaling & ops
- **Redis**: caching, and SSE fan-out across instances via pub/sub (see community guide).
- Rate limiting, request idempotency.
- **Observability**: structured logging (the `[FLOW …]` logs), Spring Boot Actuator, metrics (Micrometer).
- **API docs**: OpenAPI / Swagger UI.
- **Packaging**: Dockerize, env-based config, a simple CI (build + test on push).

---

### Suggested path
Fundamentals → Spring MVC → JPA + transactions → **Security (2 layers)** → SSE/scheduling →
then the 🧪 tasks in order A → B → C → D → E → F. Finishing D (payments done right) is what
makes you feel years ahead — it forces concurrency, transactions, idempotency and security together.
