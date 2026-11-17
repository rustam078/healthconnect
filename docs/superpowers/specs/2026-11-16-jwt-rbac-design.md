# JWT Security + Role-Based Access Control — Design Spec

**Date:** 2026-11-16
**Projects:** `healthconnect` (Spring Boot 4, Java 17, MySQL) + `healtconnectfe` (React 19, antd, React Query)
**Status:** For review

---

## 1. Goal

Add login + JWT auth and a dynamic role/permission system. An admin can create roles and
tick which modules each role can VIEW/CREATE/UPDATE/DELETE. After login a user sees only the
modules and buttons their role allows. The frontend hides what a user can't use; the backend
enforces it on every request, so tampering with the UI/Redux/localStorage changes nothing.

## 2. Security principle (the one that matters)

The **signed JWT is the source of truth**. It carries the user's role + authorities, signed by
the server. Redux/localStorage on the frontend is only for showing/hiding UI. Every API request
is authorized from the token's authorities, re-checked server-side. Editing the client cannot
grant access — the API returns 403. No module's data leaks to a user who lacks its permission.

## 3. Permission model

- **Authority string** = `MODULE_ACTION`, e.g. `PATIENTS_VIEW`, `ROLES_CREATE`.
- **`AppModule`** (enum, fixed catalog — an admin cannot invent a module with no screen):
  `DASHBOARD, PATIENTS, DOCTORS, SPECIALTIES, APPOINTMENTS, SETTINGS, WIDGETS, ROLES, USERS,
  CUSTOMER_PORTAL, DOCTOR_PORTAL`. Each has a `pathSegment` (e.g. `PATIENTS` → `patients`).
- **`PermissionAction`** (enum): `VIEW, CREATE, UPDATE, DELETE`. VIEW = can see the module;
  the others gate the matching buttons + write APIs.

## 4. Backend — package `in.healthconnect.security`

### 4.1 Entities (extend `BaseEntity`)
- `User`: `username` (unique), `passwordHash`, `enabled`, `@ManyToOne Role role`,
  nullable `doctorId` / `patientId` (links a portal user to their domain row).
- `Role`: `name` (unique), `description`, `system` (true for ADMIN — cannot be deleted),
  `@ElementCollection` of `RolePermission`.
- `RolePermission` (`@Embeddable`): `AppModule module` + `PermissionAction action`
  (stored in `role_permission(role_id, module, action)`).

One role per user (permissions = that role's set). Can extend to many-to-many later.

### 4.2 Authentication — a filter that only identifies the user
- `JwtService`: `generateToken(user)`, `isValid(token)`, `getClaims(token)` using **jjwt**
  (`io.jsonwebtoken`). Claims: subject=username, `uid`, `role`, `authorities[]`. Secret +
  expiry from config (`jwt.secret`, `jwt.expiry-minutes`), not committed.
- `JwtAuthenticationFilter extends OncePerRequestFilter`: if a valid `Authorization: Bearer`
  is present, build a `UsernamePasswordAuthenticationToken` with authorities from the token and
  set the `SecurityContext`. No token / invalid → stay anonymous (authorization layer rejects).
- `AppUserDetailsService implements UserDetailsService`: loads a `User` by username for login.
- `PasswordEncoder` = BCrypt.

### 4.3 Authorization — ONE central manager (no per-endpoint annotations)
- `RequiredPermissionResolver`: `Optional<String> requiredAuthority(HttpServletRequest)`.
  - Public whitelist (`/api/v1/auth/login`) → empty (handled by `permitAll`).
  - Otherwise: `module` = first path segment after `/api/v1` → `AppModule`; `action` = HTTP
    method → `PermissionAction`; result = `MODULE_ACTION`.
  - An `OVERRIDES` map handles exceptions, e.g.:
    - `POST /widgets/{id}/data`, `POST /integration/{code}` → `WIDGETS_VIEW` (reads)
    - `POST /widgets/dry-run`, `POST /ai/generate` → `WIDGETS_CREATE`
    - `GET /auth/me` → sentinel `AUTHENTICATED` (any logged-in user; the manager grants it when
      the request has a non-anonymous authentication)
    - `GET /modules` → `ROLES_VIEW`
    - `/boards/**` → `DASHBOARD_*`
- `PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext>`:
  gets the required authority from the resolver and returns granted iff the authentication holds
  it. Unknown path/module → deny.
- `SecurityConfig`: stateless, CSRF off, `JwtAuthenticationFilter` before
  `UsernamePasswordAuthenticationFilter`, `permitAll` for `/auth/login`,
  `anyRequest().access(permissionAuthorizationManager)`. Beans: `PasswordEncoder`,
  `AuthenticationManager`, `UserDetailsService`.

Adding a normal CRUD endpoint needs **no** security code; only special cases add one override line.

### 4.4 APIs (all wrapped in `ApiResponse`, errors via `GlobalExceptionHandler`)
- `POST /api/v1/auth/login` → `{ token, user, permissions[] }`.
- `GET  /api/v1/auth/me` → current user + permissions (FE hydrates from this each load).
- `GET  /api/v1/modules` → catalog of modules + actions (for the Role screen checkbox matrix).
- `/api/v1/roles` CRUD (guarded `ROLES_*`): create/edit send `permissions:[{module,actions[]}]`.
- `/api/v1/users` CRUD (guarded `USERS_*`): create user + assign a role + set password.

### 4.5 Seed (dev)
A `CommandLineRunner` runs when no users exist: creates role `ADMIN` (system, all permissions)
+ an admin user, and starter `DOCTOR` / `CUSTOMER` roles with sensible permissions. Hibernate
`ddl-auto` creates the new tables (dev phase — existing data may be dropped).

### 4.6 401 vs 403
- Not authenticated (no/invalid token) → **401** via an `AuthenticationEntryPoint`.
- Authenticated but missing the permission → **403** via an `AccessDeniedHandler`.
Both return the `ApiResponse` error shape.

## 5. Frontend — `healtconnectfe`

### 5.1 Auth state (Redux Toolkit)
- `store/store.js` + `store/authSlice.js`: `{ token, user, permissions }`, reducers
  `loginSucceeded` / `loggedOut`, selectors. Token persisted to localStorage; **permissions are
  re-fetched from `/auth/me` on every app load** (never trusted from storage).
- `main.jsx` wraps `<App>` in the redux `<Provider>`.
- `axiosClient`: request interceptor attaches `Bearer` from the store; response interceptor on
  **401** dispatches `loggedOut` and redirects to `/login`.

### 5.2 Routing & gating
- `/login` page (outside `AppLayout`).
- `RequireAuth`: no token → redirect to `/login`.
- `RequirePermission(module)`: wraps a protected route; lacking `VIEW` → "Not authorized"
  (typing a forbidden URL does not render the screen).
- `AppLayout` `NAV` items gain a `module`; the menu is filtered by `hasPermission(module,'VIEW')`.
  Admin sees all; doctor sees doctor modules; customer sees customer modules — **one app**.
- `usePermission()` hook + `<Can module action>` component hide Create/Edit/Delete buttons.
- Header shows the logged-in user + a logout action (replaces "Guest").

### 5.3 Admin screens (new gated modules)
- `features/roles`: list + a create/edit form with a **module × action checkbox matrix** built
  from `GET /modules`.
- `features/users`: list + a form to create a user and assign a role.

### 5.4 Portals (gated placeholders this task)
- `features/portal/customer` (`CUSTOMER_PORTAL`) and `features/portal/doctor` (`DOCTOR_PORTAL`):
  permission-gated placeholder pages, fleshed out later.

## 6. Build order (phases)
1. **BE auth core:** entities + `JwtService` + `JwtAuthenticationFilter` +
   `RequiredPermissionResolver` + `PermissionAuthorizationManager` + `SecurityConfig` +
   `/auth/login` + `/auth/me` + seed ADMIN. (Lock the whole API behind auth, test-first on the
   resolver + manager.)
2. **BE admin APIs:** `/modules`, `/roles`, `/users`.
3. **FE auth:** redux + login + axios Bearer + `RequireAuth` + `RequirePermission` + menu/button
   gating + logout.
4. **FE admin screens:** roles (matrix) + users.
5. **Portals:** gated placeholder customer/doctor modules.

## 7. Testing
- Unit (test-first): `RequiredPermissionResolver` (convention + overrides + whitelist) and
  `PermissionAuthorizationManager` (grant/deny by authorities) — the security-critical logic.
- Unit: `JwtService` (round-trip generate→validate→claims; reject tampered/expired).
- Slice: role/user repositories.
- Integration: login → call a guarded endpoint with/without the authority (401/403/200).

## 8. Coding standards
Follow `CLAUDE.md`: SOLID; methods ≤25 lines; ≤4 params (param objects); reuse existing
services/`ApiResponse`/`BaseEntity`; one-line comments only on important methods; rule of three.
Match the existing healthconnect package/style (DTOs, services, controllers, Lombok).

## 9. Non-goals (this task)
Refresh tokens, password reset, email verification, OAuth social login, multi-role users,
full customer/doctor portal features. (Noted for later.)
