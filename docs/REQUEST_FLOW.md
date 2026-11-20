# HealthConnect — Request Flow (how one API call travels)

Notes for anyone learning the codebase: when a request hits `/api/v1/...`, which class
handles it at each stage, what that class decides, and the log line it prints.

---

## The two security layers (the big idea)

Security is **central** — no endpoint carries its own `@PreAuthorize`. Two questions are
answered in two central places:

1. **Permission** — "Can this *role* do this *action* on this *module*?" (e.g. `APPOINTMENTS_VIEW`)
2. **Resource** — "Can this *user* touch *this specific row*?" (patient → own, doctor → department, admin → all)

Convention: `path's first segment → MODULE`, `HTTP method → ACTION`
(`GET→VIEW, POST→CREATE, PUT/PATCH→UPDATE, DELETE→DELETE`). A few overrides handle exceptions.

---

## The flow

```
        HTTP request  (e.g.  GET /api/v1/appointments/7/detail  + Bearer token)
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1 · AUTHENTICATION            JwtAuthenticationFilter                          │
│    "Kaun ho?"  Reads the Bearer token, puts username + authorities in the      │
│    SecurityContext. Only IDENTIFIES — does not authorize.                      │
│    LOG:  [FLOW 1 · auth] GET /api/v1/appointments/7/detail -> user 'doctor1'   │
│          identified                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2 · PERMISSION (role)         PermissionAuthorizationManager                   │
│                               └─ asks → RequiredPermissionResolver             │
│    "Is role ko is module/action ki ijazat hai?"  Resolver maps path+method to  │
│    the needed authority (or AUTHENTICATED / deny); manager checks the JWT       │
│    authorities. Wired in SecurityConfig. Fail here → 403.                       │
│    LOG:  [FLOW 2 · permission] GET /api/v1/appointments/7/detail ->            │
│          needs AUTHENTICATED, granted=true                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                  │  (passed → DispatcherServlet routes to the controller)
                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3 · RESOURCE (row)            ResourceAccessInterceptor  (preHandle)           │
│                               ├─ asks → ResourceAccessResolver  (which rule?)  │
│                               └─ calls → ResourceAccessGuard → <X>ResourceGuard│
│    "Ye row tumhari hai?"  For listed routes, reads the id from the URL,         │
│    resolves the caller's AccessScope (CurrentUserProvider), and the guard       │
│    checks ownership. Not yours → ResourceNotFoundException → 404 (not 403, so   │
│    ids can't be probed). Unlisted routes skip this step.                        │
│    LOG:  [FLOW 3 · resource] GET /api/v1/appointments/7/detail ->             │
│          APPOINTMENT guard, id=7, READ                                         │
│    LOG:  [FLOW 3 · resource] READ APPOINTMENT id=7 allowed (scope=DEPARTMENT)  │
└─────────────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
        Controller  →  Service  →  Repository  →  MySQL
   (e.g. AppointmentController.detail → AppointmentService.detail → repositories)
                  │
                  ▼
        ApiResponse<...> JSON   (errors shaped by GlobalExceptionHandler)
```

---

## Class map (stage → class → file)

| Stage | Class | File | Role |
|------|-------|------|------|
| 1 · auth | `JwtAuthenticationFilter` | `security/web/JwtAuthenticationFilter.java` | Reads JWT, sets the user in the SecurityContext |
| wiring | `SecurityConfig` | `security/config/SecurityConfig.java` | Builds the filter chain, plugs in the authorization manager |
| 2 · permission | `PermissionAuthorizationManager` | `security/web/PermissionAuthorizationManager.java` | The single role/permission decision |
| 2 · permission | `RequiredPermissionResolver` | `security/web/RequiredPermissionResolver.java` | path+method → required authority (convention + overrides) |
| routing | *your* `@RestController` | e.g. `controller/AppointmentController.java` | Maps the URL to a method |
| 3 · resource | `ResourceAccessInterceptor` | `security/access/ResourceAccessInterceptor.java` | `preHandle` row-level guard entry point |
| 3 · resource | `ResourceAccessResolver` | `security/access/ResourceAccessResolver.java` | path+method → (resource type, id variable, read/write) |
| 3 · resource | `ResourceAccessGuard` | `security/access/ResourceAccessGuard.java` | Resolves scope, dispatches to the right guard |
| 3 · resource | `AppointmentResourceGuard` / `PatientResourceGuard` / `ReportResourceGuard` | `security/access/*ResourceGuard.java` | Ownership rule per resource type |
| 3 · support | `CurrentUserProvider`, `AccessScope`, `PatientAccessPolicy` | `portal/*` | Who the caller is + their data reach (SELF / DEPARTMENT / ALL) |
| wiring | `WebMvcConfig` | `config/WebMvcConfig.java` | Registers the interceptor for `/api/v1/**` |
| errors | `GlobalExceptionHandler` | `exception/GlobalExceptionHandler.java` | Turns 404 / 403 / validation into clean JSON |

---

## Seeing the logs

Every stage logs at **DEBUG**. To watch a full call's journey, enable debug for the
security package (one line in `application.properties`):

```properties
logging.level.in.healthconnect.security=DEBUG
```

Then any API call prints a trace like:

```
[FLOW 1 · auth] GET /api/v1/appointments/7/detail -> user 'doctor1' identified
[FLOW 2 · permission] GET /api/v1/appointments/7/detail -> needs AUTHENTICATED, granted=true
[FLOW 3 · resource] GET /api/v1/appointments/7/detail -> APPOINTMENT guard, id=7, READ
[FLOW 3 · resource] READ APPOINTMENT id=7 allowed (scope=DEPARTMENT)
```

A denied call shows where it stopped: `granted=false` (403) at stage 2, or the guard
throwing `Appointment not found` (404) at stage 3.

> DEBUG is off by default so production stays quiet. Turn it on only while learning/debugging.

---

## Where do I change things to add a NEW API?

| Want | Files to touch |
|------|----------------|
| Admin-only API that fits the convention | **Controller + Service** (+ Repository/DTO if needed). No security code. |
| Doctor/patient may use it, scoped to their own rows | Above **+ 1 line** in `RequiredPermissionResolver` (override → `AUTHENTICATED`) **+ 1 line** in `ResourceAccessResolver` (rule → resource type + id + READ/WRITE) |
| A brand-new kind of resource to guard | Above **+ one small `ResourceGuard` class** and a value in the `ResourceType` enum |

That is the whole surface area — the central design means most new endpoints need no
security code at all, and the rest need one or two declarative lines.
