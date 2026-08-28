# AI query generation via NVIDIA NIM + a generic settings table

**Date:** 2026-08-27
**Status:** Approved, not yet implemented
**Scope of this spec:** backend only (`healthconnect`). The frontend round is
described at the end as agreed decisions, not as work to do now.

---

## Problem

The PROMPT module in `in.healthconnect.widgetengine` is complete except for the AI
itself. `QueryGenerator` is a one-method interface; `GeminiQueryGenerator` implements it
against Google's API but has never been given a key, so `NoopQueryGenerator` is what
actually runs and `POST /api/v1/ai/generate` returns "not implemented yet".

We have an NVIDIA NIM API key. NIM exposes an **OpenAI-compatible** `/chat/completions`
endpoint, so it drops into the same seam Gemini used.

Separately, `gemini.api-key` lived in `application.properties` — a tracked file. A
credential in a tracked file is permanent once committed, and changing it needs a
restart. We want credentials in the database instead, manageable over HTTP.

## Decisions

| # | Decision | Chosen | Why |
|---|---|---|---|
| 1 | Scope | Backend now, frontend next round | Prove the model writes good SQL before building a screen around it |
| 2 | Review flow | Generate → preview SQL + rows → approve on add | Reuses the DRAFT/approve design already built; keeps broken queries off the board |
| 3 | Widget type for AI drafts | Always `TABLE` | Already what `SqlDraftService` does; no backend change needed |
| 4 | Gemini | Replace, don't keep | One provider to maintain; `QueryGenerator` keeps the seam if another is ever needed |
| 5 | Model | ~~`qwen/qwen2.5-coder-32b-instruct`~~ → **`nvidia/nemotron-3-super-120b-a12b`** | The first choice was retired by NIM on 2026-05-12; see Correction 5. Swappable via a settings row |
| 6 | Settings shape | One row per value (`nim.api-key`, `nim.model`, …) | Keeps the table generic; secret-masking works per value |
| 7 | Settings UI | Endpoints only this round | A settings screen is its own feature; one curl unblocks development |

### Rejected, and why

- **Keeping Gemini behind an `ai.provider` property.** Two HTTP clients for one job. The
  `QueryGenerator` interface already makes re-adding a provider cheap if we need it.
- **A reasoning model** (`deepseek-ai/deepseek-r1`, Nemotron). They emit `<think>…</think>`
  before the answer. `SqlCleaner` strips markdown fences and a trailing `;` but not think
  blocks, so the trace would reach `SqlSafetyGuard`, fail the "starts with SELECT" rule,
  and break every generation.
- **A JSON blob setting** (`name = "AICredential"`, value = JSON). Masking would be
  all-or-nothing on a group that mixes one secret with two non-secrets.
- **A properties fallback** when a setting row is absent. Two sources of truth for one
  credential; changing the key in one place and seeing no effect is a bad afternoon.

---

## Design

### 1. New `setting` package

A generic key/value settings table, laid out like the rest of the project.

**`entity/AppSetting.java`** — `extends BaseEntity` (so: `Integer id`, `createdAt`,
`updatedAt`, soft delete via `@SQLRestriction`/`@SQLDelete`, matching `Widget`).

| Field | Type | Notes |
|---|---|---|
| `name` | `String`, unique (`uk_app_setting_name`) | e.g. `nim.api-key` |
| `value` | `TEXT` | the raw value |
| `secret` | `boolean`, default `false` | masks the value on read |
| `description` | `String` | free text for whoever reads the settings list |
| `enabled` | `boolean`, default `true` | a disabled row reads as absent |

**`repository/AppSettingRepository.java`** — `findByName(String)`, `existsByName(String)`.

**`service/SettingService.java`**

- CRUD: `create`, `list`, `get(id)`, `update(id, req)`, `delete(id)` (soft). `list`
  returns every non-deleted row, **including disabled ones**, so a disabled setting is
  still visible and can be re-enabled from the API.
- `create` rejects a duplicate `name` with `IllegalArgumentException` (→ 400).
- `get(id)` on a missing row throws `ResourceNotFoundException` (→ 404).
- `getRequired(name)` → the **real** value. Throws `SettingNotConfiguredException` when the row is
  missing, `enabled = false`, or the value is blank, with a message naming the setting:
  `"Setting 'nim.api-key' is not configured. Add it under /api/v1/settings."`
- `getOrDefault(name, fallback)` → the value, or `fallback` when missing/disabled/blank.

**DTOs**

- `AppSettingRequest` — `name` (`@NotBlank`, `@Size`), `value` (`@NotBlank`), `secret`,
  `description`, `enabled`. Update reuses this DTO, so a client must still send `name` to
  satisfy validation, but the service **ignores** it and never changes the stored name —
  a name must not change once something reads it, the same rule as `Widget.code`.
- `AppSettingResponse` — `id`, `name`, `value`, `secret`, `description`, `enabled`,
  timestamps. **`from(setting)` masks `value` when `secret` is true.**

**Masking rule** (in `AppSettingResponse.from`, deliberately *not* in the controller, so
no future endpoint can leak a secret by forgetting to mask):

- not secret → the value, unchanged
- secret and null/blank → `null`
- secret, 8 characters or fewer → `"****"` (too short to reveal any of it)
- secret, longer → first 4 + `"****"` + last 4, e.g. `nvap****3f2a`

**`controller/AppSettingController.java`** — `/api/v1/settings`, wrapped in `ApiResponse`,
relying on `GlobalExceptionHandler` for status codes (no try/catch):

| Method | Path | What |
|---|---|---|
| POST | `/api/v1/settings` | create (`@Valid`) |
| GET | `/api/v1/settings` | list all (values masked where secret) |
| GET | `/api/v1/settings/{id}` | one setting |
| PUT | `/api/v1/settings/{id}` | update value/secret/description/enabled |
| DELETE | `/api/v1/settings/{id}` | soft delete |

### 2. `NimQueryGenerator` replaces `GeminiQueryGenerator`

**Deleted:** `prompt/GeminiQueryGenerator.java`, `prompt/NoopQueryGenerator.java`, and
their tests `GeminiQueryGeneratorTest`, `NoopQueryGeneratorTest`.

`NoopQueryGenerator` goes because the NIM bean is no longer conditional (its credential
lives in the DB, so `@ConditionalOnProperty` can't see it), which makes the no-op
unreachable. The behaviour it protected — a clear error instead of a broken call when no
key is set — moves into `getRequired`, with a better message.

**Added:** `prompt/NimQueryGenerator.java`

```java
@Component
@Primary
public class NimQueryGenerator implements QueryGenerator
```

Dependencies: `ObjectMapper`, `SettingService`.

`generateSql(String prompt)`:

1. `String key = settingService.getRequired("nim.api-key");`
2. `String model = settingService.getOrDefault("nim.model", "qwen/qwen2.5-coder-32b-instruct");`
3. `String baseUrl = settingService.getOrDefault("nim.base-url", "https://integrate.api.nvidia.com/v1");`
4. `POST {baseUrl}/chat/completions` via `RestClient`, header
   `Authorization: Bearer {key}`, `Content-Type: application/json`, body:
   ```json
   {
     "model": "<model>",
     "messages": [{ "role": "user", "content": "<prompt>" }],
     "temperature": 0,
     "top_p": 0.1,
     "max_tokens": 1024,
     "stream": false
   }
   ```
   Temperature 0 matches the Gemini setup: the same question should give the same SQL.
   The prompt goes in as a single `user` message — `PromptBuilder` already bakes the RULES
   in, so no separate system message.
5. `extractText(responseJson)` reads `choices[0].message.content`, throwing
   `RuntimeException("NIM response did not contain any SQL text.")` when missing or null.

Everything downstream is unchanged: `SqlDraftService` cleans the answer (`SqlCleaner`),
checks it is a safe SELECT (`SqlSafetyGuard`), and stores it as a `DRAFT` `PROMPT` widget.
**AI output is still never trusted.**

**Verified 2026-08-27:** NIM accepts `temperature: 0` for `nvidia/nemotron-3-super-120b-a12b`.
The `0.01` fallback was not needed.

### 3. Config cleanup

Remove from `application.properties`:

```properties
# gemini.api-key=YOUR_GEMINI_API_KEY
gemini.model=gemini-2.5-flash
gemini.base-url=https://generativelanguage.googleapis.com
```

Nothing replaces them — NIM config lives in `app_setting`.

### 4. Journal

`widgetengine/widgetdevelopment.md` is how this package documents itself. Update it:

- **Step 15** — note that `GeminiQueryGenerator` was replaced by NIM and why, so the
  journal doesn't describe a deleted class.
- **Step 16** — the settings table (entity, service, masking rule, endpoints).
- **Step 17** — `NimQueryGenerator` (request shape, response parsing, wiring change from
  `@ConditionalOnProperty` to a runtime credential read).

Each with the same RED → GREEN notes and rebuild-from-scratch checklist as the steps
before them.

---

## Error handling

| Situation | Behaviour |
|---|---|
| No `nim.api-key` row, or disabled, or blank | `SettingNotConfiguredException` → **503**, message names the setting |
| NIM returns non-2xx (bad key, rate limit, unknown model) | `RestClient` throws; `GlobalExceptionHandler` → 500 "An error occurred". The real cause is logged server-side |
| NIM returns 200 with no `choices[0].message.content` | `RuntimeException("NIM response did not contain any SQL text.")` |
| Model returns prose, DDL, or a non-SELECT | `SqlSafetyGuard.assertSelectOnly` throws `IllegalArgumentException` → 400. No widget row is created |
| Model returns valid SELECT against columns that don't exist | Draft **is** created; it fails at run time with the engine's generic "Unable to run this widget", and a failure row lands in `widget_execution_log`. This is the case the frontend preview step exists to catch |
| Duplicate setting `name` | `IllegalArgumentException` → 400 |
| Setting id not found | `ResourceNotFoundException` → 404 |

---

## Testing

Test-first (RED → GREEN), mocked repositories, no database in unit tests — matching the
existing `widgetengine` tests.

**`AppSettingResponseTest`**
1. non-secret value passes through unchanged
2. secret value longer than 8 chars → first 4 + `****` + last 4
3. secret value of 8 chars or fewer → `****` (no characters revealed)
4. secret null/blank value → `null`

**`SettingServiceTest`** (mock `AppSettingRepository`)
1. duplicate `name` rejected
2. valid setting saved with defaults applied (`enabled = true`)
3. `getRequired` returns the **real** (unmasked) value
4. `getRequired` throws when the row is missing
5. `getRequired` throws when the row is disabled
6. `getRequired` throws when the value is blank
7. `getOrDefault` returns the fallback when missing, the value when present
8. `get(id)` on a missing row throws `ResourceNotFoundException`
9. delete calls the repository (soft delete via the entity)

**`NimQueryGeneratorTest`** (mock `SettingService`, no network)
1. missing key → the error message names `nim.api-key`
2. `extractText` reads `choices[0].message.content` from a realistic NIM response body
3. a response with no `content` throws
4. a response with an empty `choices` array throws

**`AppSettingSeedIT`** — creates `app_setting` with `ddl-auto=update` for that run only
(the app itself stays on `validate`), the same trick as `WidgetEngineSeedIT`. Does not
insert a key — the credential is inserted by hand, never committed.

**Live verification** (the part no unit test replaces). With the app running and the key
inserted:

```bash
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" \
  -d '{"name":"nim.api-key","value":"nvapi-...","secret":true,"description":"NVIDIA NIM API key"}'

curl http://localhost:8080/api/v1/settings          # value must come back masked

curl -X POST http://localhost:8080/api/v1/ai/generate -H "Content-Type: application/json" \
  -d '{"question":"list all cardiology doctors"}'   # returns a DRAFT with the SQL

curl -X POST http://localhost:8080/api/v1/widgets/{code}/data \
  -H "Content-Type: application/json" -d '{}'       # the draft actually runs
```

Success means: the masked read hides the key, the generated SQL is a single SELECT that
joins `doctors` → `doctor_specialties_map` → `specialties` correctly, and running it
returns rows.

If the SQL is wrong, the first fixes are **not** code changes — swap the model
(`PUT /api/v1/settings/{id}` on `nim.model` to `meta/llama-3.3-70b-instruct`) or improve
the `hints` in `ai_knowledge`. That's the payoff of the existing design.

---

## Out of scope

- Encrypting setting values at rest. The key sits in the DB in plaintext; anyone with DB
  read access or a backup can read it. Accepted for an internal HMS, recorded here as a
  known risk rather than an oversight.
- A settings management screen.
- Per-user or per-tenant settings. `name` is globally unique.
- Sending only the top-K relevant tables to the model; caching the static prompt prefix.

---

## Next round: the frontend (decided, not built)

Recorded so these decisions aren't re-litigated:

- `AddWidgetDrawer` gets two tabs — **Library** (the existing `module: 'WIDGET'` list,
  unchanged) and **Ask AI**. Drawer widens 380 → 480 to fit the preview.
- New `aiApi.js` (`generateQuery`, `approveWidget`, `discardWidget`), `aiHooks.js`
  (three `useMutation`s), `AskAiPanel.jsx`.
- `boardsHooks.js`: `useWidgetData(idOrCode, pageSize = 50)` gains an optional page size
  (included in the `queryKey`) so the preview can request 5 rows.
- `DashboardPage.jsx` needs no change — `handleAdd` already takes `{ id, name }`, and the
  panel calls `onAdd({ id: draft.widgetId, name: draft.question })`.
- Panel states: idle → generating → generated (SQL in a monospace block, first 5 rows,
  **Add to board** / **Try again** / **Discard**) → adding.
- **Add to board** approves then adds. **Discard** calls `DELETE /widgets/{id}` so
  rejected drafts don't accumulate. If the preview query fails, **Add to board** is
  disabled — a query that can't run never becomes a card.
- Known limitation, accepted: an approved PROMPT widget won't appear in the Library tab
  (it filters `module: 'WIDGET'`), so it lives on the board it was created from. Fixing it
  means also fetching `module: 'PROMPT'` filtered to `status: 'APPROVED'` — a later,
  small addition.

---

## Corrections made during implementation

Recorded here so the spec matches what was actually built.

1. **`IllegalStateException` → 400 was wrong.** `GlobalExceptionHandler` has no mapping
   for `IllegalStateException`, so it fell through to the catch-all `Exception` handler and
   the client received a bare **500 "An error occurred"** — the message naming the missing
   setting never arrived, which would have left the frontend's error Alert useless. Fixed
   with a dedicated `SettingNotConfiguredException` mapped to **503**, following the same
   pattern as `EmailExistException`. Verified live.
2. **`@Primary` dropped from `NimQueryGenerator`.** With `NoopQueryGenerator` deleted it is
   the only `QueryGenerator`, so the annotation resolved nothing and implied a competing
   bean existed.
3. **Column named `setting_value`, not `value`.** `VALUE` is a MySQL keyword.
4. **Additional stale references cleaned up.** Deleting the two generators left dangling
   references in `QueryGenerator.java`, `PromptService.java`, `DESIGN.md`, `API_GUIDE.md`,
   and the journal's Step 8. All corrected.
5. **Both recommended models were dead.** `qwen/qwen2.5-coder-32b-instruct` returned
   **410 Gone** ("end of life on 2026-05-12"), and the fallback suggestion
   `meta/llama-3.3-70b-instruct` is no longer in NIM's catalogue at all. Worse,
   `GET /v1/models` is not a reliable availability check: `nvidia/llama-3.1-nemotron-70b-instruct`
   and `mistralai/codestral-22b-instruct-v0.1` are both listed and both returned **404** for
   this account. The working model is **`nvidia/nemotron-3-super-120b-a12b`**, found by
   `PUT`-ing candidates into the `nim.model` row — no code change, which is precisely what
   the settings-table decision was for.
6. **NIM errors were invisible.** `RestClient.retrieve()` throws
   `RestClientResponseException`, which fell to the catch-all handler as a bare 500 with
   nothing logged, so the 410 above was undiagnosable. `NimQueryGenerator` now catches it,
   logs `model / status / body` (never the key), and rethrows a message pointing at the
   `nim.model` and `nim.api-key` settings.
