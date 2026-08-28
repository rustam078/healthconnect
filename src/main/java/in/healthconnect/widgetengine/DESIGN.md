# WidgetEngine — Design Spec

**Date:** 2026-08-27
**Project:** HealthConnect (HMS) — Spring Boot 4.0.7, Java 17, MySQL, monolithic
**Author:** rustam@kodejams.com (with Claude Code)
**Status:** Approved for planning

---

## 1. Purpose

Add a **data-driven widget engine** to HealthConnect. Instead of hand-coding an
endpoint + query + DTO for every dashboard tile or internal data API, we store a
**SQL query template + display metadata as a row in a table**. One generic engine
loads the row, safely injects filter values and pagination, executes the query,
and returns generic rows (`column -> value`). The frontend renders the result
based on the widget's `type`.

Adding a new dashboard tile or internal data API becomes: **insert one row**. No
redeploy, no new Java class.

This is a learning-oriented, enterprise-minded implementation modeled on an
existing production system, adapted to MySQL and to HealthConnect's conventions.

## 2. Goals & Non-Goals

### Goals
- Store widgets (SQL template + metadata) with full CRUD.
- Execute a widget's query with **operator-based, optional filters**, sorting, and
  pagination, returning generic rows.
- Support three **modules** that change behavior: `WIDGET`, `INTEGRATION`, `PROMPT`.
- Be **safe by default**: bound parameters for values, SELECT-only execution,
  whitelisted operators/sort columns, hard row-limit cap, masked SQL errors.
- Record an **execution audit log** for every run.
- Keep everything inside **one package** (`in.healthconnect.widgetengine`) for
  ease of learning, but organized by responsibility (one class = one job).

### Non-Goals (this iteration)
- No Gemini/AI wiring. The `PROMPT` module is stubbed with a clear injection point.
- No DB-driven filter dropdowns (a widget's filter options are not themselves
  sourced from SQL). Filter metadata is descriptive only.
- No result caching, no query timeout, no response column metadata. These are
  documented as **future enhancements** (Section 11).
- No multi-tenant / portfolio scoping (HealthConnect is single-tenant).
- No frontend. We expose JSON APIs; UI rendering is out of scope.

## 3. Modules

The `module` field on a widget selects behavior. All three share the same engine.

| Module | Meaning | This iteration |
| --- | --- | --- |
| `WIDGET` | Dashboard tile. Listed as **suggestions** when a user builds a new board (frontend lists widgets where `module = WIDGET`). | Fully built |
| `INTEGRATION` | A stored query exposed as a **dynamic JSON API**, called by `code`. A "replacement of an API" — no bespoke controller needed. | Fully built |
| `PROMPT` | Natural language -> NVIDIA NIM generates MySQL -> engine runs it. | **Built**: `QueryGenerator` interface + `NimQueryGenerator` (credentials live in the `app_setting` table). Output still passes `SqlSafetyGuard`. |

## 4. Data Model

### 4.1 `widget` (entity `Widget extends BaseEntity`)

Reuses the project's `BaseEntity` (Integer `id`, `createdAt`, `updatedAt`,
soft-delete via `deleted` + `@SQLRestriction`/`@SQLDelete`).

| Column | Type | Notes |
| --- | --- | --- |
| `code` | varchar(150), **unique**, not null | Stable slug for lookups, e.g. `active-patients-count`. Integration/data endpoints resolve by `code`. |
| `name` | varchar(200), not null | Display label. |
| `description` | varchar(1000) | Notes, or the natural-language prompt that produced the query. |
| `module` | enum `WidgetModule` (`WIDGET/INTEGRATION/PROMPT`), not null | Behavior bucket. |
| `type` | enum `WidgetType` (`COUNT/TABLE/BAR/LINE/PIE`), not null | Render hint for the UI. |
| `sql_template` | text (`@Column(columnDefinition="TEXT")`), not null, **`@JsonIgnore`** | The query with `:param` / `{{param}}` placeholders. Never returned to clients. |
| `filters` | JSON (`@JdbcTypeCode(SqlTypes.JSON)` / stored as JSON/text), nullable | Config **object** (see 4.2): `{ filters: [...], sortableColumns: [...] }`. Descriptive metadata for the UI + engine validation. Returned to clients. |
| `enabled` | boolean, not null, default true | Disable a widget without deleting it. |

Uniqueness: `uk_widget_code` on `code`.

### 4.2 Filter metadata JSON (the `filters` column)

A config **object** with two keys:

```json
{
  "filters": [
    {
      "key": "status",
      "label": "Status",
      "type": "string",
      "operators": ["eq", "in"],
      "required": false
    }
  ],
  "sortableColumns": ["Patient", "Status"]
}
```

Each element of `filters`:
- `key` — matches the `:key` / `{{key}}` placeholder in the template.
- `label` — UI label.
- `type` — `string | number | date | boolean` (UI input hint; light server validation).
- `operators` — allowed operators for this filter (server rejects others).
- `required` — if true, execution fails when the filter is absent.

`sortableColumns` — the whitelist of column names/aliases the engine will accept
for `sortBy` (Section 6.3, step 5). Optional; absent/empty means sorting disabled.

### 4.3 `widget_execution_log` (entity `WidgetExecutionLog`)

Append-only audit of every execution. Does **not** extend `BaseEntity` (no soft
delete needed); has its own `id` + `executedAt`.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | Integer, identity | PK |
| `widget_id` | Integer | FK-ish reference to `widget.id` (store id, not a JPA relation, to keep it decoupled). |
| `widget_code` | varchar(150) | Denormalized for easy querying. |
| `module` | varchar(20) | Denormalized. |
| `params_json` | text | The filter params received (JSON). |
| `row_count` | Integer | Rows returned. |
| `duration_ms` | Long | Execution time. |
| `success` | boolean | Whether it succeeded. |
| `error_message` | varchar(500) | Masked/short error if failed. |
| `executed_at` | Instant | Timestamp. |

## 5. Package Layout

Everything under `in.healthconnect.widgetengine`, grouped by responsibility:

```
in.healthconnect.widgetengine
├── entity/
│   ├── Widget.java
│   ├── WidgetExecutionLog.java
│   └── enums/  WidgetModule.java, WidgetType.java, FilterOperator.java
├── repository/
│   ├── WidgetRepository.java
│   └── WidgetExecutionLogRepository.java
├── dto/
│   ├── request/  CreateWidgetRequest, UpdateWidgetRequest, ExecuteWidgetRequest, FilterValue
│   └── response/ WidgetResponse, WidgetSummaryResponse, WidgetDataResponse, PreviewResponse
├── engine/
│   ├── SqlSafetyGuard.java        (SELECT-only, placeholder + operator validation)
│   ├── SqlTemplateEngine.java     (param injection, sorting, pagination -> bound SQL)
│   └── WidgetQueryExecutor.java   (runs SQL via NamedParameterJdbcTemplate)
├── prompt/
│   ├── QueryGenerator.java        (interface: question -> SQL)
│   └── NimQueryGenerator.java     (calls NVIDIA NIM)
├── service/
│   ├── WidgetService.java             (CRUD + validation)
│   └── WidgetExecutionService.java    (render/run + audit logging)
└── controller/
    ├── WidgetController.java          (admin CRUD + preview)
    └── WidgetExecutionController.java  (data + integration endpoints)
```

Rationale: the single top-level package keeps the feature in one place to study;
the sub-packages keep each class focused and independently testable.

## 6. The Query Engine

### 6.1 Template authoring convention

The `sql_template` uses two placeholder kinds:

- `:param` — the **value**. Always passed to JDBC as a **bound parameter**
  (`MapSqlParameterSource`). Never string-concatenated. This is the injection-safe
  path.
- `{{param}}` — the **operator**. Replaced by the engine with a **whitelisted SQL
  operator symbol** (`=`, `<>`, `>`, `>=`, `<`, `<=`, `IN`, `NOT IN`, `LIKE`,
  `NOT LIKE`). The operator comes from a fixed enum, never from raw user text.

Optional-filter pattern (author writes the template so absent filters are inert):

```sql
SELECT p.full_name AS "Patient",
       p.status    AS "Status",
       p.created_at AS from_date
FROM patients p
WHERE 1=1
  AND p.status {{status}} coalesce(:status, p.status)      -- scalar optional filter
  AND p.doctor_id {{doctorIds}} (:doctorIds)               -- IN-list filter
```

- Provided `status` with operator `eq`: becomes `p.status = :status` (value bound).
- Absent `status`: operator defaults to `=`, value bound to `null`, so
  `p.status = coalesce(null, p.status)` -> `p.status = p.status` -> always true.
- `IN`: engine binds a `List`; Spring expands `(:doctorIds)` to `(?, ?, ?)`.
- `LIKE`: engine binds the value already wrapped as `%value%`.

### 6.2 Execution request shape

`POST .../data` body (`ExecuteWidgetRequest`):

```json
{
  "filters": {
    "status": { "operator": "in", "values": ["ACTIVE", "PENDING"] },
    "doctorIds": { "operator": "in", "values": ["12", "15"] }
  },
  "sortBy": "Patient",
  "sortOrder": "asc",
  "pageNo": 1,
  "pageSize": 20
}
```

A filter value may also be a bare string (`"portfolio": "x"`), which the engine
normalizes to `{ operator: "eq", values: ["x"] }`.

### 6.3 `SqlTemplateEngine` algorithm

1. Start from `sql_template`.
2. For each **provided** filter:
   - Validate the operator is (a) a known `FilterOperator` and (b) allowed by the
     widget's filter metadata for that `key`; else reject.
   - Replace `{{key}}` with the whitelisted operator symbol.
   - Bind the value(s) into `MapSqlParameterSource`:
     - scalar ops -> single value under `:key`;
     - `like/notlike` -> `%value%` under `:key`;
     - `in/notin` -> `List<String>` under `:key`.
3. Enforce **required** filters (fail if a required key is missing).
4. **Neutralize** any remaining placeholders (keys present in the template but not
   provided): `{{key}}` -> `=`, bind `:key` -> `null` (relies on the `coalesce`
   convention above). Use the safe named-variable extractor (below).
5. Append `ORDER BY` if `sortBy` is present — **column name whitelisted**. The
   widget's `filters` JSON carries an optional top-level `sortableColumns` array
   (list of allowed sort column names/aliases). `sortBy` is accepted only if it
   appears in that list; otherwise sorting is ignored (never concatenate raw
   `sortBy` text). If `sortableColumns` is absent/empty, sorting is disabled for
   that widget.
6. Append pagination: `LIMIT :__pageSize OFFSET :__offset`, both **bound**.
   `pageNo` is 1-based from the client, converted to a 0-based offset. Enforce a
   **hard max page size** (200) and a default (50). **`hasNext` detection:** bind
   `__pageSize = pageSize + 1` (fetch one extra row); if the extra row comes back,
   `hasNext = true` and the executor trims the list to `pageSize` before returning.
   No `COUNT(*)` query is run (lightweight pagination). A full-count / total-pages
   mode is deferred (Section 11, opt-in `?withTotal=true`).
7. Return `{ finalSql, MapSqlParameterSource }`.

**Safe named-variable extraction** (to find placeholders): before matching
`:(\w+)`, replace `::` (MySQL/Postgres casts are Postgres-only, but keep the
guard) and blank out single-quoted string literals so colons inside strings
(e.g. time formats) are not treated as params. This mirrors the reference
implementation's hard-won fix.

### 6.4 `WidgetQueryExecutor`

- Uses `NamedParameterJdbcTemplate.queryForList(finalSql, paramSource)`.
- Returns `List<Map<String,Object>>` preserving column order (`LinkedHashMap`).
- Optional "hide technical columns" pass: drop columns named `id`, `*_id`,
  `from_date`, `to_date`, `dummy`, `temp` (used for filtering/joining but not for
  display). Controlled by a boolean flag.

### 6.5 `SqlSafetyGuard`

- On **save**: template must be non-empty and its first significant keyword must be
  `SELECT` or `WITH`. Reject `INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/GRANT`, etc.
- On **execute**: re-check SELECT-only (defense in depth).
- Validate operator strings against the `FilterOperator` enum.
- Provide the whitelist for sort columns.

## 7. API Endpoints

All responses wrapped in the existing `ApiResponse<T>`. Base: `/api/v1`.

### 7.1 Admin CRUD — `WidgetController` (`/api/v1/widgets`)
- `POST   /widgets` — create (validates template + filters).
- `GET    /widgets` — list, filter by `module` (query param). Powers board suggestions. Paginated via Spring `Pageable`. Returns `WidgetSummaryResponse` (no SQL).
- `GET    /widgets/{idOrCode}` — fetch one (no SQL exposed).
- `PUT    /widgets/{id}` — update.
- `DELETE /widgets/{id}` — soft delete.
- `POST   /widgets/{idOrCode}/preview` — return the **generated SQL and bound params
  WITHOUT executing** (debugging/learning aid). Guarded so only non-production or
  admin use; returns `PreviewResponse`.

### 7.2 Execution — `WidgetExecutionController`
- `POST /api/v1/widgets/{idOrCode}/data` — execute a `WIDGET` and return
  `WidgetDataResponse { rows, rowCount, pageNo, pageSize, hasNext }`
  (lightweight pagination, no total count — see 6.3 step 6).
- `POST /api/v1/integration/{code}` — execute an `INTEGRATION` widget by code (the
  "API replacement"). Same engine; returns the rows as JSON.
- (`PROMPT` execution path exists in the service but returns a clear
  "not implemented" until Gemini is wired.)

## 8. Error Handling

- SQL/JDBC exceptions are **caught and masked**: clients get a generic
  "Unable to run this widget" message; full details are logged server-side and
  recorded (short form) in the execution log. No query text, column names, or
  driver messages leak to clients.
- Not-found (`idOrCode`) -> 404 via `ApiResponse.error`.
- Validation failures (bad template, disallowed operator, missing required filter,
  non-SELECT) -> 400 with a clear message.
- Integrate with the project's existing exception handling style where present.

## 9. Security & Safety Summary

1. **Values are always bound** (never concatenated) — primary injection defense.
2. **Operators come from an enum**, mapped to fixed symbols.
3. **Sort columns are whitelisted**.
4. **SELECT-only** enforced on save and execute.
5. **Hard page-size cap** prevents runaway result sets.
6. **SQL errors masked** from clients.
7. **`sql_template` never serialized** to clients (`@JsonIgnore`).

## 10. Testing Strategy

- **Unit (test-first)** for `SqlTemplateEngine`: operator mapping, optional-filter
  neutralization, IN-list binding, LIKE wrapping, sort whitelist, pagination math,
  the named-variable extractor edge cases (colons in string literals).
- **Unit** for `SqlSafetyGuard`: accepts SELECT/WITH, rejects DML/DDL, operator
  validation.
- **Slice** (`@DataJpaTest`) for `WidgetRepository`: find by code, module filter,
  soft delete, unique code.
- **Integration** for the execution path against a test schema (MySQL/Testcontainers
  or an equivalent): seed a widget, POST `/data`, assert rows + audit log written.

## 11. Future Enhancements (documented, not built now)

- **Gemini `PROMPT` module** — implement `QueryGenerator` calling Gemini to produce
  MySQL from natural language; validate via `SqlSafetyGuard` before running.
- **Result caching** with short TTL for `WIDGET` (not `INTEGRATION`).
- **Query timeout** to abort long-running queries.
- **Response column metadata** (ordered columns + inferred types) for auto-rendering.
- **DB-driven filter dropdowns** (a `widget_filter` table whose options come from
  their own SQL).
- **Widget versioning / history**.
- **Role-based access** on execution and CRUD.

## 12. Build Order (high level; detailed plan follows)

1. Enums + `Widget` entity + `WidgetExecutionLog` entity + repositories.
2. `SqlSafetyGuard` (+ tests).
3. `SqlTemplateEngine` (+ tests).
4. `WidgetQueryExecutor`.
5. DTOs.
6. `WidgetService` (CRUD + validation).
7. `WidgetExecutionService` (execute + audit).
8. `prompt/` stub.
9. Controllers.
10. Repository/integration tests + a seeded sample widget for manual verification.
