# Board Builder — Plan 1: the data model

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move boards from `[{widgetId, width}]` to a real grid model `[{widgetId, x, y, w, h}]` and render them through `react-grid-layout`, **without changing a single thing the user can see or do.**

**Architecture:** The `layout` column stays `TEXT` JSON, so there is no schema migration. `BoardService` normalizes legacy items **on read** — filling in grid coordinates by flow-packing the old `width` into 3-column rows — so existing boards open unchanged and the first Save rewrites them in the new shape. On the frontend, `BoardView` (an antd `Row`/`Col`) is replaced by `BoardGrid` (a static `react-grid-layout`), which Plan 2 later makes draggable by flipping two props.

**Tech Stack:** Spring Boot 4.0.7 · Java 17 · MySQL · Lombok · JUnit 5 · Mockito · **Jackson 3** — React 19 · Vite 8 · antd 6 · TanStack Query 5 · **react-grid-layout 2.2.4**

**Spec:** `docs/superpowers/specs/2026-08-27-board-builder-design.md`

## Global Constraints

- **Two repos.** Backend `D:\HMS\healthconnect`, frontend `D:\HMS\healtconnectfe`. Every task says which.
- **Jackson 3.** `JsonNode` / `ObjectMapper` live in **`tools.jackson.databind`**; annotations stay at `com.fasterxml.jackson.annotation`. Tests build a mapper with `JsonMapper.builder().build()`.
- **Grid constants, defined once:** `COLS = 3`, `DEFAULT_HEIGHT = 4` (backend, `BoardService`) and `COLS = 3`, `DEFAULT_H = 4`, `ROW_HEIGHT = 80` (frontend, `boardLayout.js`).
- **The invariant that matters:** `x + w <= 3`. Enforced by bean validation on the request DTO **and** by the grid's `cols={3}`.
- **No visible change in this plan.** Adding, removing and width-changing a widget must work exactly as they do today when you finish. If something the user can do stops working, the task is not done.
- **Controllers stay thin**, results wrapped in `ApiResponse`, no try/catch — `GlobalExceptionHandler` maps `IllegalArgumentException` → 400, `ResourceNotFoundException` → 404, bean validation → 400.
- Run backend commands from `D:\HMS\healthconnect` with the **Bash** tool so `./mvnw` works.

### Deliberate sequencing choice vs the spec

The spec says the `1 / 2 / 3` Segmented control is **removed**. This plan **keeps it**, rewired to write `w` instead of `width`. Removing it here would leave an intermediate state with no way to change a widget's width at all, because dragging doesn't arrive until Plan 2. Plan 2 deletes it in the same change that adds resize handles. The end state matches the spec; only the order differs.

### The user's app is already running

A dev server is on **5173** and a Spring Boot app with **devtools** is on **8080** — both started by the user, not by you. `./mvnw compile` hot-reloads the running backend; Vite hot-reloads the frontend. **Do not start a second backend on 8080** (it will fail with "port already in use") and do not `preview_start` a second Vite server. To open a browser tab, use `preview_start` with `{url: "http://localhost:5173"}`.

### The user's board is live data

Board "Rustam board" holds **14 widgets** in the legacy shape. It is the acceptance test for this whole plan. Do not delete it, and do not save over it until Task 3 passes.

---

## File Structure

**Backend — `D:\HMS\healthconnect`**

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/in/healthconnect/widgetengine/service/BoardService.java` | modify | `LayoutItem` record, `normalize(...)`, read/write |
| `src/main/java/in/healthconnect/widgetengine/dto/request/SaveBoardLayoutRequest.java` | modify | `Item` gains `x/y/w/h` + the `x + w <= 3` rule |
| `src/main/java/in/healthconnect/widgetengine/dto/response/BoardResponse.java` | modify | `Item` gains `x/y/w/h`, loses `width` |
| `src/main/java/in/healthconnect/widgetengine/dto/response/WidgetSummaryResponse.java` | modify | gains `status` |
| `src/test/java/in/healthconnect/widgetengine/service/BoardServiceTest.java` | modify | normalization + save-shape tests |

**Frontend — `D:\HMS\healtconnectfe`**

| File | Change | Responsibility |
|---|---|---|
| `src/features/dashboard/boardLayout.js` | create | Grid constants and the three pure layout functions |
| `src/features/dashboard/BoardGrid.jsx` | create | Static `react-grid-layout` render of a board |
| `src/features/dashboard/BoardView.jsx` | delete | Replaced by `BoardGrid` |
| `src/features/dashboard/DashboardPage.jsx` | modify | New save payload; uses `boardLayout` helpers |
| `src/features/dashboard/WidgetCard.jsx` | modify | Charts made resizable; card fills its cell |

---

### Task 1: Spike — does `react-grid-layout` work on React 19?

Throwaway. Nothing from this task is committed. Its only output is an answer and three confirmed facts.

**Why first:** v2.2.4's peer deps are open (`react >= 16.3.0`), which does not prove React 19 support, and 1.x relied on `findDOMNode`, which React 19 removed. Every later task assumes this library works. Find out in twenty minutes, not after `BoardGrid` is written.

**Files (all deleted again in Step 5):**
- Create: `D:\HMS\healtconnectfe\src\features\dashboard\RglSpike.jsx`
- Modify: `D:\HMS\healtconnectfe\src\features\dashboard\DashboardPage.jsx` (two temporary lines)

**Interfaces:**
- Produces: a decision, plus three facts Task 4 needs — the **import shape**, the **CSS import paths**, and whether **`WidthProvider`** works.

- [ ] **Step 1: Install**

Run in `D:\HMS\healtconnectfe`:

```bash
npm install react-grid-layout@^2.2.4
```

Expected: installs without an `ERESOLVE` peer-dependency error. If npm refuses because of React 19, **stop and report** — that alone is a fail, and the fallback is `@dnd-kit`.

- [ ] **Step 2: Write the spike component**

Create `src/features/dashboard/RglSpike.jsx`:

```jsx
import GridLayout, { WidthProvider } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

// THROWAWAY. Proves react-grid-layout drags and resizes on React 19. Delete after checking.
const Grid = WidthProvider(GridLayout)

const layout = [
  { i: 'a', x: 0, y: 0, w: 1, h: 2 },
  { i: 'b', x: 1, y: 0, w: 1, h: 2 },
  { i: 'c', x: 2, y: 0, w: 1, h: 2 },
]

export default function RglSpike() {
  return (
    <Grid className="layout" layout={layout} cols={3} rowHeight={80} margin={[16, 16]}>
      <div key="a" style={{ background: '#1677ff', color: '#fff', padding: 8 }}>A</div>
      <div key="b" style={{ background: '#52c41a', color: '#fff', padding: 8 }}>B</div>
      <div key="c" style={{ background: '#faad14', color: '#fff', padding: 8 }}>C</div>
    </Grid>
  )
}
```

If the import on line 1 fails, try `import { WidthProvider, default as GridLayout } from 'react-grid-layout'`, and if that also fails inspect `node_modules/react-grid-layout/package.json` for its `exports` field. **Record whichever form works** — Task 4 uses it verbatim.

- [ ] **Step 3: Render it temporarily**

In `src/features/dashboard/DashboardPage.jsx`, add the import at the top:

```jsx
import RglSpike from './RglSpike.jsx'
```

and put this as the first child inside the outer `<Card ...>`, immediately before `{!boardId ? (`:

```jsx
<RglSpike />
```

- [ ] **Step 4: Check it in the browser**

Open a tab on the already-running dev server (do **not** start a second one):

`preview_start` with `{url: "http://localhost:5173"}`

Then:
1. `read_console_messages` with `onlyErrors: true` — expected: **no** error mentioning `findDOMNode`, `Cannot read properties of null`, or `react-grid-layout`.
2. Confirm three boxes render side by side, each about 160px tall.
3. Drag box A onto box C's position — the others should shuffle out of the way.
4. Drag the bottom-right corner of box B — it should resize.

**Pass:** all four hold. **Fail:** any console error from the library, or drag/resize does nothing. On fail, **stop and report** — do not continue; the fallback is `@dnd-kit`, which changes Tasks 4 and 5 substantially.

- [ ] **Step 5: Remove the spike**

```bash
rm src/features/dashboard/RglSpike.jsx
```

Then remove the two lines you added to `DashboardPage.jsx` (the `import RglSpike` line and the `<RglSpike />` element). Confirm nothing is left:

```bash
grep -rn "RglSpike" src/ || echo "CLEAN"
```

Expected: `CLEAN`.

- [ ] **Step 6: Commit only the dependency**

```bash
git add package.json package-lock.json
git commit package.json package-lock.json -m "build: add react-grid-layout for the board grid"
```

> `package.json` and `package-lock.json` already carry unrelated modifications from the user. Committing them here includes those. If that is not wanted, skip this commit and leave the dependency uncommitted — later tasks work either way.

---

### Task 2: Backend — grid coordinates and normalization

**Files:**
- Modify: `D:\HMS\healthconnect\src\main\java\in\healthconnect\widgetengine\service\BoardService.java`
- Test: `D:\HMS\healthconnect\src\test\java\in\healthconnect\widgetengine\service\BoardServiceTest.java`

**Interfaces:**
- Consumes: `BoardRepository`, `WidgetRepository`, `tools.jackson.databind.ObjectMapper` (all already injected)
- Produces:
  - `public record LayoutItem(Integer widgetId, Integer x, Integer y, Integer w, Integer h, Integer width)`
  - `static final int COLS = 3`, `static final int DEFAULT_HEIGHT = 4` on `BoardService`
  - `BoardResponse.Item` now exposes `getX()`, `getY()`, `getW()`, `getH()` and **no** `getWidth()` (Task 3 changes the DTO; this task's tests are written against it and go green there)

> **Read this before starting:** Tasks 2 and 3 are one compile unit — the record change and the DTO change break each other's compilation until both are done. Write Task 2's tests first (they will not compile yet), then do Task 3, then come back and run them. The step ordering below already does this: **Task 2 Step 4 is expected to fail compilation**, and that is the RED step.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `BoardServiceTest.java` with:

```java
package in.healthconnect.widgetengine.service;

import in.healthconnect.exception.ResourceNotFoundException;
import in.healthconnect.widgetengine.dto.request.CreateBoardRequest;
import in.healthconnect.widgetengine.dto.request.SaveBoardLayoutRequest;
import in.healthconnect.widgetengine.dto.response.BoardResponse;
import in.healthconnect.widgetengine.entity.Board;
import in.healthconnect.widgetengine.entity.Widget;
import in.healthconnect.widgetengine.entity.enums.WidgetType;
import in.healthconnect.widgetengine.repository.BoardRepository;
import in.healthconnect.widgetengine.repository.WidgetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Tests for BoardService. Real Jackson mapper; mock repositories (no database).
//
// The important behaviour here is NORMALIZATION. Boards saved before the grid existed look
// like [{"widgetId":12,"width":1}] - no x/y/w/h. We fill those in on READ, laying them out
// the way the old UI did: left to right, 3 columns per row, in stored order. That means an
// existing board opens unchanged and the first save rewrites it in the new shape.
class BoardServiceTest {

    private final BoardRepository boardRepository = mock(BoardRepository.class);
    private final WidgetRepository widgetRepository = mock(WidgetRepository.class);
    private final BoardService service =
            new BoardService(boardRepository, widgetRepository, JsonMapper.builder().build());

    private Widget widget(int id, String code, String name, WidgetType type) {
        Widget w = new Widget();
        w.setId(id);
        w.setCode(code);
        w.setName(name);
        w.setType(type);
        return w;
    }

    // Register a board with the given raw layout JSON, and widgets 1..5 so lookups succeed.
    private Board boardWithLayout(String layoutJson) {
        Board board = new Board();
        board.setId(1);
        board.setName("Ops");
        board.setLayout(layoutJson);
        when(boardRepository.findById(1)).thenReturn(Optional.of(board));
        for (int id = 1; id <= 5; id++) {
            when(widgetRepository.findById(id))
                    .thenReturn(Optional.of(widget(id, "w" + id, "Widget " + id, WidgetType.TABLE)));
        }
        return board;
    }

    @Test
    void createStoresNameAndEmptyLayout() {
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));
        CreateBoardRequest request = new CreateBoardRequest();
        request.setName("My Board");

        service.create(request);

        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).save(captor.capture());
        assertEquals("My Board", captor.getValue().getName());
        assertEquals("[]", captor.getValue().getLayout()); // starts empty
    }

    @Test
    void getThrowsWhenBoardMissing() {
        when(boardRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(99));
    }

    @Test
    void legacyItemsAreFlowPackedIntoThreeColumnRows() {
        boardWithLayout("[{\"widgetId\":1,\"width\":1},{\"widgetId\":2,\"width\":2}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(2, items.size());
        assertEquals(0, items.get(0).getX());
        assertEquals(0, items.get(0).getY());
        assertEquals(1, items.get(0).getW());
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(0).getH());

        assertEquals(1, items.get(1).getX()); // sits beside the first
        assertEquals(0, items.get(1).getY());
        assertEquals(2, items.get(1).getW());
    }

    @Test
    void aLegacyItemThatDoesNotFitStartsTheNextRow() {
        // 1 + 3 > 3, so the full-width item must wrap
        boardWithLayout("[{\"widgetId\":1,\"width\":1},{\"widgetId\":2,\"width\":3}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(0, items.get(0).getX());
        assertEquals(0, items.get(0).getY());

        assertEquals(0, items.get(1).getX());
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(1).getY()); // next row down
        assertEquals(3, items.get(1).getW());
    }

    @Test
    void legacyItemWithNoWidthDefaultsToOneColumn() {
        boardWithLayout("[{\"widgetId\":1}]");

        BoardResponse.Item item = service.get(1).getItems().get(0);

        assertEquals(1, item.getW());
        assertEquals(0, item.getX());
        assertEquals(0, item.getY());
    }

    @Test
    void itemsThatAlreadyHaveCoordinatesAreLeftAlone() {
        boardWithLayout("[{\"widgetId\":1,\"x\":2,\"y\":7,\"w\":1,\"h\":9}]");

        BoardResponse.Item item = service.get(1).getItems().get(0);

        assertEquals(2, item.getX());
        assertEquals(7, item.getY());
        assertEquals(1, item.getW());
        assertEquals(9, item.getH());
    }

    @Test
    void aMixedLayoutOnlyNormalizesTheItemsThatNeedIt() {
        boardWithLayout("[{\"widgetId\":1,\"x\":0,\"y\":0,\"w\":3,\"h\":5},{\"widgetId\":2,\"width\":2}]");

        List<BoardResponse.Item> items = service.get(1).getItems();

        assertEquals(3, items.get(0).getW());   // untouched
        assertEquals(5, items.get(0).getH());
        assertEquals(2, items.get(1).getW());   // normalized
        assertEquals(BoardService.DEFAULT_HEIGHT, items.get(1).getH());
    }

    @Test
    void aBadLayoutGivesAnEmptyBoardRatherThanAnError() {
        boardWithLayout("this is not json");

        assertTrue(service.get(1).getItems().isEmpty());
    }

    @Test
    void aWidgetThatNoLongerExistsIsSkipped() {
        Board board = new Board();
        board.setId(1);
        board.setName("Ops");
        board.setLayout("[{\"widgetId\":404,\"x\":0,\"y\":0,\"w\":1,\"h\":4}]");
        when(boardRepository.findById(1)).thenReturn(Optional.of(board));
        when(widgetRepository.findById(404)).thenReturn(Optional.empty());

        assertTrue(service.get(1).getItems().isEmpty());
    }

    @Test
    void saveWritesGridCoordinatesAndNeverWritesTheLegacyWidthField() {
        Board board = boardWithLayout("[]");
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));

        SaveBoardLayoutRequest request = new SaveBoardLayoutRequest();
        request.setItems(List.of(new SaveBoardLayoutRequest.Item(1, 2, 4, 1, 6)));

        BoardResponse response = service.save(1, request);

        assertTrue(board.getLayout().contains("\"widgetId\":1"));
        assertTrue(board.getLayout().contains("\"x\":2"));
        assertTrue(board.getLayout().contains("\"y\":4"));
        assertTrue(board.getLayout().contains("\"w\":1"));
        assertTrue(board.getLayout().contains("\"h\":6"));
        assertFalse(board.getLayout().contains("width"), "legacy width must never be written");

        BoardResponse.Item item = response.getItems().get(0);
        assertEquals(2, item.getX());
        assertEquals(4, item.getY());
        assertEquals(1, item.getW());
        assertEquals(6, item.getH());
    }

    @Test
    void saveRenamesTheBoardWhenANameIsGiven() {
        Board board = boardWithLayout("[]");
        when(boardRepository.save(any(Board.class))).thenAnswer(call -> call.getArgument(0));

        SaveBoardLayoutRequest request = new SaveBoardLayoutRequest();
        request.setName("Renamed");
        request.setItems(List.of());

        service.save(1, request);

        assertEquals("Renamed", board.getName());
    }
}
```

- [ ] **Step 2: Rewrite `LayoutItem` and add normalization**

In `BoardService.java`, replace the record at the bottom of the file:

```java
    // the small shape we store per widget on a board
    public record LayoutItem(Integer widgetId, Integer width) {
    }
```

with:

```java
    // What we store per widget on a board.
    //
    // x/y is the position on a 3-column grid, w/h the span and height in row units.
    // `width` is LEGACY: boards saved before the grid existed look like
    // [{"widgetId":12,"width":1}]. We only ever READ it - normalize() turns it into
    // coordinates - and @JsonInclude(NON_NULL) keeps it out of everything we write.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LayoutItem(
            Integer widgetId,
            Integer x, Integer y,
            Integer w, Integer h,
            Integer width
    ) {
    }
```

Add the import at the top of the file (annotations are still in the old package under Jackson 3):

```java
import com.fasterxml.jackson.annotation.JsonInclude;
```

Add the two constants just below the class declaration, above the fields:

```java
    // The board grid is 3 columns wide.
    static final int COLS = 3;

    // Height, in grid row units, given to a widget that has never had one
    // (with the frontend's rowHeight of 80px this is about the height of the old cards).
    static final int DEFAULT_HEIGHT = 4;
```

Add these two private methods next to the other helpers:

```java
    // Fill in grid coordinates for items saved before the grid existed.
    // Old items have only `width`, so we lay them out the way the old UI did: flow them
    // left to right, wrapping after 3 columns, in the order they were stored.
    // Items that already carry coordinates are passed through untouched.
    private List<LayoutItem> normalize(List<LayoutItem> stored) {
        List<LayoutItem> out = new ArrayList<>(stored.size());
        int cursorX = 0;
        int cursorY = 0;

        for (LayoutItem item : stored) {
            boolean positioned = item.x() != null && item.y() != null
                    && item.w() != null && item.h() != null;
            if (positioned) {
                out.add(item);
                continue;
            }

            int w = clampWidth(item.width());
            if (cursorX + w > COLS) {   // does not fit on this row - start the next one
                cursorX = 0;
                cursorY += DEFAULT_HEIGHT;
            }
            out.add(new LayoutItem(item.widgetId(), cursorX, cursorY, w, DEFAULT_HEIGHT, null));
            cursorX += w;
        }
        return out;
    }

    // A legacy width of null, 0 or 47 should still produce a usable column span.
    private int clampWidth(Integer width) {
        if (width == null) {
            return 1;
        }
        return Math.max(1, Math.min(COLS, width));
    }
```

- [ ] **Step 3: Use the new shape in `save` and `toResponse`**

In `save(...)`, replace the loop body:

```java
            for (SaveBoardLayoutRequest.Item item : request.getItems()) {
                items.add(new LayoutItem(item.getWidgetId(), item.getWidth() == null ? 1 : item.getWidth()));
            }
```

with:

```java
            for (SaveBoardLayoutRequest.Item item : request.getItems()) {
                // width is null: we never write the legacy field
                items.add(new LayoutItem(item.getWidgetId(),
                        item.getX(), item.getY(), item.getW(), item.getH(), null));
            }
```

In `toResponse(...)`, change the loop header from:

```java
        for (LayoutItem stored : readItems(board.getLayout())) {
```

to:

```java
        for (LayoutItem stored : normalize(readItems(board.getLayout()))) {
```

and replace `.width(stored.width())` in the builder with:

```java
                    .x(stored.x())
                    .y(stored.y())
                    .w(stored.w())
                    .h(stored.h())
```

Finally update the class comment at the top of the file — it currently says "which widgets + widths + order":

```java
// Manages dashboard boards: create (name only), list, get (with widgets resolved),
// save layout (which widgets, where on the 3-column grid, and how big), delete.
// The layout is stored as JSON text; here we read/write it, fill in coordinates for
// boards saved before the grid existed, and look up each widget's info.
```

- [ ] **Step 4: Run the tests — expect a compilation failure (this is RED)**

Run: `./mvnw -o test -Dtest=BoardServiceTest`

Expected: **FAILURE**, with `cannot find symbol` on `getX()` / `getY()` / `getW()` / `getH()` and on the 5-argument `SaveBoardLayoutRequest.Item` constructor. Those come from the DTOs, which Task 3 changes. Do not try to fix them here — go to Task 3.

---

### Task 3: Backend — the DTOs

**Files:**
- Modify: `D:\HMS\healthconnect\src\main\java\in\healthconnect\widgetengine\dto\request\SaveBoardLayoutRequest.java`
- Modify: `D:\HMS\healthconnect\src\main\java\in\healthconnect\widgetengine\dto\response\BoardResponse.java`
- Modify: `D:\HMS\healthconnect\src\main\java\in\healthconnect\widgetengine\dto\response\WidgetSummaryResponse.java`

**Interfaces:**
- Consumes: `LayoutItem` and `BoardService.DEFAULT_HEIGHT` (Task 2)
- Produces:
  - `SaveBoardLayoutRequest.Item(Integer widgetId, Integer x, Integer y, Integer w, Integer h)` — an all-args constructor in that exact order, plus getters
  - `BoardResponse.Item` with `getWidgetId/getCode/getName/getType/getX/getY/getW/getH` and a `builder()` taking `.x() .y() .w() .h()`
  - `WidgetSummaryResponse.getStatus()` returning `WidgetStatus`

- [ ] **Step 1: Rewrite the request DTO**

Replace the whole of `SaveBoardLayoutRequest.java`:

```java
package in.healthconnect.widgetengine.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

// To save a board's layout: an optional new name, plus every widget on it with its
// position on the 3-column grid. The FE sends the WHOLE list each time - simplest to save.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaveBoardLayoutRequest {

    private String name; // optional: rename the board

    @NotNull
    private List<@Valid Item> items;

    // One widget on the board: which column it starts in (x), which row (y),
    // how many columns it spans (w) and how many row units tall it is (h).
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull
        private Integer widgetId;

        @NotNull
        @Min(0)
        @Max(2)
        private Integer x;

        @NotNull
        @Min(0)
        private Integer y;

        @NotNull
        @Min(1)
        @Max(3)
        private Integer w;

        @NotNull
        @Min(1)
        private Integer h;

        // The grid is 3 columns wide, so a widget can never start at column 2 and span 2.
        // This spans two fields, so it cannot be a simple field annotation.
        // Returns true when either value is null - @NotNull already reports that case, and
        // reporting it twice would put two messages on one mistake.
        @AssertTrue(message = "x + w must not exceed 3 columns")
        public boolean isWithinGrid() {
            if (x == null || w == null) {
                return true;
            }
            return x + w <= 3;
        }
    }
}
```

> `@Valid` on the list element is what makes the nested `Item` rules actually run. Without it, Spring validates only `items != null` and every per-item rule is silently ignored.

- [ ] **Step 2: Update the response DTO**

In `BoardResponse.java`, replace the `width` field in the nested `Item` class:

```java
        private Integer width;     // 1, 2 or 3 columns
```

with:

```java
        private Integer x;         // starting column, 0-2
        private Integer y;         // row, in grid row units
        private Integer w;         // column span, 1-3
        private Integer h;         // height, in grid row units
```

And update the class comment above `Item`:

```java
    // One widget on the board: enough info for the UI to draw a card, fetch its data,
    // and place it on the 3-column grid.
```

- [ ] **Step 3: Add `status` to the widget summary**

In `WidgetSummaryResponse.java`, add the import:

```java
import in.healthconnect.widgetengine.entity.enums.WidgetStatus;
```

add the field after `type`:

```java
    // DRAFT or APPROVED. The widget gallery uses this to hide unapproved AI drafts.
    private WidgetStatus status;
```

and add the mapping to `from(...)`, after `.type(widget.getType())`:

```java
                .status(widget.getStatus())
```

- [ ] **Step 4: Run Task 2's tests — expect GREEN**

Run: `./mvnw -o test -Dtest=BoardServiceTest`
Expected: `Tests run: 11, Failures: 0, Errors: 0`

If `legacyItemsAreFlowPackedIntoThreeColumnRows` fails on the second item's `x`, the cursor is not advancing by `w`. If `aLegacyItemThatDoesNotFitStartsTheNextRow` fails on `y`, the wrap is incrementing `cursorY` by 1 instead of `DEFAULT_HEIGHT`.

- [ ] **Step 5: Run the whole backend suite**

Run: `./mvnw -o test -Dtest='SqlSafetyGuardTest,SqlTemplateEngineTest,WidgetQueryExecutorTest,FilterConfigParserTest,WidgetServiceTest,WidgetExecutionServiceTest,KnowledgeBaseServiceTest,BoardServiceTest,PromptBuilderTest,SqlCleanerTest,SqlDraftServiceTest,NimQueryGeneratorTest,SettingServiceTest,AppSettingResponseTest'`
Expected: `Failures: 0, Errors: 0`

- [ ] **Step 6: Check the live board still loads — the whole point of this plan**

`./mvnw -o compile` hot-reloads the running app via devtools. Then:

```bash
curl -s http://localhost:8080/api/v1/boards
```

Take the id of "Rustam board" and fetch it:

```bash
curl -s http://localhost:8080/api/v1/boards/{id}
```

Expected: **14 items**, each with `x`, `y`, `w`, `h` filled in and **no** `width` field. The `w` values must match the widths the board had before, and `x`/`y` must flow 3 per row in the original order.

**If `items` comes back empty, stop.** That means `readItems` swallowed a parse error — the most likely cause is the `LayoutItem` record not tolerating the legacy JSON. Check by hand:

```bash
curl -s http://localhost:8080/api/v1/boards/{id} | head -c 400
```

- [ ] **Step 7: Check validation rejects an impossible position**

```bash
curl -s -w "\nHTTP=%{http_code}\n" -X PUT http://localhost:8080/api/v1/boards/{id} -H "Content-Type: application/json" -d '{"items":[{"widgetId":1,"x":2,"y":0,"w":2,"h":4}]}'
```

Expected: **HTTP 400** mentioning `x + w must not exceed 3 columns`. (`x=2, w=2` would run off the 3-column grid.)

**Do not run a successful save against the real board yet** — the frontend has not been updated, so a save now would rewrite the layout in a shape the current UI cannot read.

- [ ] **Step 8: Commit**

```bash
git commit src/main/java/in/healthconnect/widgetengine/service/BoardService.java src/main/java/in/healthconnect/widgetengine/dto/request/SaveBoardLayoutRequest.java src/main/java/in/healthconnect/widgetengine/dto/response/BoardResponse.java src/main/java/in/healthconnect/widgetengine/dto/response/WidgetSummaryResponse.java src/test/java/in/healthconnect/widgetengine/service/BoardServiceTest.java -m "feat(board): store grid coordinates, normalize legacy layouts on read"
```

> Several of these files are **staged-but-uncommitted** work of the user's (`A` in `git status`). Committing them by pathspec includes their whole content, not just your edit. If `git status` shows them as `AM`, ask the user before committing rather than sweeping in their tree.

---

### Task 4: Frontend — layout helpers and `BoardGrid`

**Files:**
- Create: `D:\HMS\healtconnectfe\src\features\dashboard\boardLayout.js`
- Create: `D:\HMS\healtconnectfe\src\features\dashboard\BoardGrid.jsx`
- Delete: `D:\HMS\healtconnectfe\src\features\dashboard\BoardView.jsx`
- Modify: `D:\HMS\healtconnectfe\src\features\dashboard\DashboardPage.jsx`

**Interfaces:**
- Consumes: `BoardResponse.Item` shape `{ widgetId, code, name, type, x, y, w, h }` (Task 3); the confirmed import form from Task 1
- Produces:
  - `boardLayout.js` — `COLS`, `DEFAULT_H`, `ROW_HEIGHT`, `nextPosition(items)`, `setWidth(items, widgetId, w)`, `toSavePayload(items)`
  - `BoardGrid.jsx` — default export `BoardGrid({ items, onRemove, onWidthChange })`

- [ ] **Step 1: Write the layout helpers**

Create `src/features/dashboard/boardLayout.js`:

```js
// Pure helpers for the board's 3-column grid. No React, no network - just maths on the
// item list, so the page component stays about wiring rather than layout arithmetic.

export const COLS = 3
export const DEFAULT_H = 4      // matches BoardService.DEFAULT_HEIGHT on the backend
export const ROW_HEIGHT = 80    // pixels per grid row unit

// Where a newly added widget goes: a fresh row under everything already there.
export function nextPosition(items = []) {
  const bottom = items.reduce((lowest, item) => Math.max(lowest, (item.y || 0) + (item.h || DEFAULT_H)), 0)
  return { x: 0, y: bottom, w: 1, h: DEFAULT_H }
}

// Change one widget's column span, keeping it on the grid.
// Widening a widget that sits on the right edge would otherwise push it off: a widget at
// x=2 made 3 wide needs x=0. The backend rejects x + w > 3, so this is not cosmetic.
export function setWidth(items, widgetId, w) {
  return items.map((item) =>
    item.widgetId === widgetId ? { ...item, w, x: Math.min(item.x || 0, COLS - w) } : item,
  )
}

// The shape the backend wants: position only, no widget details.
export function toSavePayload(items = []) {
  return items.map((item) => ({
    widgetId: item.widgetId,
    x: item.x ?? 0,
    y: item.y ?? 0,
    w: item.w ?? 1,
    h: item.h ?? DEFAULT_H,
  }))
}
```

- [ ] **Step 2: Write `BoardGrid`**

Create `src/features/dashboard/BoardGrid.jsx`. **Use the import form Task 1 confirmed** — the one below is the expected form:

```jsx
import { useMemo } from 'react'
import { Empty } from 'antd'
import GridLayout, { WidthProvider } from 'react-grid-layout'
import WidgetCard from './WidgetCard.jsx'
import { COLS, ROW_HEIGHT } from './boardLayout.js'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

// Draws the widgets of one board on a 3-column grid.
//
// Read-only for now: dragging and resizing arrive in the next round, at which point this
// component gains an `editable` prop and flips isDraggable/isResizable. Rendering view and
// edit through the SAME component is deliberate - it means the board cannot shift position
// when you enter edit mode.
const Grid = WidthProvider(GridLayout)

export default function BoardGrid({ items = [], onRemove, onWidthChange }) {
  // react-grid-layout wants its own array, keyed by a STRING id.
  const layout = useMemo(
    () =>
      items.map((item) => ({
        i: String(item.widgetId),
        x: item.x,
        y: item.y,
        w: item.w,
        h: item.h,
        minW: 1,
        minH: 2,
      })),
    [items],
  )

  if (items.length === 0) {
    return <Empty description="This board is empty. Click 'Add widget' to add reports." />
  }

  return (
    <Grid
      className="layout"
      layout={layout}
      cols={COLS}
      rowHeight={ROW_HEIGHT}
      margin={[16, 16]}
      compactType="vertical"
      isDraggable={false}
      isResizable={false}
    >
      {items.map((item) => (
        <div key={String(item.widgetId)}>
          <WidgetCard item={item} onRemove={onRemove} onWidthChange={onWidthChange} />
        </div>
      ))}
    </Grid>
  )
}
```

- [ ] **Step 3: Rewire `DashboardPage`**

In `src/features/dashboard/DashboardPage.jsx`:

Replace the `BoardView` import:

```jsx
import BoardView from './BoardView.jsx'
```

with:

```jsx
import BoardGrid from './BoardGrid.jsx'
import { nextPosition, setWidth, toSavePayload } from './boardLayout.js'
```

Replace the `toPayload` helper:

```jsx
  // convert the rich items back to the small save shape { widgetId, width }
  const toPayload = (list) => list.map((i) => ({ widgetId: i.widgetId, width: i.width || 1 }))
```

with nothing — `toSavePayload` from `boardLayout.js` replaces it. Then update `persist`:

```jsx
  const persist = (nextItems) =>
    saveBoard.mutate(
      { id: boardId, payload: { items: toSavePayload(nextItems) } },
      { onError: (e) => message.error(e.message || 'Save failed') },
    )
```

Replace `handleAdd`:

```jsx
  const handleAdd = (widget) => {
    if (items.some((i) => i.widgetId === widget.id)) return
    persist([...items, { widgetId: widget.id, ...nextPosition(items) }])
    message.success(`Added "${widget.name}"`)
  }
```

Replace `handleWidth`:

```jsx
  const handleWidth = (widgetId, w) => persist(setWidth(items, widgetId, w))
```

And replace the `<BoardView ... />` element with:

```jsx
        <BoardGrid items={items} onRemove={handleRemove} onWidthChange={handleWidth} />
```

`handleRemove` is unchanged.

- [ ] **Step 4: Delete the replaced component**

```bash
rm src/features/dashboard/BoardView.jsx
grep -rn "BoardView" src/ || echo "CLEAN"
```

Expected: `CLEAN`.

- [ ] **Step 5: Build and lint**

```bash
npm run build
```
Expected: `✓ built in …` with no error. (A "chunks are larger than 500 kB" warning is pre-existing.)

```bash
npm run lint
```
Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add src/features/dashboard/boardLayout.js src/features/dashboard/BoardGrid.jsx
git commit src/features/dashboard/boardLayout.js src/features/dashboard/BoardGrid.jsx src/features/dashboard/DashboardPage.jsx src/features/dashboard/BoardView.jsx -m "feat(dashboard): render boards on a 3-column grid"
```

> The whole `src/features/dashboard/` folder is **untracked** work of the user's. This commit therefore includes their files wholesale. If that is not wanted, skip the commit and leave the work in the tree — verification in Task 5 does not depend on it.

---

### Task 5: Frontend — resizable cards, then verify the real board

**Files:**
- Modify: `D:\HMS\healtconnectfe\src\features\dashboard\WidgetCard.jsx`

**Interfaces:**
- Consumes: `BoardGrid` (Task 4), the live board from Task 3

**Why this is in the same task as verification:** a card that does not fill its grid cell looks broken the moment the grid renders, so the fix and the check belong together.

- [ ] **Step 1: Make the card fill its cell**

In `WidgetCard.jsx`, change the opening `<Card` tag from:

```jsx
    <Card
      title={item.name}
      size="small"
```

to:

```jsx
    <Card
      title={item.name}
      size="small"
      // Fill the grid cell: the cell has a fixed pixel height, and the body scrolls
      // rather than the card overflowing it.
      style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
      styles={{ body: { flex: 1, overflow: 'auto' } }}
```

- [ ] **Step 2: Make charts follow the cell's height**

In `renderByType`, replace:

```jsx
  const options = { responsive: true, plugins: { legend: { display: type === 'PIE' } } }

  if (type === 'BAR') return <Bar data={chartData} options={options} />
  if (type === 'LINE') return <Line data={chartData} options={options} />
  if (type === 'PIE') return <Pie data={chartData} options={options} />
  return <Empty description={`Unsupported type: ${type}`} />
```

with:

```jsx
  // maintainAspectRatio must be OFF inside a resizable cell. With it on, Chart.js keeps
  // its own ratio and either overflows the card or leaves dead space when the cell's
  // height changes. Off, it fills whatever box we give it - hence the sized wrapper.
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: type === 'PIE' } },
  }

  const chart =
    type === 'BAR' ? <Bar data={chartData} options={options} />
    : type === 'LINE' ? <Line data={chartData} options={options} />
    : type === 'PIE' ? <Pie data={chartData} options={options} />
    : null

  if (!chart) return <Empty description={`Unsupported type: ${type}`} />
  return <div style={{ position: 'relative', height: '100%', minHeight: 140 }}>{chart}</div>
```

- [ ] **Step 3: Open the app**

`preview_start` with `{url: "http://localhost:5173"}` — the dev server is already running; do **not** start another.

- [ ] **Step 4: Check the console**

`read_console_messages` with `onlyErrors: true`.

Expected: no errors from `react-grid-layout` and no `findDOMNode` error. Pre-existing antd deprecation warnings (`List` is deprecated) are fine.

- [ ] **Step 5: Confirm the live board renders unchanged**

`get_page_text` and confirm **all 14 widgets** are present with the same titles as before, and the tables and charts have data rather than error alerts.

Then check the grid actually laid out in 3 columns:

`javascript_tool`:

```js
(() => { const items=[...document.querySelectorAll('.react-grid-item')]; return JSON.stringify({ count: items.length, firstThreeLefts: items.slice(0,3).map(e => Math.round(e.getBoundingClientRect().left)), anyZeroHeight: items.some(e => e.getBoundingClientRect().height < 20) }); })()
```

Expected: `count` is 14, the first three `left` values are three **different** ascending numbers (three widgets across a row), and `anyZeroHeight` is `false`.

- [ ] **Step 6: Confirm nothing the user can do has broken**

This plan changes no behaviour, so all three must still work:

1. **Change a width** — click `2` on a card's Segmented control. The card widens, and it persists (reload the page and it is still 2 wide).
2. **Add a widget** — "Add widget" → Library → Add. It appears at the bottom, full flow intact.
3. **Remove a widget** — the trash icon on the widget you just added. It disappears and stays gone after a reload.

If any of these returns a **400**, read the response: the likely cause is `toSavePayload` emitting a null `x`/`y`, or `setWidth` not clamping `x`, leaving `x + w > 3`.

- [ ] **Step 7: Confirm the layout survives a full round trip**

Step 6 has now written the board at least once, so what is stored is the new shape.

```bash
curl -s http://localhost:8080/api/v1/boards/{id} | head -c 300
```

Expected: items carry `x`/`y`/`w`/`h` and no `width`.

That response alone is not proof, because normalization would produce coordinates even from
a legacy row. The proof is a **round trip**: hard-reload the dashboard (Ctrl+Shift+R) and
confirm the widget you widened in Step 6 is still 2 columns wide and in the same place. A
legacy row could not carry that — normalization would have reset it to a flow-packed
default.

If you want to see the raw column, connect to MySQL with the credentials in
`src/main/resources/application.properties` and run:

```sql
SELECT id, name, layout FROM board WHERE is_deleted = false;
```

Expected: `layout` reads `[{"widgetId":…,"x":…,"y":…,"w":…,"h":…}, …]` with no `width` key.
This is optional — `saveWritesGridCoordinatesAndNeverWritesTheLegacyWidthField` in Task 2
already covers the written shape; the round trip above is what proves it end to end.

- [ ] **Step 8: Build, lint, commit**

```bash
npm run build
```
Expected: builds clean.

```bash
npm run lint
```
Expected: no output.

```bash
git commit src/features/dashboard/WidgetCard.jsx -m "fix(dashboard): let cards and charts fill their grid cell"
```

---

## Done when

- `./mvnw -o test` passes for the backend suites, `BoardServiceTest` included (11 tests).
- `GET /api/v1/boards/{id}` returns all 14 of the user's widgets with `x/y/w/h` and no `width`.
- A save with `x=2, w=2` is rejected with **400** and the message `x + w must not exceed 3 columns`.
- The dashboard renders all 14 widgets in a 3-column grid, visually the same as before.
- Width change, add and remove all still work and survive a reload.
- `npm run build` and `npm run lint` are clean.

## Not in this plan

Drag, resize, the widget gallery with previews, multi-select, deleting AI widgets from the library, view/edit modes, explicit Save/Cancel, and removing the Segmented control. All of that is **Plan 2**, which assumes this plan's data model and `BoardGrid`.
