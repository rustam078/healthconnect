# WidgetEngine — Build Journal (`widgetdevelopment.md`)

A step-by-step walkthrough of how this package was built, in order, so anyone can
rebuild it from scratch as practice. Read [`DESIGN.md`](./DESIGN.md) first for the
*what* and *why*; this file is the *how*, step by step, with pointers to the real code.
To **use** the engine's API (create widgets, filters, aliases, run/paginate, AI endpoints),
see [`API_GUIDE.md`](./API_GUIDE.md).

**Stack:** Spring Boot 4.0.7 · Java 17 · MySQL · Lombok · package `in.healthconnect.widgetengine`

**Golden rule of the engine:** filter *values* are always **bound parameters**;
*operators* and *sort columns* come only from **whitelists**. Raw user text is never
spliced into SQL.

---

## Roadmap (build in this order)

| Step | What | Status |
| --- | --- | --- |
| 1 | Enums + entities + repositories (the data foundation) | ✅ Done |
| 2 | `SqlSafetyGuard` — SELECT-only + operator validation (test-first) | ✅ Done |
| 3 | `SqlTemplateEngine` — param injection, sorting, pagination (test-first) | ✅ Done |
| 4 | `WidgetQueryExecutor` — run bound SQL, shape rows, `hasNext` | ✅ Done |
| 5 | DTOs (request/response) | ✅ Done |
| 6 | `WidgetService` — CRUD + validation | ✅ Done |
| 7 | `WidgetExecutionService` — execute + write audit log | ✅ Done |
| 8 | `prompt/` stub — `QueryGenerator` + Gemini injection point | ✅ Done |
| 9 | Controllers — CRUD + preview + data + integration | ✅ Done |
| 10 | Real MySQL end-to-end run + a seeded sample widget | ✅ Done |

### Part 2 — the PROMPT module (plain English → SQL with Gemini)

| Step | What | Status |
| --- | --- | --- |
| 11 | Knowledge-base entities + repositories (`ai_knowledge`, `ai_prompt_example`) | ✅ Done |
| 12 | `KnowledgeBaseService` + controllers + seed the HMS schema knowledge | ✅ Done |
| 13 | `PromptBuilder` — build a compact, token-cheap prompt (test-first) | ✅ Done |
| 14 | `SqlCleaner` + draft flow — `WidgetStatus`, generate → clean → validate → DRAFT, approve | ✅ Done |
| 15 | `GeminiQueryGenerator` — the real AI call (Gemini API + key) | ⛔ Replaced by 17 |
| 16 | `app_setting` table — settings + secret masking (test-first) | ✅ Done |
| 17 | `NimQueryGenerator` — the real AI call (NVIDIA NIM) | ✅ Done |

**How the AI part will work (the plan):** we send Gemini four things — short RULES, the
KNOWLEDGE (your tables, compact), a few EXAMPLES, and the user's QUESTION. Only the question
changes each time, so the big static part can be cached to save tokens. Gemini returns SQL,
which must still pass `SqlSafetyGuard`, is saved as a DRAFT `PROMPT` widget, and can be
previewed/run before you approve it. The generated query reuses the whole engine we already
built.

---

## Step 1 — Data foundation (enums, entities, repositories)

**Goal:** model a widget and its audit log the way the rest of HealthConnect models
its domain, so the engine has something concrete to read and write.

### 1a. Enums — [`entity/enums/`](./entity/enums)

- [`WidgetModule`](./entity/enums/WidgetModule.java) — `WIDGET | INTEGRATION | PROMPT`.
  Decides the *behaviour bucket*; the engine itself is shared across all three.
- [`WidgetType`](./entity/enums/WidgetType.java) — `COUNT | TABLE | BAR | LINE | PIE`.
  A pure rendering hint for the UI; the backend never draws anything.
- [`FilterOperator`](./entity/enums/FilterOperator.java) — **the safety whitelist.**
  Each constant carries a fixed SQL `symbol` (`=`, `IN`, `LIKE`, …) plus two shape
  flags: `list` (IN/NOT IN → value is a collection) and `like` (LIKE/NOT LIKE →
  value wrapped as `%v%`). `fromKey("in")` resolves a client key to a constant, and
  returns empty for anything unknown so callers can reject it. **Because the symbol
  is hard-coded per constant, an attacker can never inject an operator.**

### 1b. `Widget` entity — [`entity/Widget.java`](./entity/Widget.java)

Extends the project's `BaseEntity`, so it inherits `Integer id`, `createdAt`,
`updatedAt`, and the soft-delete flag with `@SQLRestriction("is_deleted = false")`.
Mirrors `Specialty`'s `@SQLDelete` so a `DELETE` becomes a soft delete.

Key fields and *why*:
- `code` — unique slug (`uk_widget_code`). Integration/data endpoints resolve widgets
  by this stable, readable value instead of a brittle numeric id.
- `sqlTemplate` — `TEXT`, **`@JsonIgnore`**. The query lives in the DB, and it must
  never be serialized to a client (that would leak the whole query surface).
- `filters` — `TEXT` holding a JSON config object
  (`{ "filters": [...], "sortableColumns": [...] }`). Stored as text for portability
  across databases; parsed with Jackson when the engine needs it. `@JsonRawValue`
  passes it through to clients as real JSON (no double-encoding).
- `enabled` — soft on/off switch, `@Builder.Default true`.

> Note: we store JSON as `TEXT` rather than a native MySQL `JSON` column to keep the
> mapping simple and portable while learning. Switching to a native JSON type later is
> a one-annotation change (`@JdbcTypeCode(SqlTypes.JSON)`).

### 1c. `WidgetExecutionLog` entity — [`entity/WidgetExecutionLog.java`](./entity/WidgetExecutionLog.java)

Append-only audit row per execution: `widgetId`, `widgetCode`, `module`, `paramsJson`,
`rowCount`, `durationMs`, `success`, `errorMessage`, `executedAt`. **Deliberately does
NOT extend `BaseEntity`** — an audit log is never updated or soft-deleted, so it only
needs its own id + timestamp. `@PrePersist` stamps `executedAt` if unset.

### 1d. Repositories — [`repository/`](./repository)

- [`WidgetRepository`](./repository/WidgetRepository.java) — `findByCode`,
  `existsByCode`, and `findByModule(module, Pageable)`. These are ordinary JPA queries,
  so they get **Spring Data auto-pagination** and the soft-delete filter for free. (This
  is the pagination world that *can* use `Pageable`/`Page`; the widget-*data* path
  cannot — see Step 4.)
- [`WidgetExecutionLogRepository`](./repository/WidgetExecutionLogRepository.java) —
  plain CRUD; we only insert and (later) read.

### 1e. Verify

```bash
./mvnw -o compile
```
→ `BUILD SUCCESS`, nine `widgetengine` classes compiled. No DB or app startup needed
to verify structure at this stage.

**Rebuild-from-scratch checklist for Step 1:**
1. Create the package `in.healthconnect.widgetengine` with `entity`, `entity/enums`,
   `repository` subpackages.
2. Write the three enums (get `FilterOperator`'s symbol/list/like table right — the
   engine depends on it).
3. Write `Widget extends BaseEntity` (unique `code`, `@JsonIgnore` on `sqlTemplate`).
4. Write `WidgetExecutionLog` (standalone, not `BaseEntity`).
5. Write the two repositories.
6. `./mvnw -o compile` → green.

---

---

## Step 2 — `SqlSafetyGuard` (the security gate), built test-first

**Goal:** one small class that decides whether a stored query is safe to run, plus a
check that a filter operator is one we allow. This is the first place we use
**TDD (Test-Driven Development)**: write the test first, watch it fail, then write the
code that makes it pass.

### Why test-first here?
The safety rules are the most important part of the whole engine. If we get them
wrong, someone could change or delete data. Writing the tests first forces us to spell
out every rule clearly *before* coding, and proves the code actually enforces them.

### The three RED-GREEN steps
1. **RED — write the test.** [`SqlSafetyGuardTest`](../../../../../test/java/in/healthconnect/widgetengine/engine/SqlSafetyGuardTest.java)
   lists every rule as a tiny test: plain SELECT is allowed; WITH is allowed; UPDATE/
   DELETE/INSERT/DROP are blocked; null/blank blocked; two statements stuck together
   blocked; writing to a file blocked; unknown operator rejected.
2. **Watch it fail.** We first wrote a fake ("stub") `SqlSafetyGuard` that returns the
   wrong answers on purpose, and ran only this test:
   ```bash
   ./mvnw test -Dtest=SqlSafetyGuardTest
   ```
   Result: `Tests run: 17, Failures: 8`. Good — the tests really do check something.
3. **GREEN — write the real code.** Then we filled in
   [`SqlSafetyGuard`](./engine/SqlSafetyGuard.java) and re-ran:
   `Tests run: 17, Failures: 0` → **all pass.**

### What the guard actually checks — [`engine/SqlSafetyGuard.java`](./engine/SqlSafetyGuard.java)
- `isSelectOnly(sql)` returns true only if the query:
  1. is not empty;
  2. is a **single** command — after allowing one optional `;` at the very end, there
     must be no other `;` (blocks a hidden second command like `SELECT 1; DROP TABLE`);
  3. **starts with `SELECT` or `WITH`** (so it can only read data);
  4. does **not** write results to a file (`INTO OUTFILE` / `INTO DUMPFILE`).
- Before checking, it blanks out text inside quotes (`'...'` → `''`) so a semicolon or
  keyword *inside a normal text value* doesn't cause a false alarm.
- `assertSelectOnly(sql)` does the same check but **throws** a clear error to stop a bad
  query.
- `requireOperator("in")` turns an operator word into a known `FilterOperator`, or
  throws if the word isn't allowed.

> Simple rule of thumb: **operators and query shape come from our fixed lists; user
> text is never trusted as SQL.**

> Note (kept simple on purpose): a query that *starts with a comment* is currently
> treated as unsafe. That's fine for now. When we add the AI (PROMPT) module later,
> we may allow leading comments — we'll add a test for it then.

### Also in this step: simpler comments everywhere
We rewrote the Step 1 comments in plain English so a non-technical reader can follow
them. No code changed — only the explanations.

### Rebuild-from-scratch checklist for Step 2
1. Write `SqlSafetyGuardTest` with one small test per rule (see the list above).
2. Create a stub `SqlSafetyGuard` that returns wrong answers, run the test, confirm it
   **fails**.
3. Implement the real checks, run again, confirm it **passes** (17/17).

---

---

## Step 3 — `SqlTemplateEngine` (the brain), built test-first

**Goal:** turn a saved query *template* + the filters the user sent into the *final
query*. This is the class that makes filtering, sorting, and paging actually work.

### The template idea (very important)
A saved query has two kinds of blanks:

```sql
SELECT p.full_name AS `Patient`, p.status AS `Status`
FROM patients p
WHERE 1=1
  AND p.status {{status}} coalesce(:status, p.status)     -- {{status}} = operator, :status = value
  AND p.doctor_id {{doctorIds}} (:doctorIds)              -- IN list
```

- `:name` is a **value**. We keep it as `:name` in the text and hand the real value to
  the database separately. The database fills it in safely. This is what stops SQL
  injection — the value can never become part of the query's code.
- `{{name}}` is an **operator**. We replace it with a safe symbol (`=`, `IN`, `LIKE`…)
  taken from our whitelist. Never from the user's raw text.

### What `build(...)` does, in order — [`engine/SqlTemplateEngine.java`](./engine/SqlTemplateEngine.java)
1. **For each filter the user sent:** check the widget allows that filter and that
   operator, put the operator symbol into `{{name}}`, and bind the value under `:name`.
   - `LIKE` wraps the value as `%value%` (a "contains" search).
   - `IN` binds the whole list; the database expands `(:name)` into `(?, ?, ?)`.
2. **Required filters:** if the widget marks a filter as required and it wasn't sent,
   stop with a clear error.
3. **Leftover blanks become harmless:** filters the user did *not* send get their
   `{{name}}` turned into `=` and their `:name` bound to `null`. Because templates use
   `coalesce(:name, column)`, a `null` means "don't filter on this".
4. **Sorting:** only if `sortBy` is in the widget's approved `sortableColumns` list
   (and is a simple name). Otherwise sorting is ignored. The column is wrapped in
   back-ticks (that's how MySQL quotes a column name).
5. **Paging:** add `LIMIT :__pageSize OFFSET :__offset`. Page size defaults to 50 and is
   capped at 200. Page number starts at 1. **Trick:** we ask for `pageSize + 1` rows —
   if that extra row comes back, we know there's a next page (this is the `hasNext`
   idea; the executor uses it in Step 4).

### The small helper types (just data holders)
- [`FilterInput`](./engine/FilterInput.java) — what the user sends: `operator` + `values`.
- [`FilterRule`](./engine/FilterRule.java) — what the widget allows: `key`,
  `allowedOperators`, `required`.
- [`QueryBuildRequest`](./engine/QueryBuildRequest.java) — all inputs in one `@Builder`
  object.
- [`PreparedQuery`](./engine/PreparedQuery.java) — the result: final `sql` + the bound
  `params`. **Note:** the engine only *prepares* the query. Running it is Step 4.

### RED → GREEN
1. Wrote [`SqlTemplateEngineTest`](../../../../../test/java/in/healthconnect/widgetengine/engine/SqlTemplateEngineTest.java)
   (15 tests): value binding, LIKE wrapping, IN lists, neutralising missing filters,
   required filters, operator rules, sorting, and paging math.
2. Ran with an empty stub engine → `Tests run: 15, Failures: 14`.
3. Wrote the real `build(...)` → `Tests run: 32, Failures: 0` (guard + engine together).

```bash
./mvnw -o test -Dtest='SqlTemplateEngineTest,SqlSafetyGuardTest'
```

### One honest limitation (kept simple on purpose)
The "make it harmless" trick (`coalesce(:name, column)`) works great for single-value
filters (`=`, `>=`, `LIKE`…). For an **optional** `IN` filter it's awkward, because
`column IN (NULL)` is not "match everything". So: use `IN` filters when they are
**required**, or filter on a single value. We can improve this later if needed.

### Rebuild-from-scratch checklist for Step 3
1. Write the 4 tiny data holders (`FilterInput`, `FilterRule`, `QueryBuildRequest`,
   `PreparedQuery`).
2. Write `SqlTemplateEngineTest` covering: bind value, LIKE, IN, missing filter,
   required filter, bad operator, sorting on/off, paging math + cap.
3. Empty-stub the engine, run → red.
4. Implement `build(...)` in the 5 steps above, run → green (32/32).

---

---

## Step 4 — `WidgetQueryExecutor` (runs the query), built test-first

**Goal:** take the `PreparedQuery` from Step 3, run it on MySQL, and return tidy rows plus
a `hasNext` flag.

### Small refinement first
We added `pageSize` to [`PreparedQuery`](./engine/PreparedQuery.java). The engine already
knows the real page size (after the default and the 200 cap), so it passes it along. That
way the executor knows exactly where to cut the one extra row — no guessing, no duplicated
logic.

### What the executor does — [`engine/WidgetQueryExecutor.java`](./engine/WidgetQueryExecutor.java)
- `execute(prepared, hideTechnicalColumns)`:
  1. `jdbcTemplate.queryForList(sql, params)` runs the query. `NamedParameterJdbcTemplate`
     is Spring's helper that fills in the `:name` values **safely**.
  2. hands the rows to `shape(...)`.
- `shape(raw, pageSize, hide)` is **pure logic (no database)**, so it is easy to test:
  1. we asked for `pageSize + 1` rows; if we got more than `pageSize`, `hasNext = true`;
  2. drop that extra row so the page has exactly `pageSize` rows;
  3. copy each row into a fresh map, skipping "technical" columns when asked;
  4. keep the columns in the same order (uses `LinkedHashMap`).
- `isTechnicalColumn(name)` hides `id`, anything containing `_id`, and date helpers
  (`from_date`, `to_date`) plus `dummy`/`temp`. These help build the query but are not for
  showing to the user.

### [`ExecutionResult`](./engine/ExecutionResult.java)
A tiny record holding `rows` + `hasNext`. This is what the service will turn into the API
response in a later step.

### RED → GREEN
1. [`WidgetQueryExecutorTest`](../../../../../test/java/in/healthconnect/widgetengine/engine/WidgetQueryExecutorTest.java)
   tests only the pure `shape(...)` logic: extra-row → hasNext, exact/fewer rows, hiding
   technical columns, keeping all columns when hide is off, and column order. No database
   needed.
2. Empty-stub `shape(...)` → `Tests run: 6, Failures: 3, Errors: 3`.
3. Real `shape(...)` → all engine tests green: `Tests run: 38, Failures: 0`.

```bash
./mvnw -o test -Dtest='SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest'
```

> Why not test the real database call here? That one line (`queryForList`) is best checked
> by a full integration test with a real widget and a real table — that is Step 10.

### Rebuild-from-scratch checklist for Step 4
1. Add `pageSize` to `PreparedQuery`; have the engine pass it.
2. Write `ExecutionResult` (rows + hasNext).
3. Write `WidgetQueryExecutorTest` for the `shape(...)` rules; stub, run red.
4. Implement `shape(...)` and the thin `execute(...)`; run green (38/38).

---

---

## Step 5 — DTOs (the request/response objects)

**Goal:** small, plain data classes the controllers speak in. No database, no TDD — these
just carry values in and out. They follow the same Lombok style as the rest of the project
(requests: `@Getter/@Setter/@NoArgs/@AllArgs`; responses add `@Builder`).

### Request DTOs — [`dto/request/`](./dto/request)
- [`CreateWidgetRequest`](./dto/request/CreateWidgetRequest.java) — fields to create a
  widget, with validation (`@NotBlank`, `@NotNull`, `@Size`). `filters` is accepted as real
  JSON.
- [`UpdateWidgetRequest`](./dto/request/UpdateWidgetRequest.java) — like create, but **no
  `code`** (the code should never change once other things depend on it).
- [`FilterValue`](./dto/request/FilterValue.java) — one filter the user picked:
  `operator` + `values`.
- [`ExecuteWidgetRequest`](./dto/request/ExecuteWidgetRequest.java) — everything to run a
  widget: `filters` map, `sortBy`/`sortOrder`, `pageNo`/`pageSize` (all optional).

### Response DTOs — [`dto/response/`](./dto/response)
- [`WidgetResponse`](./dto/response/WidgetResponse.java) — full widget details
  (**no `sqlTemplate`** — we never send the query out). Has a `from(widget)` helper.
- [`WidgetSummaryResponse`](./dto/response/WidgetSummaryResponse.java) — short version for
  lists (board suggestions).
- [`WidgetDataResponse`](./dto/response/WidgetDataResponse.java) — the data result:
  `rows`, `rowCount`, `pageNo`, `pageSize`, `hasNext`. Built from the engine's
  `ExecutionResult` via `of(...)`.
- [`PreviewResponse`](./dto/response/PreviewResponse.java) — the final SQL + bound values,
  **without running** the query (for learning/debugging).

Each response has a small `from(...)` / `of(...)` method so the service can convert
database objects to responses in one line.

### ⚠️ Important lesson: Spring Boot 4 uses Jackson 3
Our first compile failed with:
```
package com.fasterxml.jackson.databind does not exist
```
Reason: **Spring Boot 4 upgraded to Jackson 3**, which moved its main classes to a new
package name:
- annotations (like `@JsonRawValue`, `@JsonIgnore`) stayed at
  `com.fasterxml.jackson.annotation` — that is why the entity classes compiled fine;
- but `JsonNode`, `ObjectMapper`, etc. moved to **`tools.jackson.databind`**.

So the correct import is `import tools.jackson.databind.JsonNode;`. We will use
`tools.jackson.databind.ObjectMapper` in the next step to read the `filters` JSON.

### Rebuild-from-scratch checklist for Step 5
1. Create the 4 request DTOs (add validation annotations).
2. Create the 4 response DTOs (add `from`/`of` helpers; never expose `sqlTemplate`).
3. Remember: on Spring Boot 4, import Jackson from `tools.jackson.databind`, not
   `com.fasterxml.jackson.databind`.
4. `./mvnw -o compile` → green.

---

---

## Step 6 — `WidgetService` (CRUD + validation), built test-first

**Goal:** the create / read / update / delete logic for widgets, plus reading the widget's
`filters` JSON into something the engine can use. Two pieces, each built test-first.

### Piece A: `FilterConfigParser` — read the filters JSON
- [`FilterConfig`](./engine/FilterConfig.java) is the parsed result: the list of allowed
  `FilterRule`s + the list of sortable columns.
- [`FilterConfigParser`](./service/FilterConfigParser.java) reads the JSON text with
  Jackson into small holder classes, then converts each operator word into a
  `FilterOperator` using the **same safe lookup** (`fromKey`). Unknown operator = error.
  Bad JSON = error. Empty/no JSON = empty config.
- Tests: [`FilterConfigParserTest`](../../../../../test/java/in/healthconnect/widgetengine/service/FilterConfigParserTest.java)
  (5 tests). We build a plain Jackson mapper in the test with
  `JsonMapper.builder().build()` (Jackson 3 style).

### Piece B: `WidgetService` — the CRUD
- [`WidgetService`](./service/WidgetService.java):
  - **create:** checks the code is unique, checks the query is a safe SELECT
    (`SqlSafetyGuard.assertSelectOnly`), stores the widget (filters JSON saved as text),
    defaults `enabled` to true.
  - **update:** finds the widget, re-checks SELECT-only, updates fields. (It does not
    change `code`.)
  - **getByIdOrCode / findByIdOrCode:** if the text is all digits, look up by id; else by
    code. This is how integration URLs can use a friendly code.
  - **list:** returns a page of short summaries, optionally filtered by module (this powers
    board suggestions). Uses Spring `Pageable`/`Page`.
  - **delete:** soft delete (the entity's `@SQLDelete` turns it into a "mark as deleted").
- Tests: [`WidgetServiceTest`](../../../../../test/java/in/healthconnect/widgetengine/service/WidgetServiceTest.java)
  (7 tests). We use a **real** `SqlSafetyGuard` and a **mock** `WidgetRepository`
  (Mockito), so no database is needed. We check: duplicate code rejected, non-SELECT
  rejected, valid widget saved, lookup by id and by code, missing widget throws, delete
  calls the repository.

### RED → GREEN
- Parser: stub returns empty config → `5 tests, 4 red`; real parser → green.
- Service: stub returns null → `7 tests, all red`; real service → green.
- Whole package now: `./mvnw -o test` on all widget tests → **50 tests, 0 failures.**

```bash
./mvnw -o test -Dtest='FilterConfigParserTest,WidgetServiceTest,SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest'
```

### What "mock" means (for beginners)
A **mock** is a pretend object. Instead of a real database repository, we give the service
a fake one and tell it what to answer (e.g. "when asked existsByCode, say true"). This lets
us test the service's decisions quickly, without a running database.

### Rebuild-from-scratch checklist for Step 6
1. Write `FilterConfig` + `FilterConfigParserTest`; stub, red; implement parser, green.
2. Write `WidgetServiceTest` with a mock repository; stub the service, red.
3. Implement `WidgetService` (unique code, SELECT-only, id-or-code lookup, soft delete),
   green (50/50 across the package).

---

---

## Step 7 — `WidgetExecutionService` (the join-up), built test-first

**Goal:** the class that actually runs a widget by using everything built so far.

### The full flow — [`service/WidgetExecutionService.java`](./service/WidgetExecutionService.java)
`execute(idOrCode, request, hideTechnicalColumns)`:
1. **load** the widget (by id or code) and check it is turned on (`enabled`);
2. **re-check** the stored query is a safe SELECT (defense in depth);
3. **read** the widget's filter settings (`FilterConfigParser`);
4. **build** the final query (`SqlTemplateEngine`) - values bound, operators safe, sorting,
   paging;
5. **run** it (`WidgetQueryExecutor`) and measure how long it took;
6. **save** an audit row (success, row count, duration);
7. on any error: log the real problem on the server, save a **failure** audit row, and
   throw a **generic** message so the client never sees the real database error.

`buildPreview(...)` does steps 1-4 and returns the `PreparedQuery` without running it -
that powers the preview endpoint (great for learning: you can see the exact final query).

### Two small translations it does
- **DTO → engine input:** the request's `FilterValue` map is turned into the engine's
  `FilterInput` map.
- **error → safe message:** the real error goes to the log; the client gets
  "Unable to run this widget."

### RED → GREEN
[`WidgetExecutionServiceTest`](../../../../../test/java/in/healthconnect/widgetengine/service/WidgetExecutionServiceTest.java)
uses the **real** engine, guard, and parser (so the real query build runs), but a **mock**
executor (no database) and a **mock** log repository. Three tests:
1. a valid run returns data and saves a success audit row;
2. a disabled widget is rejected;
3. a database error is hidden from the client but saved as a failure audit row
   (checked with an `ArgumentCaptor`, which grabs the object passed to a mock so we can
   inspect it).

Stub → `3 tests, all red`; real service → whole package **53 tests, 0 failures.**

```bash
./mvnw -o test -Dtest='WidgetExecutionServiceTest,WidgetServiceTest,FilterConfigParserTest,SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest'
```

### Rebuild-from-scratch checklist for Step 7
1. Write `WidgetExecutionServiceTest` (real engine/guard/parser, mock executor + log repo).
2. Stub the service, run red.
3. Implement the 7-step flow + preview + audit logging + error masking; run green (53/53).

---

---

## Step 8 — the `prompt/` stub (a home for the AI, wired later)

> **Historical.** `NoopQueryGenerator` was deleted in Step 17 once the real generator
> became unconditional — see Step 17 for why. The `QueryGenerator` interface and the
> "never trust AI output" rule below are still exactly how the module works.

**Goal:** give the PROMPT module (plain English → SQL via Gemini) a clean place to live,
with an obvious "plug in the AI here" point, but without building any AI yet.

### The pieces — [`prompt/`](./prompt)
- [`QueryGenerator`](./prompt/QueryGenerator.java) — a one-method interface:
  `String generateSql(String naturalLanguagePrompt)`. Everything talks to this interface,
  so swapping in the real AI later changes nothing else.
- [`NoopQueryGenerator`](./prompt/NoopQueryGenerator.java) — the "do nothing" version used
  now. It throws a clear "not implemented yet" message so no one is confused.
- [`PromptService`](./prompt/PromptService.java) — shows the flow and, importantly, the
  **injection point**:
  ```
  // TODO: integrate Gemini here.  (inside QueryGenerator)
  String sql = queryGenerator.generateSql(prompt);   // AI writes the SQL
  safetyGuard.assertSelectOnly(sql);                 // but it STILL must pass our safety gate
  ```

### The key lesson: never trust AI output
Even when Gemini writes the query, it goes through the **same** `SqlSafetyGuard` as every
other query. AI output is treated like any untrusted input. This is why we built the guard
as a separate, reusable piece back in Step 2.

### How to add Gemini later (future)
1. Create `GeminiQueryGenerator implements QueryGenerator` that calls the Gemini API.
2. Mark it `@Primary` (or remove the no-op) so Spring injects it instead.
3. Everything else — the safety gate, the engine, the executor — already works.

### Test
[`NoopQueryGeneratorTest`](../../../../../test/java/in/healthconnect/widgetengine/prompt/NoopQueryGeneratorTest.java)
just checks the no-op clearly says "not implemented yet" (1 test, green).

---

---

## Step 9 — the controllers (the HTTP endpoints)

**Goal:** expose everything over HTTP, wrapped in the project's `ApiResponse`, reusing the
project's `GlobalExceptionHandler` (so no try/catch in the controllers).

### First: better error status codes
The project's `GlobalExceptionHandler` already maps exceptions to responses:
- `IllegalArgumentException` → **400** (bad request)
- `ResourceNotFoundException` → **404** (not found)
- validation errors → **400** with field messages
- anything else → **500** "An error occurred"

So we made two small fixes for correct status codes:
- "Widget not found" now throws `ResourceNotFoundException` → **404** (was 400).
- "Widget is turned off" now throws `IllegalArgumentException` → **400** (was a 500).

We updated the two affected tests to match. All good practice: let the shared handler
decide the HTTP status; the service just throws the right *type* of error.

### [`WidgetController`](./controller/WidgetController.java) — base `/api/v1/widgets`
| Method | Path | What |
| --- | --- | --- |
| POST | `/api/v1/widgets` | create a widget (`@Valid` runs the DTO checks) |
| GET | `/api/v1/widgets?module=WIDGET` | list a page of widgets (board suggestions) |
| GET | `/api/v1/widgets/{idOrCode}` | one widget by id or code |
| PUT | `/api/v1/widgets/{id}` | update |
| DELETE | `/api/v1/widgets/{id}` | soft delete |
| POST | `/api/v1/widgets/{idOrCode}/preview` | show the final SQL **without running it** |

### [`WidgetExecutionController`](./controller/WidgetExecutionController.java)
| Method | Path | What |
| --- | --- | --- |
| POST | `/api/v1/widgets/{idOrCode}/data` | run a WIDGET, hide technical columns |
| POST | `/api/v1/integration/{code}` | run an INTEGRATION widget (keep all columns) |

Both take the filters/sort/paging in the request body and return a `WidgetDataResponse`.

### A note on testing
The controllers are thin (they just call the services, which we already tested). We check
them for real in **Step 10** with a full run against the app + a seeded widget. All package
tests still green: **54 tests, 0 failures.**

### Rebuild-from-scratch checklist for Step 9
1. Make the service throw `ResourceNotFoundException` for missing widgets (→ 404).
2. Write `WidgetController` (CRUD + preview) and `WidgetExecutionController` (data +
   integration), each wrapped in `ApiResponse`.
3. `./mvnw -o compile` → green; rely on `GlobalExceptionHandler` for errors.

---

---

## Step 10 — real MySQL end-to-end + a seeded sample widget

**Goal:** prove the whole thing works against the **real MySQL database** (not a mock, not
H2), and leave a real widget + real data behind so you can test it by hand.

### What we did — [`WidgetEngineSeedIT`](../../../../../test/java/in/healthconnect/widgetengine/WidgetEngineSeedIT.java)
This is an integration test (`*IT`) that runs against your MySQL. It:
1. **creates the widget tables if missing** - the app normally uses
   `ddl-auto=validate` (which does NOT create tables), so the test overrides it to
   `update` **for this one run** so Hibernate builds `widget` and `widget_execution_log`
   with the exact schema it expects. (Your `application.properties` is not changed.)
2. **inserts real specialty rows** with `INSERT IGNORE` (skips any that already exist);
3. **creates a sample widget** `specialty-list` (only if it is not there already);
4. **runs it through the whole engine** against MySQL and prints the rows.

Nothing is deleted, so the widget and rows stay in your database for manual testing.

```bash
./mvnw test -Dtest=WidgetEngineSeedIT
```
Real output (sorted by Specialty, page size 10):
```
>>> Widget 'specialty-list' returned 10 row(s):
    {Specialty=Cardiology, Details=Diagnosis and treatment of heart and cardiovascular diseases}
    {Specialty=Dermatology, Details=...}
    ... (10 rows)
```

> Because `ddl-auto=update` already created the tables, the app now starts fine with the
> normal `ddl-auto=validate`.

### The sample widget
| Field | Value |
| --- | --- |
| code | `specialty-list` |
| module | `WIDGET` |
| type | `TABLE` |
| SQL | `SELECT name AS \`Specialty\`, description AS \`Details\` FROM specialties WHERE is_deleted = false AND name {{name}} coalesce(:name, name)` |
| filters | name (eq/like), sortable: Specialty |

---

## How to test it by hand (with the app running)

Start the app (`./mvnw spring-boot:run`), then:

**Get the widget:**
```bash
curl http://localhost:8080/api/v1/widgets/specialty-list
```

**List WIDGET-module widgets (board suggestions):**
```bash
curl "http://localhost:8080/api/v1/widgets?module=WIDGET"
```

**Run it - all rows, sorted:**
```bash
curl -X POST http://localhost:8080/api/v1/widgets/specialty-list/data \
  -H "Content-Type: application/json" \
  -d '{ "sortBy": "Specialty", "sortOrder": "asc", "pageNo": 1, "pageSize": 5 }'
```

**Run it - with a LIKE filter (names containing "logy"):**
```bash
curl -X POST http://localhost:8080/api/v1/widgets/specialty-list/data \
  -H "Content-Type: application/json" \
  -d '{ "filters": { "name": { "operator": "like", "values": ["logy"] } } }'
```

**Preview the final SQL WITHOUT running it (great for learning):**
```bash
curl -X POST http://localhost:8080/api/v1/widgets/specialty-list/preview \
  -H "Content-Type: application/json" \
  -d '{ "filters": { "name": { "operator": "eq", "values": ["Cardiology"] } } }'
```

**Create your own widget:**
```bash
curl -X POST http://localhost:8080/api/v1/widgets \
  -H "Content-Type: application/json" \
  -d '{
        "code": "patient-count",
        "name": "Patient Count",
        "module": "WIDGET",
        "type": "COUNT",
        "sqlTemplate": "SELECT count(*) AS total FROM patient WHERE is_deleted = false",
        "filters": { "filters": [], "sortableColumns": [] }
      }'
```
(Adjust the table name to your real one if needed.)

Every run also writes a row to `widget_execution_log` (the audit trail) - have a look:
`SELECT * FROM widget_execution_log ORDER BY executed_at DESC;`

---

## Done! What you built

A complete, enterprise-style **WidgetEngine** in one package, built test-first:

```
widgetengine/
├── entity/ (+enums)   the data: Widget, WidgetExecutionLog, WidgetModule/Type, FilterOperator
├── repository/        database access
├── engine/            SqlSafetyGuard, SqlTemplateEngine, WidgetQueryExecutor (+ small value types)
├── dto/               request/response objects
├── service/           WidgetService (CRUD), WidgetExecutionService (run+audit), FilterConfigParser
├── prompt/            QueryGenerator interface + Noop + PromptService (Gemini goes here later)
└── controller/        WidgetController (CRUD+preview), WidgetExecutionController (data+integration)
```

- **54 unit tests** (guard, engine, executor, parser, services) + **1 real MySQL
  integration test**, all green.
- Safe by design: values bound, operators/sort columns whitelisted, SELECT-only, page cap,
  DB errors hidden, audit log on every run.
- Three modules: `WIDGET` (built), `INTEGRATION` (built), `PROMPT` (stubbed, ready for
  Gemini).

### Ideas for later
- Add result caching for dashboards (skip for INTEGRATION).
- Add a query timeout and total-count paging (`?withTotal=true`).
- Support optional multi-select (IN) filters more cleanly.

---

# Part 2 — the PROMPT module (plain English → SQL)

**The goal:** a user types a question like *"list all cardiology doctors"* or
*"count all doctors"*, the AI (Gemini) writes the MySQL, we save it as a draft, and the
user can look at the query and run it before approving. The generated query reuses the
whole engine from Part 1.

**Why a knowledge base?** The AI cannot guess your tables. So we store a short description
of each table (the "knowledge base") and a few example question→SQL pairs, and send them to
the AI with every question. Kept compact on purpose, to use fewer tokens.

## Step 11 — knowledge-base entities + repositories

**Goal:** two small tables the AI will read from. Structural only (like Step 1), so no TDD -
just compile.

- [`AiKnowledge`](./entity/AiKnowledge.java) (`ai_knowledge`) — one row per table:
  `tableName` (unique), `purpose` (one line), `columnsInfo` (a compact comma list of
  columns), `hints` (joins, enum meanings), `enabled`. Extends `BaseEntity` (soft delete +
  audit). The compact `columnsInfo` is deliberate: short text = fewer tokens sent to the AI.
- [`AiPromptExample`](./entity/AiPromptExample.java) (`ai_prompt_example`) — one row per
  example: `question` + `generatedSql` + `enabled`. These "few-shot examples" are the
  cheapest way to boost accuracy.
- [`AiKnowledgeRepository`](./repository/AiKnowledgeRepository.java) —
  `findByEnabledTrueOrderByTableNameAsc` (what we send to the AI), `findByTableName`,
  `existsByTableName`.
- [`AiPromptExampleRepository`](./repository/AiPromptExampleRepository.java) —
  `findByEnabledTrue`.

`./mvnw -o compile` → green. (The real tables get created in the next step's seeding run,
the same way Step 10 created the widget tables.)

### Rebuild-from-scratch checklist for Step 11
1. Create `AiKnowledge` + `AiPromptExample` (both extend `BaseEntity`, both soft-deletable).
2. Create their two repositories with the finders above.
3. `./mvnw -o compile` → green.

---

---

## Step 12 — manage the knowledge base from the UI + seed it

**Goal:** let the UI fully control the knowledge base (add/edit/remove tables and examples),
and put the real HealthConnect schema in so the AI has something to work with.

### Service — [`KnowledgeBaseService`](./service/KnowledgeBaseService.java) (test-first)
Plain CRUD for both tables: `createKnowledge / listKnowledge / getKnowledge /
updateKnowledge / deleteKnowledge`, and the same five for examples. Rules: one knowledge
row per table (no duplicate `tableName`); "not found" throws `ResourceNotFoundException`
(→ 404); delete is soft. Tested with mock repositories
([`KnowledgeBaseServiceTest`](../../../../../test/java/in/healthconnect/widgetengine/service/KnowledgeBaseServiceTest.java),
5 tests): stub → red, real service → green.

### UI endpoints (full CRUD, wrapped in `ApiResponse`)
[`AiKnowledgeController`](./controller/AiKnowledgeController.java) — `/api/v1/ai/knowledge`
and [`AiPromptExampleController`](./controller/AiPromptExampleController.java) —
`/api/v1/ai/examples`. Each supports `POST` / `GET` (list) / `GET /{id}` / `PUT /{id}` /
`DELETE /{id}`. So the UI can build a "Knowledge Base" screen with no extra backend work.

### DTOs — [`dto/request`](./dto/request) + [`dto/response`](./dto/response)
`AiKnowledgeRequest`/`Response` and `AiPromptExampleRequest`/`Response` (with `from(...)`
mappers). Requests carry validation (`@NotBlank`, `@Size`).

### Seeded into real MySQL — [`KnowledgeBaseSeedIT`](../../../../../test/java/in/healthconnect/widgetengine/KnowledgeBaseSeedIT.java)
Creates the two tables (via `ddl-auto=update`, this run only) and fills them with the real
schema, then leaves them for you to edit from the UI:
- **5 knowledge rows:** `doctors`, `specialties`, `doctor_specialties_map`, `patient`,
  `appointments` - each with a compact column list and join/enum HINTS.
- **3 examples:** "count all doctors", "list all cardiology doctors" (the join!),
  "list all patients".

```bash
./mvnw test -Dtest=KnowledgeBaseSeedIT
```

### Manage it by hand (with the app running)
```bash
# see all knowledge
curl http://localhost:8080/api/v1/ai/knowledge
# add knowledge for a new table
curl -X POST http://localhost:8080/api/v1/ai/knowledge -H "Content-Type: application/json" \
  -d '{ "tableName":"appointments", "purpose":"Appointments", "columnsInfo":"id, doctor_id, patient_id, status, is_deleted", "hints":"join to doctors and patient" }'
# add an example
curl -X POST http://localhost:8080/api/v1/ai/examples -H "Content-Type: application/json" \
  -d '{ "question":"how many appointments today", "generatedSql":"SELECT count(*) AS total FROM appointments WHERE is_deleted=false AND appointment_date = CURDATE()" }'
```

### Rebuild-from-scratch checklist for Step 12
1. Write the 4 DTOs.
2. `KnowledgeBaseService` test-first (mock repos), then implement CRUD for both tables.
3. Two controllers under `/api/v1/ai/knowledge` and `/api/v1/ai/examples`.
4. A seed IT that creates the tables and inserts the real schema + examples.

---

---

## Step 13 — `PromptBuilder` (turn the knowledge base into one prompt)

**Goal:** build the single text prompt we send the AI: **RULES + SCHEMA + EXAMPLES +
QUESTION**. Only the QUESTION changes each time, so the rest can be cached later to save
tokens.

### The rules we bake in — [`prompt/PromptBuilder.java`](./prompt/PromptBuilder.java)
Adapted from a proven BigQuery text-to-SQL prompt, rewritten for **MySQL + SELECT-only**:
- **Output rules:** exactly one SELECT; no markdown/fences/comments; never
  INSERT/UPDATE/DELETE/DDL; single quotes for text; backtick an alias only if it has a space.
- **Column rules:** use ONLY tables/columns from SCHEMA (never invent one); add
  `is_deleted = false` where the table has it; use the HINTS to join.
- **MySQL-only rules:** no `::cast`, no `ILIKE`, no `to_char`, no `date_trunc`, no
  `interval '...'`; use `LOWER() LIKE`, `CURDATE()`, `NOW()`.
- **Final check:** the model re-verifies single-SELECT, real columns, is_deleted, no
  markdown, before answering.

These directly enforce your three asks: **only SELECT**, **MySQL-compatible**, and a
**final rule check**. (The `SqlSafetyGuard` from Step 2 then enforces SELECT-only again in
code - belt and suspenders.)

### Token thrift
The schema is sent compactly (`table : purpose` + one `columns:` line + one `hints:` line).
For a small schema like HealthConnect we can send all enabled tables; as the knowledge base
grows we can send only the top-K tables/examples most relevant to the question (the
reference code scores them by shared words) - a future tweak, not needed yet.

### Test → [`PromptBuilderTest`](../../../../../test/java/in/healthconnect/widgetengine/prompt/PromptBuilderTest.java)
5 tests: the prompt includes the question, the schema (table/column/hint), the examples,
mentions MySQL + SELECT-only + forbids INSERT/DELETE, and forbids markdown fences. Stub →
5 red; real builder → green.

### Rebuild-from-scratch checklist for Step 13
1. Write `PromptBuilderTest` for the four parts + the key rules.
2. Stub `build(...)` returning "", run red.
3. Implement the RULES text + schema/examples/question assembly, run green (5/5).

---

---

## Step 14 — `SqlCleaner` + the draft flow (generate → review → approve)

**Goal:** take a question, turn it into SQL, and store it as a DRAFT the user reviews
before it becomes a real widget. The AI itself is still the no-op stub - we test the whole
flow with a fake AI, so Step 15 only has to swap in the real call.

### `SqlCleaner` — [`prompt/SqlCleaner.java`](./prompt/SqlCleaner.java) (test-first)
AI answers often come wrapped in ```` ```sql ... ``` ```` and end with ";". The cleaner
strips the fences, trims, and removes a trailing ";" (which would break the engine when it
appends ORDER BY / LIMIT). 6 tests.

### `WidgetStatus` — [`entity/enums/WidgetStatus.java`](./entity/enums/WidgetStatus.java)
`DRAFT` or `APPROVED`. We added a `status` column to `Widget`. Hand-made widgets are saved
`APPROVED`; AI drafts are saved `DRAFT`. (Older rows have null status, which we treat as
approved.) The column was added to MySQL by re-running the seed IT with `ddl-auto=update`.

### `SqlDraftService` — [`prompt/SqlDraftService.java`](./prompt/SqlDraftService.java) (test-first)
The orchestration:
1. **exact-match reuse** - if the question exactly matches a saved example (ignoring case
   and extra spaces), reuse that SQL and **skip the AI call** (saves tokens, always correct);
2. otherwise **build the prompt** (knowledge + examples + question) and **ask the AI**;
3. **clean** the answer (`SqlCleaner`);
4. **check** it is a safe SELECT (`SqlSafetyGuard`) - we never trust AI output;
5. **store** it as a `PROMPT` widget marked `DRAFT`, with a readable unique `code` made from
   the question.

Tested (3 tests) with real PromptBuilder/SqlCleaner/SqlSafetyGuard and a **mock AI**:
generate-clean-store, exact-match-skips-AI, and reject-non-SELECT.

### Endpoints
- [`PromptController`](./controller/PromptController.java) - `POST /api/v1/ai/generate`
  with `{ "question": "..." }` → returns a [`GeneratedQueryResponse`](./dto/response/GeneratedQueryResponse.java)
  that **includes the SQL** so you can review it (this is the "see only the query" step).
- `PUT /api/v1/widgets/{id}/approve` (in `WidgetController`) → flips a DRAFT to APPROVED.

So the full loop is: generate a draft → look at the SQL → run it via
`/widgets/{code}/data` to see results → approve it. Until Step 15, `/ai/generate` returns
"not implemented yet" because the AI is still the no-op stub.

### Rebuild-from-scratch checklist for Step 14
1. `SqlCleaner` test-first (fences + trailing ";").
2. Add `WidgetStatus` + `status` column; `create` = APPROVED, add `approve(id)`.
3. `SqlDraftService` test-first (exact-match, AI+clean+validate+store DRAFT).
4. `PromptController` (`/ai/generate`) + approve endpoint; migrate the column via a seed run.

---

---

## Step 15 — `GeminiQueryGenerator` (the real AI call)

> **Superseded by Step 17.** `GeminiQueryGenerator` was replaced by `NimQueryGenerator`
> (NVIDIA NIM) and deleted, along with `NoopQueryGenerator`. The section below is kept as
> a record of how the first provider was wired — the `@ConditionalOnProperty` trick no
> longer applies, because the credential now lives in the `app_setting` table rather than
> in `application.properties`.

**Goal:** replace the no-op with a real call to Google's Gemini API. This is the only place
that talks to the AI; everything else already works.

### [`prompt/GeminiQueryGenerator.java`](./prompt/GeminiQueryGenerator.java) (test-first)
- `generateSql(prompt)`:
  1. if no API key is set, fail clearly (don't make a broken call);
  2. build the request body Gemini expects, with **temperature 0** (+ low top_p/top_k) so
     the same question gives the same SQL;
  3. POST to `.../v1beta/models/{model}:generateContent?key=...` using Spring's `RestClient`;
  4. read the SQL text out of the JSON reply (`extractText`).
- The `SqlDraftService` then cleans it, checks it is a safe SELECT, and stores it as a DRAFT
  - so even the AI's output goes through the same safety gate.

### Smart wiring (no key = safe)
```
@Component
@Primary
@ConditionalOnProperty(prefix = "gemini", name = "api-key")
```
- `@ConditionalOnProperty` - this bean only exists **when you set `gemini.api-key`**. No key
  => the bean isn't created and the app keeps using `NoopQueryGenerator`.
- `@Primary` - when it does exist, it's chosen over the no-op automatically.
- So the app runs fine with or without a key; nothing else changes.

### Config — [`application.properties`](../../../resources/application.properties)
```properties
# gemini.api-key=YOUR_GEMINI_API_KEY      <- paste key + uncomment to turn AI on
gemini.model=gemini-2.5-flash
gemini.base-url=https://generativelanguage.googleapis.com
```

### Tests
[`GeminiQueryGeneratorTest`](../../../../../test/java/in/healthconnect/widgetengine/prompt/GeminiQueryGeneratorTest.java)
(2 tests, no network): missing key fails clearly; the SQL text is read correctly from a
sample Gemini JSON response. The real HTTP call is exercised by hand once you add a key.

### Turn it on and use it
1. Get a Gemini API key and put it in `application.properties`:
   `gemini.api-key=...` (uncomment the line). Restart the app.
2. Ask for a query:
   ```bash
   curl -X POST http://localhost:8080/api/v1/ai/generate \
     -H "Content-Type: application/json" \
     -d '{ "question": "list all cardiology doctors" }'
   ```
   You get back a DRAFT widget **with the generated SQL** to review.
3. See it run:
   ```bash
   curl -X POST http://localhost:8080/api/v1/widgets/{code}/data \
     -H "Content-Type: application/json" -d '{}'
   ```
4. Approve it when happy:
   ```bash
   curl -X PUT http://localhost:8080/api/v1/widgets/{id}/approve
   ```

### Rebuild-from-scratch checklist for Step 15
1. `GeminiQueryGeneratorTest` for the key-guard + response parsing.
2. Implement `generateSql` (RestClient POST, temperature 0) + `extractText`.
3. Wire `@Component @Primary @ConditionalOnProperty(gemini.api-key)`; add the 3 config lines.

---

# Done — the PROMPT module is complete

Plain English → Gemini → clean → safety-check → DRAFT widget → review → approve → a normal
widget that runs through the same safe engine as everything else.

- **75 unit tests** + 3 MySQL integration/seed tests, all green; the full app context loads.
- The knowledge base is **UI-controlled** (`/api/v1/ai/knowledge`, `/api/v1/ai/examples`)
  and seeded with the real HealthConnect schema.
- The AI is **off by default** (safe no-op) and turns on the moment you add an API key.
- Token-aware: compact schema, few-shot examples, exact-match reuse skips the AI entirely.

### Ideas for later
- Send only the top-K most relevant tables/examples once the knowledge base is large.
- Cache the static prompt prefix (rules + schema + examples) to save tokens per call.
- Add an `EXPLAIN` dry-run check on a generated draft before it can be approved.
- Add another `QueryGenerator` (e.g. a self-hosted model) if you move off NIM.

---

---

## Step 16 — `app_setting` (settings in the database, not in a file)

**Goal:** stop keeping API keys in `application.properties`. That file is tracked in git,
so a key committed there is permanent, and changing it needs a restart. One small generic
settings table fixes both.

### The table — [`AppSetting`](../../setting/entity/AppSetting.java)
One row per setting: `name` (unique, e.g. `nim.api-key`), `value`, `secret`,
`description`, `enabled`. Extends `BaseEntity`, so it gets soft delete for free.

> The column is called `setting_value`, not `value` — `VALUE` is a MySQL keyword, and
> sidestepping it is cheaper than quoting it everywhere.

### The one rule that matters: masking
[`AppSettingResponse.from(...)`](../../setting/dto/response/AppSettingResponse.java) masks
the value when `secret` is true — nothing to show → `null`; 8 characters or fewer →
`****`; longer → first 4 + `****` + last 4. **Masking lives in the DTO, not the
controller**, so no future endpoint can leak a secret by forgetting to call it.

Server-side code that needs the *real* value calls
[`SettingService`](../../setting/service/SettingService.java)`.getRequired(name)`, which
returns the raw string and throws when the row is missing, disabled, or blank.
`getOrDefault(name, fallback)` is the soft version.

### The error type (learned the hard way)
`getRequired` first threw `IllegalStateException` — and the client got a bare
**500 "An error occurred"**, because `GlobalExceptionHandler` has no mapping for it and
the catch-all `Exception` handler hides messages on purpose. The whole point of the error
is to *name the setting to add*, so it needed its own type:
[`SettingNotConfiguredException`](../../exception/SettingNotConfiguredException.java) →
**503**, message passed through. This follows the same pattern as `EmailExistException`
and `ResourceNotFoundException`.

> Lesson: a clear exception message is worthless if the shared handler swallows it. Check
> what the *client* actually receives, not just what you threw.

### RED → GREEN
- `AppSettingResponseTest` (5 tests): the four masking cases + the `secret` flag.
- `SettingServiceTest` (11 tests, mock repository): duplicate name rejected, defaults
  applied, `getRequired` returns the **unmasked** value, throws when
  missing/disabled/blank, `getOrDefault` falls back, update never renames, 404 on a
  missing id, delete is soft.

```bash
./mvnw -o test -Dtest='AppSettingResponseTest,SettingServiceTest'
```

### Endpoints — [`AppSettingController`](../../setting/controller/AppSettingController.java)
`/api/v1/settings` — POST / GET / GET `{id}` / PUT `{id}` / DELETE `{id}`.

### The table itself
[`AppSettingSeedIT`](../../../../../test/java/in/healthconnect/setting/AppSettingSeedIT.java)
creates `app_setting` with `ddl-auto=update` for that one run, the same trick as Step 10.
It inserts **nothing** — the API key is added by hand and must never be committed.

```bash
./mvnw test -Dtest=AppSettingSeedIT
```

> Run this **before** starting the app after adding the entity. The app uses
> `ddl-auto=validate`, so an `@Entity` with no table stops the context from starting — and
> takes every `@SpringBootTest` down with it.

### Rebuild-from-scratch checklist for Step 16
1. `AppSetting` entity (unique `name`, `setting_value` column, `secret` flag) + repository.
2. Seed IT to create the table; run it before starting the app.
3. `AppSettingResponseTest` → stub → red → implement `mask(...)` → green.
4. `SettingServiceTest` with a mock repository → stub → red → implement CRUD +
   `getRequired`/`getOrDefault` → green.
5. `SettingNotConfiguredException` + a handler in `GlobalExceptionHandler` (503).
6. Thin controller under `/api/v1/settings`.

---

---

## Step 17 — `NimQueryGenerator` (the real AI call, on NVIDIA NIM)

**Goal:** replace the never-configured Gemini call with NVIDIA NIM, and take the
credential out of `application.properties`.

### Why NIM
NIM exposes an **OpenAI-compatible** `/chat/completions` endpoint, so the call is an
ordinary `RestClient` POST with a `Bearer` token. It hosts code-specialised open models;
we default to `nvidia/nemotron-3-super-120b-a12b`, verified working on 2026-08-27.

> **Watch out for reasoning models.** Some emit a think-trace before the answer.
> `SqlCleaner` strips markdown fences and a trailing `;` but not think-blocks, so a trace
> would reach `SqlSafetyGuard`, fail the "starts with SELECT" rule, and break every
> generation. `nemotron-3-super` returns clean SQL and is fine; a model with `reasoning`
> in its name (e.g. `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning`) is the risky kind.
> If you switch models, check the raw output before trusting it.

### The class — [`NimQueryGenerator`](./prompt/NimQueryGenerator.java)
1. read `nim.api-key` from settings (`getRequired` — fails clearly when unset);
2. read `nim.model` and `nim.base-url` with defaults, so only the key is mandatory;
3. POST `{base-url}/chat/completions` with one `user` message, `temperature 0`,
   `top_p 0.1`, `max_tokens 1024` — `PromptBuilder` already bakes the RULES in, so no
   separate system message is needed;
4. `extractText` reads `choices[0].message.content`, throwing if it is missing.

### The wiring change
Gemini used `@ConditionalOnProperty(prefix = "gemini", name = "api-key")` so the bean only
existed when a key was set in a file. A credential in the database cannot be seen by that
annotation, so `NimQueryGenerator` is an ordinary `@Component` and the "no key" case is
handled at call time by `getRequired`. That made `NoopQueryGenerator` unreachable, so it
was deleted too — the clear message it existed to provide now comes from the setting
lookup, and names the exact setting to add.

`@Primary` was dropped as well: with the no-op gone there is only one `QueryGenerator`, so
the annotation would resolve nothing and merely imply a competing bean exists.

### Tests — [`NimQueryGeneratorTest`](../../../../../test/java/in/healthconnect/widgetengine/prompt/NimQueryGeneratorTest.java)
4 tests, no network: missing key fails clearly and names `nim.api-key`; the SQL is read out
of a realistic NIM response; a response with no `content` throws; empty `choices` throws.

### Turn it on and use it
```bash
# 1. store the key (once) - it comes back masked, and is never written to a file
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" \
  -d '{"name":"nim.api-key","value":"nvapi-...","secret":true,"description":"NVIDIA NIM API key"}'

# 2. ask a question -> a DRAFT widget with the SQL to review
curl -X POST http://localhost:8080/api/v1/ai/generate -H "Content-Type: application/json" \
  -d '{"question":"how many appointments were booked today"}'

# 3. run the draft (DRAFT widgets are runnable - execution checks enabled, not status)
curl -X POST http://localhost:8080/api/v1/widgets/{code}/data \
  -H "Content-Type: application/json" -d '{}'

# 4. approve it
curl -X PUT http://localhost:8080/api/v1/widgets/{id}/approve
```

> Careful when testing: a question that **exactly matches** a saved example in
> `ai_prompt_example` is answered from that example and the AI is never called (Step 14's
> token-saving path). To exercise the real model, ask something novel.

### What it actually returned
Verified against real MySQL on 2026-08-27 with `nvidia/nemotron-3-super-120b-a12b`.

**A count question** — *"how many appointments were booked today"*:

```sql
SELECT count(*) AS AppointmentsToday
FROM appointments
WHERE is_deleted = false AND appointment_date = CURDATE()
```

Ran it: `{"rows":[{"AppointmentsToday":6}],"rowCount":1,"hasNext":false}`. Correct MySQL —
`CURDATE()` rather than a Postgres-ism, and the `is_deleted` filter the RULES ask for.

**A join question the model had never seen** — *"show each doctor name together with the
specialty they practise"*:

```sql
SELECT d.first_name AS `First Name`, d.last_name AS `Last Name`, s.name AS Specialty
FROM doctors d
JOIN doctor_specialties_map m ON m.doctor_id = d.id
JOIN specialties s ON s.id = m.specialty_id
WHERE d.is_deleted = false AND s.is_deleted = false
```

Ran it: `Rajesh Kumar / Cardiology`, `Priya Sharma / Pediatrics`,
`Amit Verma / Orthopedics`. It found the many-to-many hop through
`doctor_specialties_map` on its own, from the HINTS in `ai_knowledge` alone.

`temperature: 0` was accepted — no need for the `0.01` fallback.

The full loop works: generate → read the SQL → run the DRAFT → approve.

### The model list is a moving target (worth reading before you debug)

The first model we picked, `qwen/qwen2.5-coder-32b-instruct`, returned:

```
410 GONE — "The model 'qwen/qwen2.5-coder-32b-instruct' has reached its end of life
            on 2026-05-12T00:00:00Z and is no longer available."
```

Two things this taught us:

1. **NIM retires models.** Do not trust a model name from documentation or memory; check
   `GET https://integrate.api.nvidia.com/v1/models` (no auth needed) for what exists today.
2. **Being in that list is not enough.** `nvidia/llama-3.1-nemotron-70b-instruct` and
   `mistralai/codestral-22b-instruct-v0.1` are both listed, and both returned
   **404 Not Found** for our account — a listed model still has to be provisioned for your
   API key.

Neither failure needed a code change. The model is a settings row, so finding a working
one was a sequence of `PUT /api/v1/settings/{id}` calls. That is exactly the payoff of
keeping it out of `application.properties`.

> Until we added the `RestClientResponseException` catch, all of this surfaced as a bare
> **500 "An error occurred"** with nothing in the log. If the AI ever stops working, the
> `NIM call failed: model=… status=… body=…` line is the first place to look.



### Changing the model costs no code
The model is a settings row. If the SQL is weak on joins, `PUT` a different value into
`nim.model` (pick one your account can actually reach) or improve the `hints` in
`ai_knowledge`.
That is the payoff of keeping the AI behind the `QueryGenerator` interface.

### Rebuild-from-scratch checklist for Step 17
1. `NimQueryGeneratorTest` for the key guard + response parsing → stub → red.
2. Implement `generateSql` (RestClient POST, temperature 0) + `extractText` → green.
3. Delete `GeminiQueryGenerator`, `NoopQueryGenerator`, and their tests.
4. Remove the three `gemini.*` lines from `application.properties`.
