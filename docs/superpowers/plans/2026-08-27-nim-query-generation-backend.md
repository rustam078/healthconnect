# NIM Query Generation (Backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `POST /api/v1/ai/generate` actually write MySQL, by replacing the never-configured Gemini generator with an NVIDIA NIM one whose credential lives in a new generic `app_setting` table instead of `application.properties`.

**Architecture:** A new `in.healthconnect.setting` feature package (entity → repository → service → DTOs → controller, laid out like `widgetengine`) provides name/value settings with per-row secret masking on read. `NimQueryGenerator` implements the existing `QueryGenerator` interface, reads its key/model/base-url from that service at call time, and POSTs to NIM's OpenAI-compatible `/chat/completions`. Nothing downstream changes: `SqlDraftService` still cleans the answer, `SqlSafetyGuard` still rejects anything that isn't a single SELECT, and the draft is still stored as a `DRAFT` `PROMPT` widget.

**Tech Stack:** Spring Boot 4.0.7 · Java 17 · MySQL · Lombok · JUnit 5 · Mockito · **Jackson 3** · Spring `RestClient`

**Spec:** `docs/superpowers/specs/2026-08-27-nim-query-generation-design.md`

## Global Constraints

- **Jackson 3, not Jackson 2.** Annotations stay at `com.fasterxml.jackson.annotation`, but `JsonNode` / `ObjectMapper` moved to **`tools.jackson.databind`**. In tests build a mapper with `JsonMapper.builder().build()` (`tools.jackson.databind.json.JsonMapper`). Importing `com.fasterxml.jackson.databind.*` will not compile.
- **Entities extend `in.healthconnect.entity.BaseEntity`** — that supplies `Integer id`, `createdAt`, `updatedAt`, and `is_deleted` with `@SQLRestriction("is_deleted = false")`. Every entity adds its own `@SQLDelete` so delete becomes soft delete.
- **Controllers stay thin.** Wrap results in `in.healthconnect.wrapper.ApiResponse`. No try/catch — the project's `GlobalExceptionHandler` maps `IllegalArgumentException` → 400, `ResourceNotFoundException` → 404, validation errors → 400, anything else → 500.
- **Never mask in a controller.** Masking lives in `AppSettingResponse.from(...)` so no future endpoint can leak a secret by forgetting.
- **`SettingService.getRequired` / `getOrDefault` return the REAL value.** They are server-side readers and must never be serialized to a client.
- **Exact setting names:** `nim.api-key`, `nim.model`, `nim.base-url`.
- **Exact defaults:** model `qwen/qwen2.5-coder-32b-instruct`, base URL `https://integrate.api.nvidia.com/v1`.
- **Never commit a real API key.** It is inserted by hand into the DB, never into a file.
- Run commands from `D:\HMS\healthconnect` using the **Bash** tool (Git Bash), so `./mvnw` works.

### Before you start: the pre-existing staged tree

`git status` on `feature/widget-engine` shows the entire `widgetengine` package staged but **not committed**. A bare `git commit -m "..."` would sweep all of it into your commit.

Every commit step in this plan therefore uses an **explicit pathspec** — `git commit <paths> -m "..."` — which commits only those paths and leaves the rest of the index untouched. Do not replace these with `git add -A`. If the user has since committed that tree, the pathspec form still works unchanged.

### Deliberate correction to the spec

The spec says `NimQueryGenerator` is `@Component @Primary`. Once `NoopQueryGenerator` is deleted (also per the spec), it is the *only* `QueryGenerator` implementation, so `@Primary` resolves nothing and reads as though a competing bean exists. **This plan drops `@Primary`.** If you would rather keep it as a marker for a future second provider, add it back in Task 5 — nothing else depends on the choice.

---

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `src/main/java/in/healthconnect/setting/entity/AppSetting.java` | The row: name, value, secret, description, enabled |
| `src/main/java/in/healthconnect/setting/repository/AppSettingRepository.java` | `findByName`, `existsByName` |
| `src/main/java/in/healthconnect/setting/dto/request/AppSettingRequest.java` | What the client sends (create + update) |
| `src/main/java/in/healthconnect/setting/dto/response/AppSettingResponse.java` | What goes back — **owns the masking rule** |
| `src/main/java/in/healthconnect/setting/service/SettingService.java` | CRUD + the server-side unmasked readers |
| `src/main/java/in/healthconnect/setting/controller/AppSettingController.java` | `/api/v1/settings` |
| `src/main/java/in/healthconnect/widgetengine/prompt/NimQueryGenerator.java` | The one class that talks to NIM |
| `src/test/java/in/healthconnect/setting/dto/response/AppSettingResponseTest.java` | Masking rules |
| `src/test/java/in/healthconnect/setting/service/SettingServiceTest.java` | CRUD + reader rules (mock repo) |
| `src/test/java/in/healthconnect/setting/AppSettingSeedIT.java` | Creates `app_setting` in real MySQL |
| `src/test/java/in/healthconnect/widgetengine/prompt/NimQueryGeneratorTest.java` | Key guard + response parsing (no network) |

**Modified:**

| File | Change |
|---|---|
| `src/main/resources/application.properties` | Delete the three `gemini.*` lines |
| `src/main/java/in/healthconnect/widgetengine/widgetdevelopment.md` | Correct Step 15; add Steps 16 and 17 |

**Deleted:**

| File | Why |
|---|---|
| `src/main/java/in/healthconnect/widgetengine/prompt/GeminiQueryGenerator.java` | Replaced by NIM |
| `src/main/java/in/healthconnect/widgetengine/prompt/NoopQueryGenerator.java` | Unreachable once the NIM bean is unconditional |
| `src/test/java/in/healthconnect/widgetengine/prompt/GeminiQueryGeneratorTest.java` | Tests a deleted class |
| `src/test/java/in/healthconnect/widgetengine/prompt/NoopQueryGeneratorTest.java` | Tests a deleted class |

---

### Task 1: `AppSetting` entity, repository, and the table in MySQL

Structural only — no unit tests, matching how Steps 1 and 11 of `widgetdevelopment.md` were built. The deliverable is a real table.

**Why the seed IT is in this task, not at the end:** the app runs with `spring.jpa.hibernate.ddl-auto=validate`. The moment `AppSetting` exists as an `@Entity` with no matching table, **the application context fails to start** — and so does every `@SpringBootTest`. Creating the table has to happen in the same task that introduces the entity, or you leave the repo unable to boot.

**Files:**
- Create: `src/main/java/in/healthconnect/setting/entity/AppSetting.java`
- Create: `src/main/java/in/healthconnect/setting/repository/AppSettingRepository.java`
- Test: `src/test/java/in/healthconnect/setting/AppSettingSeedIT.java`

**Interfaces:**
- Consumes: `in.healthconnect.entity.BaseEntity` (gives `getId()`, `getCreatedAt()`, `getUpdatedAt()`, `getDeleted()`)
- Produces:
  - `AppSetting` with `getName()/setName(String)`, `getValue()/setValue(String)`, `getSecret()/setSecret(Boolean)`, `getDescription()/setDescription(String)`, `getEnabled()/setEnabled(Boolean)`
  - `AppSettingRepository extends JpaRepository<AppSetting, Integer>` with `Optional<AppSetting> findByName(String)` and `boolean existsByName(String)`

- [ ] **Step 1: Create the entity**

Note the column is named `setting_value`, not `value`. `VALUE` is a MySQL keyword; naming the column around it avoids ever needing to quote it.

```java
package in.healthconnect.setting.entity;

import in.healthconnect.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

// One row = one application setting, e.g. name="nim.api-key".
// Deliberately generic: any part of the app can store a value here instead of
// putting it in application.properties (which is tracked in git and needs a restart).
//
// Extends BaseEntity, so it gets id, created/updated time, and the soft-delete flag.
@Entity
@Table(
        name = "app_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_setting_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE app_setting SET is_deleted = true WHERE id = ?")
public class AppSetting extends BaseEntity {

    // The lookup key, e.g. "nim.api-key". Unique, and never changed after creation.
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    // The value. Column is "setting_value" because VALUE is a MySQL keyword.
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    // When true, the value is MASKED whenever it is sent to a client.
    @Column(name = "secret", nullable = false)
    @Builder.Default
    private Boolean secret = false;

    // Free text so whoever reads the settings list knows what this is for.
    @Column(name = "description", length = 500)
    private String description;

    // Turn a setting off without deleting it. A disabled setting reads as absent.
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
}
```

- [ ] **Step 2: Create the repository**

```java
package in.healthconnect.setting.repository;

import in.healthconnect.setting.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Read/save application settings. The soft-delete filter comes free from BaseEntity.
@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, Integer> {

    // Look a setting up by its key, e.g. "nim.api-key".
    Optional<AppSetting> findByName(String name);

    boolean existsByName(String name);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw -o compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Write the seed IT that creates the table**

It inserts **no** rows — the credential goes in by hand in Task 6 and is never committed.

```java
package in.healthconnect.setting;

import in.healthconnect.setting.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Creates the app_setting table in REAL MySQL.
//
// The app normally runs with ddl-auto=validate, which does NOT create tables. This test
// overrides it to "update" for this one run so Hibernate builds app_setting with exactly
// the schema it expects. application.properties is not changed.
//
// It deliberately inserts NOTHING: the API key is added by hand and must never be committed.
//
// Run on its own with:
//   ./mvnw test -Dtest=AppSettingSeedIT
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
class AppSettingSeedIT {

    @Autowired
    private AppSettingRepository repository;

    @Test
    void createsAppSettingTable() {
        // Reaching this line at all means the context started and the table validates.
        System.out.println(">>> app_setting rows: " + repository.count());
    }
}
```

- [ ] **Step 5: Run the seed IT against MySQL**

Run: `./mvnw test -Dtest=AppSettingSeedIT`
Expected: `Tests run: 1, Failures: 0` and a line `>>> app_setting rows: 0`

If this fails to connect, MySQL isn't running or `application.properties` points elsewhere — fix that before continuing, because nothing downstream can be verified without it.

- [ ] **Step 6: Confirm the rest of the suite still passes**

Run: `./mvnw -o test -Dtest='SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest,FilterConfigParserTest,WidgetServiceTest,WidgetExecutionServiceTest,KnowledgeBaseServiceTest,BoardServiceTest'`
Expected: `Failures: 0, Errors: 0`

Adding an entity can break every `@SpringBootTest` if the table is missing, so this step
proves Step 5 actually worked before you build anything on top of it.

- [ ] **Step 7: Commit**

```bash
git commit src/main/java/in/healthconnect/setting/entity/AppSetting.java src/main/java/in/healthconnect/setting/repository/AppSettingRepository.java src/test/java/in/healthconnect/setting/AppSettingSeedIT.java -m "feat(setting): add AppSetting entity, repository, and table seed"
```

---

### Task 2: The DTOs, and the masking rule

**Files:**
- Create: `src/main/java/in/healthconnect/setting/dto/request/AppSettingRequest.java`
- Create: `src/main/java/in/healthconnect/setting/dto/response/AppSettingResponse.java`
- Test: `src/test/java/in/healthconnect/setting/dto/response/AppSettingResponseTest.java`

**Interfaces:**
- Consumes: `AppSetting` (Task 1)
- Produces:
  - `AppSettingRequest` with `getName()`, `getValue()`, `getSecret()`, `getDescription()`, `getEnabled()` and matching setters
  - `AppSettingResponse` with `getId()`, `getName()`, `getValue()`, `getSecret()`, `getDescription()`, `getEnabled()`, `getCreatedAt()`, `getUpdatedAt()`
  - `static AppSettingResponse from(AppSetting)` — **masks `value` when `secret` is true**
  - package-private `static String mask(String)`

- [ ] **Step 1: Write the failing test**

```java
package in.healthconnect.setting.dto.response;

import in.healthconnect.setting.entity.AppSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// The masking rule lives in AppSettingResponse.from(...) on purpose: if it lived in the
// controller, any future endpoint could leak a secret by forgetting to call it.
class AppSettingResponseTest {

    private AppSetting setting(String value, boolean secret) {
        AppSetting s = new AppSetting();
        s.setName("nim.api-key");
        s.setValue(value);
        s.setSecret(secret);
        s.setEnabled(true);
        return s;
    }

    @Test
    void nonSecretValuePassesThroughUnchanged() {
        AppSettingResponse response =
                AppSettingResponse.from(setting("qwen/qwen2.5-coder-32b-instruct", false));

        assertEquals("qwen/qwen2.5-coder-32b-instruct", response.getValue());
    }

    @Test
    void longSecretShowsOnlyFirstAndLastFour() {
        // 20 characters: "nvap" + "****" + "3f2a"
        AppSettingResponse response = AppSettingResponse.from(setting("nvapi-abcdefghij3f2a", true));

        assertEquals("nvap****3f2a", response.getValue());
    }

    @Test
    void shortSecretRevealsNothing() {
        // 8 characters or fewer: showing 4 + 4 would show the whole thing
        AppSettingResponse response = AppSettingResponse.from(setting("12345678", true));

        assertEquals("****", response.getValue());
    }

    @Test
    void blankOrNullSecretBecomesNull() {
        assertNull(AppSettingResponse.from(setting("   ", true)).getValue());
        assertNull(AppSettingResponse.from(setting(null, true)).getValue());
    }

    @Test
    void secretFlagIsReportedToTheClient() {
        assertTrue(AppSettingResponse.from(setting("nvapi-abcdefghij3f2a", true)).getSecret());
        assertFalse(AppSettingResponse.from(setting("plain", false)).getSecret());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -o test -Dtest=AppSettingResponseTest`
Expected: FAIL — compilation error, `AppSettingResponse` does not exist.

- [ ] **Step 3: Write the request DTO**

```java
package in.healthconnect.setting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// What the client sends to create or update a setting.
//
// Update reuses this DTO, so a client must still send `name` to pass validation - but the
// service IGNORES it and never renames a stored setting. Other code looks settings up by
// name, so a rename would silently break them (same rule as Widget.code).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotBlank
    private String value;

    // optional; defaults to false when creating
    private Boolean secret;

    @Size(max = 500)
    private String description;

    // optional; defaults to true when creating
    private Boolean enabled;
}
```

- [ ] **Step 4: Write the response DTO with the masking rule**

```java
package in.healthconnect.setting.dto.response;

import in.healthconnect.setting.entity.AppSetting;
import lombok.*;

import java.time.Instant;

// One setting, sent back to the client.
//
// IMPORTANT: when the setting is marked secret, `value` is MASKED here. This is the only
// place masking happens, so it cannot be forgotten by a caller.
// Server-side code that needs the real value uses SettingService.getRequired(name).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettingResponse {

    private Integer id;
    private String name;
    private String value;   // masked when secret is true
    private Boolean secret;
    private String description;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppSettingResponse from(AppSetting setting) {
        boolean secret = Boolean.TRUE.equals(setting.getSecret());
        return AppSettingResponse.builder()
                .id(setting.getId())
                .name(setting.getName())
                .value(secret ? mask(setting.getValue()) : setting.getValue())
                .secret(secret)
                .description(setting.getDescription())
                .enabled(setting.getEnabled())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    // Show just enough to recognise which key is stored, and no more:
    //   nothing to show      -> null
    //   8 characters or less -> "****"   (4 + 4 would reveal the whole value)
    //   longer               -> first 4 + "****" + last 4
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=AppSettingResponseTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git commit src/main/java/in/healthconnect/setting/dto src/test/java/in/healthconnect/setting/dto -m "feat(setting): add setting DTOs with secret masking on read"
```

---

### Task 3: `SettingService`

**Files:**
- Create: `src/main/java/in/healthconnect/setting/service/SettingService.java`
- Test: `src/test/java/in/healthconnect/setting/service/SettingServiceTest.java`

**Interfaces:**
- Consumes: `AppSettingRepository` (Task 1), `AppSettingRequest` / `AppSettingResponse` (Task 2), `in.healthconnect.exception.ResourceNotFoundException`
- Produces — these exact signatures, used by Tasks 4 and 5:
  - `AppSettingResponse create(AppSettingRequest)`
  - `List<AppSettingResponse> list()`
  - `AppSettingResponse get(Integer id)`
  - `AppSettingResponse update(Integer id, AppSettingRequest)`
  - `void delete(Integer id)`
  - `String getRequired(String name)` — the **real, unmasked** value; throws `IllegalStateException` when missing, disabled, or blank
  - `String getOrDefault(String name, String fallback)` — the real value, or `fallback`

- [ ] **Step 1: Write the failing test**

```java
package in.healthconnect.setting.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.entity.AppSetting;
import in.healthconnect.setting.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for SettingService. The repository is mocked, so no database is needed.
//
// The important rule under test: getRequired returns the REAL value (server-side reader),
// while anything that goes back to a client goes through AppSettingResponse, which masks.
class SettingServiceTest {

    private final AppSettingRepository repository = mock(AppSettingRepository.class);
    private final SettingService service = new SettingService(repository);

    private AppSettingRequest request(String name, String value) {
        AppSettingRequest r = new AppSettingRequest();
        r.setName(name);
        r.setValue(value);
        return r;
    }

    private AppSetting stored(String name, String value, boolean enabled) {
        AppSetting s = new AppSetting();
        s.setName(name);
        s.setValue(value);
        s.setEnabled(enabled);
        s.setSecret(false);
        return s;
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("nim.api-key")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request("nim.api-key", "nvapi-x")));
    }

    @Test
    void createAppliesDefaults() {
        when(repository.existsByName(any())).thenReturn(false);
        when(repository.save(any(AppSetting.class))).thenAnswer(call -> call.getArgument(0));

        AppSettingResponse response =
                service.create(request("nim.model", "qwen/qwen2.5-coder-32b-instruct"));

        assertTrue(response.getEnabled());   // defaults to on
        assertFalse(response.getSecret());   // defaults to not-secret
        assertEquals("qwen/qwen2.5-coder-32b-instruct", response.getValue()); // not secret -> not masked
        verify(repository).save(any(AppSetting.class));
    }

    @Test
    void getRequiredReturnsTheRealUnmaskedValue() {
        AppSetting secretRow = stored("nim.api-key", "nvapi-abcdefghij3f2a", true);
        secretRow.setSecret(true);
        when(repository.findByName("nim.api-key")).thenReturn(Optional.of(secretRow));

        assertEquals("nvapi-abcdefghij3f2a", service.getRequired("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenMissing() {
        when(repository.findByName("nim.api-key")).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getRequired("nim.api-key"));
        assertTrue(error.getMessage().contains("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenDisabled() {
        when(repository.findByName("nim.api-key"))
                .thenReturn(Optional.of(stored("nim.api-key", "nvapi-x", false)));

        assertThrows(IllegalStateException.class, () -> service.getRequired("nim.api-key"));
    }

    @Test
    void getRequiredThrowsWhenValueIsBlank() {
        when(repository.findByName("nim.api-key"))
                .thenReturn(Optional.of(stored("nim.api-key", "   ", true)));

        assertThrows(IllegalStateException.class, () -> service.getRequired("nim.api-key"));
    }

    @Test
    void getOrDefaultFallsBackWhenMissing() {
        when(repository.findByName("nim.model")).thenReturn(Optional.empty());

        assertEquals("fallback", service.getOrDefault("nim.model", "fallback"));
    }

    @Test
    void getOrDefaultReturnsTheValueWhenPresent() {
        when(repository.findByName("nim.model"))
                .thenReturn(Optional.of(stored("nim.model", "meta/llama-3.3-70b-instruct", true)));

        assertEquals("meta/llama-3.3-70b-instruct", service.getOrDefault("nim.model", "fallback"));
    }

    @Test
    void updateChangesTheValueButNeverTheName() {
        when(repository.findById(5)).thenReturn(Optional.of(stored("nim.model", "old-model", true)));
        when(repository.save(any(AppSetting.class))).thenAnswer(call -> call.getArgument(0));

        AppSettingResponse response = service.update(5, request("renamed", "new-model"));

        assertEquals("nim.model", response.getName());  // the name in the request is ignored
        assertEquals("new-model", response.getValue());
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
    }

    @Test
    void deleteRemoves() {
        AppSetting existing = stored("nim.model", "x", true);
        when(repository.findById(3)).thenReturn(Optional.of(existing));

        service.delete(3);

        verify(repository).delete(existing);  // soft delete happens via @SQLDelete
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw -o test -Dtest=SettingServiceTest`
Expected: FAIL — compilation error, `SettingService` does not exist.

- [ ] **Step 3: Write the service**

```java
package in.healthconnect.setting.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.entity.AppSetting;
import in.healthconnect.setting.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Application settings: plain CRUD for clients, plus two readers for server-side code.
//
// The split matters. create/list/get/update return AppSettingResponse, which MASKS secret
// values. getRequired/getOrDefault return the RAW value and are only ever called from
// inside the server (e.g. NimQueryGenerator reading the API key).
@Service
@RequiredArgsConstructor
public class SettingService {

    private final AppSettingRepository repository;

    // ---------- CRUD (masked on the way out) ----------

    public AppSettingResponse create(AppSettingRequest request) {
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException(
                    "Setting '" + request.getName() + "' already exists.");
        }
        AppSetting setting = new AppSetting();
        setting.setName(request.getName());
        setting.setValue(request.getValue());
        setting.setSecret(request.getSecret() == null ? Boolean.FALSE : request.getSecret());
        setting.setDescription(request.getDescription());
        setting.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return AppSettingResponse.from(repository.save(setting));
    }

    // Returns every non-deleted row, INCLUDING disabled ones, so a disabled setting stays
    // visible and can be switched back on.
    @Transactional(readOnly = true)
    public List<AppSettingResponse> list() {
        return repository.findAll().stream()
                .map(AppSettingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AppSettingResponse get(Integer id) {
        return AppSettingResponse.from(find(id));
    }

    public AppSettingResponse update(Integer id, AppSettingRequest request) {
        AppSetting setting = find(id);
        // The name is deliberately NOT updated - other code looks settings up by name.
        setting.setValue(request.getValue());
        setting.setDescription(request.getDescription());
        if (request.getSecret() != null) {
            setting.setSecret(request.getSecret());
        }
        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }
        return AppSettingResponse.from(repository.save(setting));
    }

    public void delete(Integer id) {
        repository.delete(find(id)); // soft delete via @SQLDelete
    }

    // ---------- server-side readers (REAL value, never masked, never serialized) ----------

    // The value, or a clear error naming the setting that needs configuring.
    @Transactional(readOnly = true)
    public String getRequired(String name) {
        String value = rawValue(name);
        if (value == null) {
            throw new IllegalStateException(
                    "Setting '" + name + "' is not configured. Add it under /api/v1/settings.");
        }
        return value;
    }

    // The value, or the fallback when it is missing/disabled/blank.
    @Transactional(readOnly = true)
    public String getOrDefault(String name, String fallback) {
        String value = rawValue(name);
        return value == null ? fallback : value;
    }

    // null when the row is missing, switched off, or holds nothing useful.
    private String rawValue(String name) {
        return repository.findByName(name)
                .filter(setting -> Boolean.TRUE.equals(setting.getEnabled()))
                .map(AppSetting::getValue)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    private AppSetting find(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found: " + id));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=SettingServiceTest`
Expected: `Tests run: 11, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git commit src/main/java/in/healthconnect/setting/service src/test/java/in/healthconnect/setting/service -m "feat(setting): add SettingService with masked CRUD and raw server-side readers"
```

---

### Task 4: `AppSettingController`

Thin, like the other controllers in this project — it calls the service and wraps the result. Verified live rather than by unit test, matching how `AiKnowledgeController` was handled.

**Files:**
- Create: `src/main/java/in/healthconnect/setting/controller/AppSettingController.java`

**Interfaces:**
- Consumes: `SettingService` (Task 3), `AppSettingRequest` / `AppSettingResponse` (Task 2), `in.healthconnect.wrapper.ApiResponse`
- Produces: HTTP endpoints under `/api/v1/settings`

- [ ] **Step 1: Write the controller**

```java
package in.healthconnect.setting.controller;

import in.healthconnect.setting.dto.request.AppSettingRequest;
import in.healthconnect.setting.dto.response.AppSettingResponse;
import in.healthconnect.setting.service.SettingService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Manage application settings (API keys, model names, anything configurable).
//
// Secret values come back MASKED - the masking is done by AppSettingResponse, not here.
// No try/catch: GlobalExceptionHandler maps IllegalArgumentException -> 400 and
// ResourceNotFoundException -> 404.
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class AppSettingController {

    private final SettingService settingService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppSettingResponse>> create(
            @RequestBody @Valid AppSettingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(settingService.create(request), "Setting created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppSettingResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(settingService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppSettingResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(settingService.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AppSettingResponse>> update(
            @PathVariable Integer id,
            @RequestBody @Valid AppSettingRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(settingService.update(id, request), "Setting updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        settingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Setting deleted"));
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw -o compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Start the app and check the endpoints live**

Start it in one terminal: `./mvnw spring-boot:run`

Then, in another, create a non-secret setting and read it back:

```bash
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" -d '{"name":"nim.model","value":"qwen/qwen2.5-coder-32b-instruct","description":"NIM model for text-to-SQL"}'
```
Expected: HTTP 201, `"value":"qwen/qwen2.5-coder-32b-instruct"` — not secret, so not masked.

```bash
curl http://localhost:8080/api/v1/settings
```
Expected: a list containing that one row.

Now prove masking works, using a throwaway value (**not** your real key):

```bash
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" -d '{"name":"masking.test","value":"nvapi-abcdefghij3f2a","secret":true,"description":"temporary masking check"}'
```
Expected: HTTP 201 with `"value":"nvap****3f2a"`.

And prove the duplicate rule:

```bash
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" -d '{"name":"masking.test","value":"anything","secret":true}'
```
Expected: HTTP 400, message `Setting 'masking.test' already exists.`

Delete the throwaway row (use the id from the create response):

```bash
curl -X DELETE http://localhost:8080/api/v1/settings/{id}
```
Expected: HTTP 200, `Setting deleted`.

- [ ] **Step 4: Stop the app and commit**

```bash
git commit src/main/java/in/healthconnect/setting/controller -m "feat(setting): expose /api/v1/settings CRUD"
```

---

### Task 5: `NimQueryGenerator` replaces Gemini

**Files:**
- Create: `src/main/java/in/healthconnect/widgetengine/prompt/NimQueryGenerator.java`
- Test: `src/test/java/in/healthconnect/widgetengine/prompt/NimQueryGeneratorTest.java`
- Delete: `src/main/java/in/healthconnect/widgetengine/prompt/GeminiQueryGenerator.java`
- Delete: `src/main/java/in/healthconnect/widgetengine/prompt/NoopQueryGenerator.java`
- Delete: `src/test/java/in/healthconnect/widgetengine/prompt/GeminiQueryGeneratorTest.java`
- Delete: `src/test/java/in/healthconnect/widgetengine/prompt/NoopQueryGeneratorTest.java`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: `QueryGenerator` (existing interface — `String generateSql(String prompt)`), `SettingService.getRequired` / `getOrDefault` (Task 3), `tools.jackson.databind.ObjectMapper`
- Produces: `NimQueryGenerator implements QueryGenerator`, plus package-private `String extractText(String responseJson)` for the test

- [ ] **Step 1: Check nothing else references the classes being deleted**

Run: `grep -rn "NoopQueryGenerator\|GeminiQueryGenerator\|gemini" src/ --include=*.java --include=*.properties`
Expected: hits only in the four files being deleted and the three `gemini.*` property lines. If anything else appears (for example a config class or a doc-comment reference), fix that reference in this task rather than leaving a broken build.

- [ ] **Step 2: Write the failing test**

```java
package in.healthconnect.widgetengine.prompt;

import in.healthconnect.setting.service.SettingService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Tests for NimQueryGenerator - the parts we can check WITHOUT calling the network:
//   1. no API key configured => fail clearly, naming the setting to add;
//   2. read the SQL out of NIM's OpenAI-compatible JSON response;
//   3. a malformed response fails loudly instead of returning null.
//
// The real HTTP call is exercised by hand once a key is in the settings table.
class NimQueryGeneratorTest {

    private final SettingService settingService = mock(SettingService.class);
    private final NimQueryGenerator generator =
            new NimQueryGenerator(JsonMapper.builder().build(), settingService);

    @Test
    void failsClearlyWhenApiKeyIsNotConfigured() {
        when(settingService.getRequired("nim.api-key"))
                .thenThrow(new IllegalStateException(
                        "Setting 'nim.api-key' is not configured. Add it under /api/v1/settings."));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> generator.generateSql("count doctors"));

        assertTrue(error.getMessage().contains("nim.api-key"));
    }

    @Test
    void extractsSqlTextFromNimResponse() {
        String responseJson =
                "{ \"choices\": [ { \"index\": 0, \"message\": { \"role\": \"assistant\", " +
                "\"content\": \"SELECT count(*) FROM doctors\" } } ] }";

        assertEquals("SELECT count(*) FROM doctors", generator.extractText(responseJson));
    }

    @Test
    void throwsWhenResponseHasNoContent() {
        String responseJson = "{ \"choices\": [ { \"message\": { \"role\": \"assistant\" } } ] }";

        assertThrows(RuntimeException.class, () -> generator.extractText(responseJson));
    }

    @Test
    void throwsWhenChoicesIsEmpty() {
        assertThrows(RuntimeException.class, () -> generator.extractText("{ \"choices\": [] }"));
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./mvnw -o test -Dtest=NimQueryGeneratorTest`
Expected: FAIL — compilation error, `NimQueryGenerator` does not exist.

- [ ] **Step 4: Write the generator**

```java
package in.healthconnect.widgetengine.prompt;

import in.healthconnect.setting.service.SettingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

// The REAL AI. Sends the prompt to NVIDIA NIM and returns the SQL it writes.
//
// NIM speaks the OpenAI-compatible chat API, so this is a plain POST to /chat/completions
// with a Bearer token.
//
// Credentials come from the app_setting table (NOT application.properties), so they can be
// changed over HTTP without a restart and are never committed to git. If no key is
// configured, getRequired throws a clear error naming the setting to add.
//
// Whatever comes back is still untrusted: SqlDraftService cleans it (SqlCleaner) and
// SqlSafetyGuard rejects anything that is not a single SELECT.
@Component
public class NimQueryGenerator implements QueryGenerator {

    static final String KEY_SETTING = "nim.api-key";
    static final String MODEL_SETTING = "nim.model";
    static final String BASE_URL_SETTING = "nim.base-url";

    static final String DEFAULT_MODEL = "qwen/qwen2.5-coder-32b-instruct";
    static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";

    private final ObjectMapper objectMapper;
    private final SettingService settingService;

    public NimQueryGenerator(ObjectMapper objectMapper, SettingService settingService) {
        this.objectMapper = objectMapper;
        this.settingService = settingService;
    }

    @Override
    public String generateSql(String prompt) {
        // Only the key is mandatory; model and base URL fall back to sensible defaults.
        String apiKey = settingService.getRequired(KEY_SETTING);
        String model = settingService.getOrDefault(MODEL_SETTING, DEFAULT_MODEL);
        String baseUrl = settingService.getOrDefault(BASE_URL_SETTING, DEFAULT_BASE_URL);

        // temperature 0 + low top_p => as predictable as possible: the same question
        // should give the same SQL. PromptBuilder already bakes the RULES into the prompt,
        // so a single user message is all that is needed.
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0,
                "top_p", 0.1,
                "max_tokens", 1024,
                "stream", false
        );

        String response = RestClient.create()
                .post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return extractText(response);
    }

    // Read choices[0].message.content out of NIM's JSON reply.
    String extractText(String responseJson) {
        JsonNode content = objectMapper.readTree(responseJson)
                .path("choices").path(0)
                .path("message").path("content");

        if (content.isMissingNode() || content.isNull()) {
            throw new RuntimeException("NIM response did not contain any SQL text.");
        }
        return content.asString();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=NimQueryGeneratorTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Delete the replaced classes and their tests**

```bash
git rm src/main/java/in/healthconnect/widgetengine/prompt/GeminiQueryGenerator.java src/main/java/in/healthconnect/widgetengine/prompt/NoopQueryGenerator.java src/test/java/in/healthconnect/widgetengine/prompt/GeminiQueryGeneratorTest.java src/test/java/in/healthconnect/widgetengine/prompt/NoopQueryGeneratorTest.java
```

If `git rm` reports the files are not tracked (the `widgetengine` tree may still be staged-but-uncommitted), use `git rm --cached` on them and delete the files from disk instead.

- [ ] **Step 7: Remove the Gemini config**

Delete these three lines and their comment header from `src/main/resources/application.properties`:

```properties
# --- Gemini (PROMPT module: plain English -> SQL) ---
# Paste your Gemini API key below and UNCOMMENT it to turn on AI query generation.
# gemini.api-key=YOUR_GEMINI_API_KEY
gemini.model=gemini-2.5-flash
gemini.base-url=https://generativelanguage.googleapis.com
```

Nothing replaces them — NIM configuration lives in `app_setting`.

- [ ] **Step 8: Run the whole widget + setting suite**

Run: `./mvnw -o test -Dtest='SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest,FilterConfigParserTest,WidgetServiceTest,WidgetExecutionServiceTest,KnowledgeBaseServiceTest,BoardServiceTest,PromptBuilderTest,SqlCleanerTest,SqlDraftServiceTest,NimQueryGeneratorTest,SettingServiceTest,AppSettingResponseTest'`
Expected: `Failures: 0, Errors: 0`

Every one of those classes exists today (the two deleted in Step 6 are deliberately absent from the list). `SqlDraftServiceTest` uses a **mock** `QueryGenerator`, so deleting the no-op does not affect it — if it fails here, Step 1's grep missed a reference.

- [ ] **Step 9: Verify the app still starts**

Run: `./mvnw spring-boot:run`
Expected: the context starts with no `NoUniqueBeanDefinitionException` and no missing-bean error. `NimQueryGenerator` is now the only `QueryGenerator`. Stop the app.

- [ ] **Step 10: Commit**

```bash
git commit src/main/java/in/healthconnect/widgetengine/prompt src/test/java/in/healthconnect/widgetengine/prompt src/main/resources/application.properties -m "feat(prompt): replace Gemini with NVIDIA NIM, credentials from app_setting"
```

---

### Task 6: Live end-to-end verification, then the journal

The journal is written **after** the live run, on purpose: it records what actually happened, including whether NIM accepted `temperature: 0`.

**Files:**
- Modify: `src/main/java/in/healthconnect/widgetengine/widgetdevelopment.md`
- Modify: `docs/superpowers/specs/2026-08-27-nim-query-generation-design.md` (only if the temperature assumption turns out wrong)

- [ ] **Step 1: Insert the real API key**

Start the app (`./mvnw spring-boot:run`), then — with the user's actual key, which must not be pasted into any file:

```bash
curl -X POST http://localhost:8080/api/v1/settings -H "Content-Type: application/json" -d '{"name":"nim.api-key","value":"nvapi-REPLACE_WITH_REAL_KEY","secret":true,"description":"NVIDIA NIM API key"}'
```
Expected: HTTP 201, with `value` already masked in the response.

- [ ] **Step 2: Confirm the key is not readable back**

```bash
curl http://localhost:8080/api/v1/settings
```
Expected: the `nim.api-key` row shows a masked value like `nvap****xxxx`, never the full key. **If the full key comes back, stop** — the masking is broken and nothing else matters until it's fixed.

- [ ] **Step 3: Generate a query with the AI**

```bash
curl -X POST http://localhost:8080/api/v1/ai/generate -H "Content-Type: application/json" -d '{"question":"list all cardiology doctors"}'
```

Expected: HTTP 200 with a `GeneratedQueryResponse` — `widgetId`, `code`, `question`, `status: "DRAFT"`, and a `sql` field containing a single SELECT that joins `doctors` → `doctor_specialties_map` → `specialties` and filters `s.name = 'Cardiology'` and `is_deleted = false`.

Record the actual SQL returned — it goes in the journal.

**If it fails, read the error before changing code:**

| Error | Meaning | Fix |
|---|---|---|
| `Setting 'nim.api-key' is not configured` | Step 1 didn't take, or the row is disabled | Re-check `GET /api/v1/settings` |
| 400/401 from NIM | Bad or expired key | Update the row via `PUT /api/v1/settings/{id}` |
| 400 mentioning `temperature` | NIM rejects `0` for this model | Change `"temperature", 0` to `"temperature", 0.01` in `NimQueryGenerator`, and **update the spec's "Unverified assumption" note to record it** |
| 404 mentioning the model | Model name wrong or unavailable on your account | `PUT` a different value into the `nim.model` row — no code change |
| `NIM response did not contain any SQL text.` | Unexpected response shape | Log the raw response and compare against `extractText` |
| 400 from `SqlSafetyGuard` | The model returned prose or non-SELECT | Try `meta/llama-3.3-70b-instruct` via the `nim.model` row before touching code |

- [ ] **Step 4: Run the generated draft**

Using the `code` from Step 3:

```bash
curl -X POST http://localhost:8080/api/v1/widgets/{code}/data -H "Content-Type: application/json" -d '{}'
```
Expected: HTTP 200 with a `rows` array. A DRAFT is runnable — `WidgetExecutionService` checks `enabled`, not `status`.

If this returns `Unable to run this widget`, the SQL passed the safety gate but references something that doesn't exist. Check `SELECT * FROM widget_execution_log ORDER BY executed_at DESC LIMIT 5;` for the real error, then improve the `hints` in `ai_knowledge` — this is a knowledge-base fix, not a code fix.

- [ ] **Step 5: Approve it**

```bash
curl -X PUT http://localhost:8080/api/v1/widgets/{widgetId}/approve
```
Expected: HTTP 200, status becomes `APPROVED`. Stop the app.

- [ ] **Step 6: Correct Step 15 in the journal**

In `src/main/java/in/healthconnect/widgetengine/widgetdevelopment.md`, add this note directly under the `## Step 15 — GeminiQueryGenerator (the real AI call)` heading, so the journal doesn't describe a deleted class:

```markdown
> **Superseded by Step 17.** `GeminiQueryGenerator` was replaced by `NimQueryGenerator`
> (NVIDIA NIM) and deleted, along with `NoopQueryGenerator`. The section below is kept as
> a record of how the first provider was wired — the `@ConditionalOnProperty` trick no
> longer applies, because the credential now lives in the `app_setting` table rather than
> in `application.properties`.
```

Also update the roadmap table at the top of Part 2 by appending two rows:

```markdown
| 16 | `app_setting` table — settings + secret masking (test-first) | ✅ Done |
| 17 | `NimQueryGenerator` — the real AI call (NVIDIA NIM) | ✅ Done |
```

- [ ] **Step 7: Write Step 16 in the journal**

Append to `widgetdevelopment.md`, in the same voice as the surrounding steps — plain English, RED → GREEN, and a rebuild checklist:

```markdown
## Step 16 — `app_setting` (settings in the database, not in a file)

**Goal:** stop keeping API keys in `application.properties`. That file is tracked in git,
so a key committed there is permanent, and changing it needs a restart. A small generic
settings table fixes both.

### The table — [`AppSetting`](../../../setting/entity/AppSetting.java)
One row per setting: `name` (unique, e.g. `nim.api-key`), `value`, `secret`,
`description`, `enabled`. Extends `BaseEntity`, so it gets soft delete for free.

> The column is called `setting_value`, not `value` — `VALUE` is a MySQL keyword, and
> sidestepping it is cheaper than quoting it everywhere.

### The one rule that matters: masking
[`AppSettingResponse.from(...)`](../../../setting/dto/response/AppSettingResponse.java)
masks the value when `secret` is true — nothing to show → `null`; 8 characters or fewer →
`****`; longer → first 4 + `****` + last 4. **Masking lives in the DTO, not the
controller**, so no future endpoint can leak a secret by forgetting to call it.

Server-side code that needs the *real* value calls
[`SettingService`](../../../setting/service/SettingService.java)`.getRequired(name)`,
which returns the raw string and throws a clear error — naming the setting — when the row
is missing, disabled, or blank. `getOrDefault(name, fallback)` is the soft version.

### RED → GREEN
- `AppSettingResponseTest` (5 tests): the four masking cases + the `secret` flag.
- `SettingServiceTest` (11 tests, mock repository): duplicate name rejected, defaults
  applied, `getRequired` returns the **unmasked** value, throws when missing/disabled/blank,
  `getOrDefault` falls back, update never renames, 404 on missing id, delete is soft.

Stub first → red; real code → green.

### Endpoints — [`AppSettingController`](../../../setting/controller/AppSettingController.java)
`/api/v1/settings` — POST / GET / GET `{id}` / PUT `{id}` / DELETE `{id}`.

### The table itself
[`AppSettingSeedIT`](../../../../../test/java/in/healthconnect/setting/AppSettingSeedIT.java)
creates `app_setting` with `ddl-auto=update` for that one run, the same trick as Step 10.
It inserts **nothing** — the API key is added by hand and must never be committed.

```bash
./mvnw test -Dtest=AppSettingSeedIT
```

### Rebuild-from-scratch checklist for Step 16
1. `AppSetting` entity (unique `name`, `setting_value` column, `secret` flag) + repository.
2. Seed IT to create the table; run it before starting the app, or `ddl-auto=validate` fails.
3. `AppSettingResponseTest` → stub → red → implement `mask(...)` → green.
4. `SettingServiceTest` with a mock repository → stub → red → implement CRUD +
   `getRequired`/`getOrDefault` → green.
5. Thin controller under `/api/v1/settings`.
```

- [ ] **Step 8: Write Step 17 in the journal**

Append, filling the SQL block with what the model **actually** returned in Step 3 — not an idealised version:

```markdown
## Step 17 — `NimQueryGenerator` (the real AI call, on NVIDIA NIM)

**Goal:** replace the never-configured Gemini call with NVIDIA NIM, and take the
credential out of `application.properties`.

### Why NIM
NIM exposes an **OpenAI-compatible** `/chat/completions` endpoint, so the call is an
ordinary `RestClient` POST with a `Bearer` token. It hosts code-specialised open models;
we default to `qwen/qwen2.5-coder-32b-instruct`, which is strong at text-to-SQL.

> **Avoid reasoning models** here (`deepseek-ai/deepseek-r1`, Nemotron). They emit a
> `<think>…</think>` trace before the answer. `SqlCleaner` strips markdown fences and a
> trailing `;` but not think-blocks, so the trace would reach `SqlSafetyGuard`, fail the
> "starts with SELECT" rule, and break every generation.

### The class — [`NimQueryGenerator`](./prompt/NimQueryGenerator.java)
1. read `nim.api-key` from settings (`getRequired` — fails clearly when unset);
2. read `nim.model` and `nim.base-url` with defaults, so only the key is mandatory;
3. POST `{base-url}/chat/completions` with one `user` message, `temperature 0`,
   `top_p 0.1`, `max_tokens 1024` — `PromptBuilder` already bakes the RULES in, so no
   separate system message is needed;
4. `extractText` reads `choices[0].message.content`, throwing if it is missing.

### The wiring change
Gemini used `@ConditionalOnProperty(prefix = "gemini", name = "api-key")` so the bean only
existed when a key was set in a file. A credential in the database can't be seen by that
annotation, so `NimQueryGenerator` is an ordinary `@Component` and the "no key" case is
handled at call time by `getRequired`. That made `NoopQueryGenerator` unreachable, so it
was deleted too — the clear error message it existed to provide now comes from the setting
lookup, and names the exact setting to add.

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
  -d '{"question":"list all cardiology doctors"}'

# 3. run the draft (DRAFT widgets are runnable - execution checks enabled, not status)
curl -X POST http://localhost:8080/api/v1/widgets/{code}/data \
  -H "Content-Type: application/json" -d '{}'

# 4. approve it
curl -X PUT http://localhost:8080/api/v1/widgets/{id}/approve
```

### What it actually returned

<!-- Paste the real SQL from the live run in Step 3 here. -->

### Changing the model costs no code
The model is a settings row. If the SQL is weak on joins, `PUT` a different value into
`nim.model` (e.g. `meta/llama-3.3-70b-instruct`) or improve the `hints` in `ai_knowledge`.
That is the payoff of keeping the AI behind the `QueryGenerator` interface.

### Rebuild-from-scratch checklist for Step 17
1. `NimQueryGeneratorTest` for the key guard + response parsing → stub → red.
2. Implement `generateSql` (RestClient POST, temperature 0) + `extractText` → green.
3. Delete `GeminiQueryGenerator`, `NoopQueryGenerator`, and their tests.
4. Remove the three `gemini.*` lines from `application.properties`.
```

- [ ] **Step 9: Commit**

```bash
git commit src/main/java/in/healthconnect/widgetengine/widgetdevelopment.md docs/superpowers -m "docs: record the settings table and the NIM generator in the build journal"
```

---

## Done when

- `./mvnw -o test` passes for the widget + setting suites.
- `GET /api/v1/settings` shows `nim.api-key` **masked**.
- `POST /api/v1/ai/generate` returns a DRAFT with real, runnable MySQL.
- `widgetdevelopment.md` describes Steps 16 and 17 and no longer presents Gemini as live.
- No API key appears anywhere in the repo — check with `grep -rn "nvapi-" . --exclude-dir=.git` before the final commit; the only hits should be the placeholder `nvapi-REPLACE_WITH_REAL_KEY` and the fake `nvapi-abcdefghij3f2a` used in tests.

## Next round (not this plan)

The frontend: prompt field in the Add-widget drawer → preview SQL and 5 rows → **Add to board** approves and adds. Decisions are recorded at the end of the spec.
