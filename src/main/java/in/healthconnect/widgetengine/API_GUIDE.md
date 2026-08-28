# WidgetEngine — Developer API Guide

Everything a developer needs to use the WidgetEngine: how to create widgets, write the SQL
template, define filters, pass dynamic filter values at run time, set column names shown in
the UI, paginate, sort, and use the AI (PROMPT) endpoints.

- **Base URL:** `http://localhost:8080`
- **Content type:** `application/json`
- **Response envelope:** every endpoint returns the project `ApiResponse` wrapper (below).

---

## 1. Concepts in 30 seconds

A **widget** is a saved SQL query + a little metadata, stored in one row. One generic engine
loads it, safely fills in filter values and paging, runs it, and returns rows as
`column → value`. The UI decides how to draw them from the widget's `type`.

Three **modules** (the `module` field):
- `WIDGET` — a dashboard tile; shown as a suggestion when building a board.
- `INTEGRATION` — a stored query exposed as a plain data API (call it by `code`).
- `PROMPT` — an AI-generated query (plain English → SQL); starts as a DRAFT to review.

**Golden safety rules (always on):** only `SELECT` queries; filter *values* are bound
parameters (never pasted into SQL); operators and sort columns come from fixed whitelists;
page size is capped; database errors are hidden from clients.

---

## 2. The response envelope — `ApiResponse`

Every response looks like this:

```json
{
  "success": true,
  "data": { ... },              // the actual payload (varies by endpoint)
  "message": "optional text",
  "errors": ["optional list"],
  "fieldErrors": { "field": "message" }   // present on validation errors
}
```

- On success: `success=true`, `data` filled.
- On error: `success=false`, `message` set, and `fieldErrors` for validation problems.

**HTTP status codes**
| Code | When |
| --- | --- |
| 200 | OK |
| 201 | Widget created |
| 400 | Bad input: validation failed, unknown/So disallowed filter operator, non-SELECT query, widget is disabled |
| 404 | Widget / knowledge / example not found |
| 500 | Unexpected error (query failures are hidden behind a generic message) |

---

## 3. The SQL template — the most important part

A widget's `sqlTemplate` is **not plain SQL** — it is a template with two kinds of blanks.

| Blank | Meaning | Example |
| --- | --- | --- |
| `:name` | a **value**, filled in safely (bound parameter) | `:status` |
| `{{name}}` | an **operator** (`=`, `IN`, `LIKE`…), filled from the whitelist | `{{status}}` |

### 3.1 Optional filter pattern (recommended)

Write optional filters with `coalesce(:name, column)` so the query still works when the
caller does NOT send that filter:

```sql
SELECT d.first_name AS `First Name`,
       d.last_name  AS `Last Name`,
       d.email      AS `Email`
FROM doctors d
WHERE d.is_deleted = false
  AND d.status {{status}} coalesce(:status, d.status)
```

- If the caller sends `status` → `d.status = 'ACTIVE'`.
- If not → `d.status = coalesce(NULL, d.status)` → always true (no filtering).

### 3.2 IN-list filter

Write the placeholder inside parentheses; the engine expands the list:

```sql
AND d.id {{doctorIds}} (:doctorIds)
```
Caller sends `doctorIds` with operator `in` and a list of values → `d.id IN ('1','2','3')`.
> Tip: IN filters are best used as **required** (see filter metadata), because the
> "no filter" trick above only works cleanly for single-value operators.

### 3.3 Column aliases = the column names shown in the UI  ⭐

**Each row is returned as `alias → value`. The alias IS the column header the UI shows.**
Use a SQL alias for every column you want displayed, and wrap it in **backticks** if it has
spaces or capitals:

```sql
SELECT d.first_name AS `First Name`,      -- UI shows "First Name"
       d.consultation_fee AS `Fee`,       -- UI shows "Fee"
       count(*) AS `Total`                -- UI shows "Total"
```
A row comes back as:
```json
{ "First Name": "Asha", "Fee": "500.00", "Total": "12" }
```
So to control the UI column name, **set the alias**. No alias → the raw column name is used.

### 3.4 Hidden "technical" columns (on the `/data` endpoint)

The dashboard `/data` endpoint automatically **removes** columns whose name is `id`, or
contains `_id`, `from_date`, `to_date`, `dummy`, or `temp`. These are useful for filtering/
joining but not for display. Two consequences:
- Keep a raw `property_id` in your query for filtering — it won't clutter the UI.
- If you DO want to show such a value, alias it to a friendly name (e.g.
  `property_id AS \`Property\``) so it is not treated as technical.

The `/integration/{code}` endpoint does **not** hide anything (it returns all columns).

---

## 4. Filter metadata (the `filters` field)

When creating/updating a widget you may pass a `filters` config object. It tells the engine
which filters are allowed (and which operators), and which columns can be sorted. It is also
returned to the UI so it can render the filter inputs.

```json
{
  "filters": [
    { "key": "status",    "label": "Status",  "type": "string", "operators": ["eq", "in"], "required": false },
    { "key": "doctorIds", "label": "Doctors", "type": "number", "operators": ["in"],       "required": true  }
  ],
  "sortableColumns": ["First Name", "Last Name", "Fee"]
}
```

| Field | Meaning |
| --- | --- |
| `filters[].key` | matches the `:key` / `{{key}}` placeholder in the template |
| `filters[].label` | label for the UI input |
| `filters[].type` | `string` / `number` / `date` / `boolean` (UI hint) |
| `filters[].operators` | operators the caller may use for this filter (others are rejected) |
| `filters[].required` | if `true`, the caller MUST send this filter or the run fails |
| `sortableColumns` | the only column names/aliases accepted for `sortBy` (else sorting is ignored) |

**Allowed operators** (the whitelist):

| Key | SQL | Value shape |
| --- | --- | --- |
| `eq` | `=` | single |
| `ne` | `<>` | single |
| `gt` / `gte` | `>` / `>=` | single |
| `lt` / `lte` | `<` / `<=` | single |
| `like` / `notlike` | `LIKE` / `NOT LIKE` | single (auto-wrapped as `%value%`) |
| `in` / `notin` | `IN` / `NOT IN` | list |

---

## 5. Widget CRUD API — `/api/v1/widgets`

### 5.1 Create a widget — `POST /api/v1/widgets`

Body (`CreateWidgetRequest`):

| Field | Required | Notes |
| --- | --- | --- |
| `code` | ✅ | unique slug, e.g. `active-patients` (used in URLs) |
| `name` | ✅ | display title |
| `description` | ❌ | free text |
| `module` | ✅ | `WIDGET` / `INTEGRATION` / `PROMPT` |
| `type` | ✅ | `COUNT` / `TABLE` / `BAR` / `LINE` / `PIE` |
| `sqlTemplate` | ✅ | the SELECT template (Section 3). Must be a SELECT. |
| `filters` | ❌ | filter config object (Section 4) |
| `enabled` | ❌ | defaults to `true` |

Example — a simple COUNT widget:
```bash
curl -X POST http://localhost:8080/api/v1/widgets \
 -H "Content-Type: application/json" \
 -d '{
   "code": "active-doctor-count",
   "name": "Active Doctors",
   "module": "WIDGET",
   "type": "COUNT",
   "sqlTemplate": "SELECT count(*) AS `Total` FROM doctors WHERE is_deleted = false"
 }'
```

Example — a TABLE widget with filters, aliases, and sortable columns:
```bash
curl -X POST http://localhost:8080/api/v1/widgets \
 -H "Content-Type: application/json" \
 -d '{
   "code": "doctor-list",
   "name": "Doctor List",
   "module": "WIDGET",
   "type": "TABLE",
   "sqlTemplate": "SELECT d.first_name AS `First Name`, d.last_name AS `Last Name`, d.email AS `Email`, d.consultation_fee AS `Fee` FROM doctors d WHERE d.is_deleted = false AND lower(d.first_name) {{name}} coalesce(:name, lower(d.first_name)) AND d.consultation_fee {{minFee}} coalesce(:minFee, d.consultation_fee)",
   "filters": {
     "filters": [
       { "key": "name",   "label": "Name",    "type": "string", "operators": ["like","eq"], "required": false },
       { "key": "minFee", "label": "Min Fee", "type": "number", "operators": ["gte"],       "required": false }
     ],
     "sortableColumns": ["First Name", "Last Name", "Fee"]
   }
 }'
```

Response (`WidgetResponse`, note: `sqlTemplate` is **never** returned):
```json
{ "success": true, "message": "Widget created successfully",
  "data": { "id": 12, "code": "doctor-list", "name": "Doctor List",
            "module": "WIDGET", "type": "TABLE",
            "filters": { "filters": [ ... ], "sortableColumns": [ ... ] },
            "enabled": true, "status": "APPROVED",
            "createdAt": "2026-08-27T09:00:00Z", "updatedAt": "2026-08-27T09:00:00Z" } }
```

### 5.2 List widgets — `GET /api/v1/widgets`

Query params (all optional):
| Param | Meaning |
| --- | --- |
| `module` | filter by module, e.g. `?module=WIDGET` (board suggestions) |
| `page` | page number, **0-based** (Spring paging), default `0` |
| `size` | page size, default `20` |
| `sort` | e.g. `sort=name,asc` |

> ⚠️ This list uses **0-based** `page` (Spring `Pageable`). The *data* endpoint (Section 6)
> uses **1-based** `pageNo`. They are different on purpose.

Returns a `Page` of `WidgetSummaryResponse` (`id, code, name, description, module, type, enabled`).

### 5.3 Get one — `GET /api/v1/widgets/{idOrCode}`
Accepts a numeric id or a code. Returns `WidgetResponse` (no `sqlTemplate`).

### 5.4 Update — `PUT /api/v1/widgets/{id}`
Body (`UpdateWidgetRequest`) = same as create **without `code`** (the code never changes).

### 5.5 Delete — `DELETE /api/v1/widgets/{id}`
Soft delete (row is marked deleted, not removed).

### 5.6 Approve — `PUT /api/v1/widgets/{id}/approve`
Flips a `DRAFT` widget (usually AI-generated) to `APPROVED`.

### 5.7 Preview — `POST /api/v1/widgets/{idOrCode}/preview`
Returns the **final SQL + bound values without running it** — great for debugging.
Body is an optional execute request (Section 6). Response (`PreviewResponse`):
```json
{ "success": true,
  "data": { "sql": "SELECT ... WHERE ... limit :__pageSize offset :__offset",
            "params": { "name": "%asha%", "minFee": null, "__pageSize": 21, "__offset": 0 } } }
```

---

## 6. Run a widget (get data) — `POST /api/v1/widgets/{idOrCode}/data`

This is where you pass **dynamic filter values**, sorting, and paging.

### 6.1 Request body (`ExecuteWidgetRequest`) — everything is optional

```json
{
  "filters": {
    "name":   { "operator": "like", "values": ["asha"] },
    "minFee": { "operator": "gte",  "values": ["500"] }
  },
  "sortBy": "Fee",
  "sortOrder": "desc",
  "pageNo": 1,
  "pageSize": 20
}
```

| Field | Optional? | Notes |
| --- | --- | --- |
| `filters` | ✅ | map of `filterKey → { operator, values }`. Omit to run with no filters. |
| `filters[].operator` | — | one of the whitelist keys; must be allowed for that filter |
| `filters[].values` | — | array of strings. `in`/`notin` use the whole list; other operators use the first value |
| `sortBy` | ✅ | must be one of the widget's `sortableColumns`, else ignored |
| `sortOrder` | ✅ | `asc` (default) or `desc` |
| `pageNo` | ✅ | **1-based**, default `1` |
| `pageSize` | ✅ | default `50`, **max `200`** |

You can run with an empty body `{}` — you get the first page with default paging and no
filters.

### 6.2 Response (`WidgetDataResponse`)

```json
{ "success": true,
  "data": {
    "rows": [
      { "First Name": "Asha", "Last Name": "Rao", "Email": "asha@x.com", "Fee": "800.00" }
    ],
    "rowCount": 1,
    "pageNo": 1,
    "pageSize": 20,
    "hasNext": false
  } }
```

- `rows` — each row is `column-alias → value`. The alias is the UI column name (Section 3.3).
- `hasNext` — `true` if there is another page (the engine fetches one extra row to know this;
  there is **no total-count** query, to keep it fast).

### 6.3 Example
```bash
curl -X POST http://localhost:8080/api/v1/widgets/doctor-list/data \
 -H "Content-Type: application/json" \
 -d '{ "filters": { "name": { "operator": "like", "values": ["as"] } },
       "sortBy": "Fee", "sortOrder": "desc", "pageNo": 1, "pageSize": 10 }'
```

A bare string value is also accepted and treated as `eq`:
```json
{ "filters": { "status": "ACTIVE" } }   // same as { "operator":"eq", "values":["ACTIVE"] }
```

---

## 7. Integration endpoint — `POST /api/v1/integration/{code}`

Same engine and same request body as `/data`, but for `INTEGRATION` widgets and it **keeps
all columns** (does not hide technical ones). Use it to expose a stored query as a simple
data API instead of writing a new controller.

```bash
curl -X POST http://localhost:8080/api/v1/integration/active-doctor-count \
 -H "Content-Type: application/json" -d '{}'
```

---

## 8. AI knowledge base API (for the PROMPT module)

The AI needs to know your tables. Manage that knowledge from the UI.

### 8.1 Knowledge — `/api/v1/ai/knowledge`
`POST` / `GET` (list) / `GET /{id}` / `PUT /{id}` / `DELETE /{id}`. Body:
```json
{
  "tableName": "doctors",
  "purpose": "Doctors in the hospital",
  "columnsInfo": "id, first_name, last_name, email, consultation_fee, is_deleted",
  "hints": "join to specialties via doctor_specialties_map; filter is_deleted = false",
  "enabled": true
}
```
Keep `columnsInfo` compact (short text = fewer tokens sent to the AI). One row per table.

### 8.2 Examples — `/api/v1/ai/examples`
`POST` / `GET` (list) / `GET /{id}` / `PUT /{id}` / `DELETE /{id}`. Body:
```json
{ "question": "count all doctors",
  "generatedSql": "SELECT count(*) AS `Total` FROM doctors WHERE is_deleted = false",
  "enabled": true }
```
A few good examples strongly improve accuracy. If a user's question **exactly** matches an
example, the engine reuses that SQL and skips the AI call entirely (saves tokens).

---

## 9. Generate a query with AI — `POST /api/v1/ai/generate`

Body:
```json
{ "question": "list all cardiology doctors" }
```
Flow: build prompt (rules + knowledge + examples + question) → Gemini → clean the answer →
**SELECT-only safety check** → store as a **DRAFT** `PROMPT` widget.

Response (`GeneratedQueryResponse`, includes the SQL so you can review it):
```json
{ "success": true, "message": "Draft query generated. Review it, then approve to use it.",
  "data": {
    "widgetId": 21,
    "code": "list-all-cardiology-doctors",
    "question": "list all cardiology doctors",
    "status": "DRAFT",
    "sql": "SELECT d.first_name AS `First Name`, ... FROM doctors d JOIN doctor_specialties_map m ... WHERE s.name = 'Cardiology'"
  } }
```

Then:
1. Review the `sql`.
2. Run it: `POST /api/v1/widgets/{code}/data` (or `/preview` to just see the final SQL).
3. Approve it: `PUT /api/v1/widgets/{id}/approve`.

> The AI is **off** until you store a `nim.api-key` setting via `POST /api/v1/settings`.
> Without it, `/ai/generate` returns a clear error naming the setting to add.

---

## 10. Recipes

**A read-only count tile**
```json
{ "code":"open-appointments", "name":"Open Appointments", "module":"WIDGET", "type":"COUNT",
  "sqlTemplate":"SELECT count(*) AS `Open` FROM appointments WHERE is_deleted=false AND status='SCHEDULED'" }
```

**A filtered, sorted, paged table** — see the `doctor-list` example in 5.1 + run it in 6.3.

**A join with a required multi-select filter**
```json
{ "code":"doctors-by-specialty", "name":"Doctors by Specialty", "module":"WIDGET", "type":"TABLE",
  "sqlTemplate":"SELECT d.first_name AS `First Name`, d.last_name AS `Last Name` FROM doctors d JOIN doctor_specialties_map m ON m.doctor_id=d.id WHERE d.is_deleted=false AND m.specialty_id {{specialtyIds}} (:specialtyIds)",
  "filters":{ "filters":[{ "key":"specialtyIds","label":"Specialties","type":"number","operators":["in"],"required":true }], "sortableColumns":["First Name","Last Name"] } }
```
Run:
```json
{ "filters": { "specialtyIds": { "operator": "in", "values": ["1","4","7"] } } }
```

---

## 11. Quick reference — all endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/widgets` | create a widget |
| GET | `/api/v1/widgets?module=&page=&size=&sort=` | list widgets (paged) |
| GET | `/api/v1/widgets/{idOrCode}` | get one widget |
| PUT | `/api/v1/widgets/{id}` | update a widget |
| DELETE | `/api/v1/widgets/{id}` | soft-delete a widget |
| PUT | `/api/v1/widgets/{id}/approve` | approve a DRAFT widget |
| POST | `/api/v1/widgets/{idOrCode}/preview` | show final SQL without running |
| POST | `/api/v1/widgets/{idOrCode}/data` | run a WIDGET, get rows (hides technical columns) |
| POST | `/api/v1/integration/{code}` | run an INTEGRATION widget (all columns) |
| POST | `/api/v1/ai/generate` | AI: question → DRAFT query |
| GET/POST/PUT/DELETE | `/api/v1/ai/knowledge` | manage AI table knowledge |
| GET/POST/PUT/DELETE | `/api/v1/ai/examples` | manage AI examples |

---

## 12. Rules to remember (so requests don't get rejected)

1. `sqlTemplate` must be a single `SELECT` (or `WITH … SELECT`). No INSERT/UPDATE/DELETE/DDL.
2. A filter's `operator` must be in the whitelist **and** allowed by that filter's metadata.
3. `sortBy` must be listed in the widget's `sortableColumns`, or it is ignored.
4. `pageSize` above 200 is capped to 200; `pageNo` is 1-based on `/data`.
5. Alias every display column (backticks for spaces) — the alias is the UI column name.
6. Add `is_deleted = false` yourself in the template for tables that have it.
7. `code` is unique and permanent; pick it carefully.
