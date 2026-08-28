# Dashboard board builder: gallery → arrange → save → view

**Date:** 2026-08-27
**Status:** Approved, not yet implemented
**Scope:** both repos — `healthconnect` (backend) and `healtconnectfe` (frontend).
Docs live here because the existing specs and plans do.

---

## Problem

Today a board is built one widget at a time: open a drawer, click **Add**, and the widget
lands at the bottom at width 1. Width is then changed with a `1 / 2 / 3` Segmented control
on each card, and **every change writes to the server immediately**. There is no way to
reorder, no way to change height, and the drawer shows only a name and a type tag — you
cannot tell what a widget looks like until it is already on the board.

Approved AI widgets don't appear in the drawer at all: it fetches `module: 'WIDGET'` and AI
widgets are `module: 'PROMPT'`. And because every AI generation creates a widget row,
one-off experiments accumulate with no way to clean them up from the UI.

## What we're building

A three-step builder:

1. **Select** — a gallery of every widget as a card showing a live mini preview of what it
   renders. Tick as many as you want. Approved AI widgets appear here, and AI widgets can
   be deleted from the library.
2. **Arrange** — the selected widgets on a 3-column canvas. Drag to reorder, drag edges to
   resize width *and* height. A row holds at most 3 columns.
3. **Save**, then a read-only **View** with an **Edit** button back into the builder.

The reference screenshot supplied by the user is a **pattern reference only**. The existing
Ant Design styling stays.

## Decisions

| # | Decision | Chosen | Why |
|---|---|---|---|
| 1 | Gallery previews | Live, fetched when the gallery opens, `pageSize: 5` | Reuses `useWidgetData` + React Query cache; ~14 small aggregate queries is within budget |
| 2 | Drag & resize | `react-grid-layout` v2 | Purpose-built for dashboards; `cols={3}` enforces "max 3 per row" in the grid itself |
| 3 | Delete semantics | Remove-from-board **and** delete-from-library, the latter limited to `PROMPT` widgets | AI experiments need cleanup; hand-built widgets are shared and a one-click delete beside them in a picker is a foot-gun |
| 4 | Edit entry point | Edit → **arrange** step, with "Add widgets" opening the gallery | The common edit is a nudge, not a re-pick |
| 5 | Saving | Explicit Save; drag/resize are local until then | A drag interaction would otherwise fire a save per pixel |
| 6 | Width control | The `1 / 2 / 3` Segmented control is removed | Width now comes from dragging |
| 7 | Legacy layouts | Normalized **on read** in `BoardService` | The user has a live 14-widget board in the old shape; no hand migration, no chance of it coming back empty |

### Rejected, and why

- **Lazy previews (IntersectionObserver).** Correct at scale, unnecessary at ~14 widgets.
  Revisit past roughly 30 — a contained change, not a rewrite.
- **Static per-type illustrations.** Free, but every bar chart looks identical, so the card
  tells you nothing beyond the type — which the existing tag already did.
- **`@dnd-kit` + hand-rolled resize.** Resize is the bulk of the work here; building it by
  hand for a fixed-column dashboard grid is choosing the hard path. Kept as the fallback if
  `react-grid-layout` misbehaves on React 19.
- **Edit → gallery every time.** Tedious when you only want to change one card's height.
- **A combined `?modules=WIDGET,PROMPT` endpoint.** A backend endpoint change to save one
  HTTP call that React Query caches anyway.
- **Keeping `width` alongside `w` in `BoardResponse.Item`.** Two sources of truth for one
  number. This frontend is the only consumer and we're rewriting the part that reads it.

---

## Design — backend (`healthconnect`)

### 1. The layout record

```java
public record LayoutItem(
    Integer widgetId,
    Integer x, Integer y,   // grid position on a 3-column grid
    Integer w, Integer h,   // column span and height in row units
    Integer width           // LEGACY: read only, never written
) {}
```

`layout` stays a `TEXT` column holding JSON, so **there is no schema migration.**

### 2. Normalization on read

Stored boards look like `[{"widgetId":12,"width":1}, ...]`. Jackson reads those into the
record with `x/y/w/h` as `null`. `BoardService` then fills them in before building the
response:

- walk items in stored order, carrying a cursor `(x, y)`;
- for an item missing coordinates, use `w = width` (defaulting to 1, clamped to 1–3) and
  `h = DEFAULT_HEIGHT`;
- if `x + w > 3`, wrap to the next row (`x = 0`, `y += 1`);
- place at the cursor, then advance `x` by `w`;
- items that already carry `x/y/w/h` are left untouched, and do not move the cursor.

A layout mixing both shapes could in principle place two items on the same cell. That only
happens if the JSON was hand-edited, and `react-grid-layout`'s vertical compaction resolves
any overlap on render, so it is not worth extra logic to prevent.

The result: an existing board opens looking exactly as it does now, and the first Save
rewrites it in the new shape.

`DEFAULT_HEIGHT` is **4** row units (with the frontend's `rowHeight: 80` and margin, about
the height of the current cards). It is a constant in `BoardService`, referenced by the
tests, so the number lives in one place.

### 3. DTOs

**`SaveBoardLayoutRequest.Item`** — `widgetId` (`@NotNull`), `x`, `y`, `w`, `h`, with
validation that enforces the 3-column rule server-side:

- `x` — `@NotNull @Min(0) @Max(2)`
- `y` — `@NotNull @Min(0)`
- `w` — `@NotNull @Min(1) @Max(3)`
- `h` — `@NotNull @Min(1)`
- **`x + w <= 3`** — a class-level `@AssertTrue` method, since it spans two fields

The client grid enforces the same rule, but a rule that matters shouldn't live only in the
browser.

**`BoardResponse.Item`** — `widgetId`, `code`, `name`, `type`, `x`, `y`, `w`, `h`.
The `width` field is removed.

**`WidgetSummaryResponse`** gains `status`, so the gallery can show approved AI widgets and
hide drafts.

### 4. What does not change

`Board` entity, the `board` table, `WidgetController`, `WidgetExecutionController`, the
engine, and the whole PROMPT module. `DELETE /widgets/{id}` already soft-deletes, so
delete-from-library needs no backend work.

---

## Design — frontend (`healtconnectfe`)

### Components

| File | Responsibility | Status |
|---|---|---|
| `BoardGrid.jsx` | The 3-column `react-grid-layout`; both view and edit | new |
| `WidgetGallery.jsx` | Modal: Library tab + Ask AI tab, multi-select | new |
| `WidgetPreviewCard.jsx` | One gallery card: name, live mini preview, selected state, delete | new |
| `useBoardDraft.js` | The unsaved layout and every mutation on it | new |
| `DashboardPage.jsx` | Board selection, create/delete, view↔edit orchestration | modified |
| `WidgetCard.jsx` | Gains `editable`; Segmented control removed; charts made resizable | modified |
| `AskAiPanel.jsx` | Adds to the *selection* rather than straight to the board | modified |
| `boardsApi.js` / `boardsHooks.js` | New layout shape; fetch both widget modules | modified |
| `BoardView.jsx`, `AddWidgetDrawer.jsx` | Replaced by `BoardGrid` / `WidgetGallery` | deleted |

**One grid, two modes.** `BoardGrid` takes an `editable` prop: view renders with
`isDraggable={false} isResizable={false}`, edit turns both on. Sharing the component means
the board cannot shift when entering edit — an easy bug to ship otherwise.

Grid config: `cols={3}`, `rowHeight={80}`, `margin={[16,16]}`, `compactType="vertical"`,
`minW: 1`, `minH: 2`.

**`useBoardDraft`** owns the tricky state, in its own file so `DashboardPage` stays a thin
orchestrator:

- `draft` — the working item list, seeded from the saved board
- `addWidgets(widgets)` — appends at the bottom at `w:1, h:DEFAULT_HEIGHT`
- `removeWidget(widgetId)`
- `applyLayout(rglLayout)` — on drag/resize, maps RGL's `{i,x,y,w,h}` back onto items
- `isDirty` — draft differs from the saved board
- `reset()`

### Flow

- **Existing board** → view mode. **Edit** seeds the draft and unlocks the grid.
- **New board** → straight into edit with an empty draft, gallery opened automatically.
- **Add widgets** → gallery opens with widgets already on the board pre-ticked **and
  disabled**, so duplicates can't be added. Confirm appends them.
- **Save** → one `PUT /boards/{id}` with the whole layout → back to view.
- **Cancel**, or switching board while `isDirty` → `Modal.confirm` before discarding.

### Gallery

Two tabs, reusing the shape the Add-widget drawer already had:

- **Library** — a responsive grid of `WidgetPreviewCard`. Each runs its widget via
  `useWidgetData(code, 5)`. Cards are tick-to-select. `PROMPT` widgets carry a delete
  button with a `Popconfirm` naming the widget; `WIDGET` widgets do not.
- **Ask AI** — the existing `AskAiPanel`, with `onAdd` now adding to the pending selection
  instead of the board, and no longer closing the drawer.

A preview whose query fails renders a muted "preview unavailable" tile and **stays
selectable** — a broken preview shouldn't block adding a widget you know you want, or make
the gallery look broken.

### Chart resizing

`WidgetCard` currently passes `responsive: true` and leaves `maintainAspectRatio` at its
default. In a cell whose height can be dragged, that combination either overflows or leaves
dead space. Charts get `maintainAspectRatio: false` inside a wrapper with
`height: 100%`, and the card body gets `height: 100%` with `overflow: auto`.

---

## Error handling

| Situation | Behaviour |
|---|---|
| A widget's query fails on the board | The card shows its existing error Alert; the grid is unaffected |
| A widget's preview fails in the gallery | Muted "preview unavailable" tile; card stays selectable |
| Save fails | `message.error` with the backend message; the draft is **kept** so nothing is lost |
| Delete-from-library fails | `message.error`; the card stays |
| Leaving with unsaved changes | `Modal.confirm` before discarding |
| A board's layout JSON is malformed | `readItems` already returns an empty list rather than throwing |
| `x + w > 3` sent by a client | 400 from bean validation |

---

## Testing

**Backend** — `BoardServiceTest`, mock repositories, matching the existing pattern:

1. legacy `[{widgetId,width:1},{widgetId,width:2}]` → `(x:0,y:0,w:1)` and `(x:1,y:0,w:2)`
2. legacy `width:3` starts its own row
3. an item already carrying `x/y/w/h` passes through untouched
4. a mixed layout normalizes only the items missing coordinates
5. malformed JSON returns an empty list
6. saving writes `x/y/w/h` and never writes `width`

Validation (`x + w <= 3`) is bean validation on the DTO, so it is checked live with curl
rather than in a service unit test.

**Frontend** has no test runner — `package.json` has only `dev`, `build`, `lint`,
`preview`. Verification is the browser:

1. the existing 14-widget board loads and renders **identically** to before
2. drag reorders; resize changes width and height; a row never exceeds 3 columns
3. gallery previews render; a failing preview degrades to the muted tile
4. multi-select adds several widgets at once; already-added ones are disabled
5. an AI widget can be deleted from the library and disappears from the gallery
6. Save persists; reload shows the same layout
7. Cancel with changes prompts; discarding restores the saved layout
8. `npm run build` and `npm run lint` are clean

---

## Risks

1. **`react-grid-layout` on React 19.** Peer deps are open (`react >= 16.3.0`), which does
   not prove support, and 1.x relied on `findDOMNode`, which React 19 removed. v2.2.4 is a
   recent major and very likely fine, but this is **unverified**. The first task is a
   throwaway spike: install, render three boxes, drag and resize one. If it fails, fall
   back to `@dnd-kit` before anything depends on it. The spike is the **first task of
   Plan 1**, because `BoardGrid` lands there.
2. **The existing 14-widget board.** The first check after the backend change is that it
   still loads and looks the same. This is why normalization happens on read.
3. **Chart.js in a resizable cell** — see above; handled in the plan, not left to be
   discovered.

---

## Plan split

Two plans, not one — this is bigger than the previous rounds and there is a natural seam.

- **Plan 1 — the data model.** `LayoutItem`, normalization, validation, `status` on the
  summary DTO, and `BoardGrid` in view-only mode. Deliverable: *the board still works, now
  on the new model.* No new features, independently verifiable, and it de-risks the
  migration before any UI churn.
- **Plan 2 — the builder.** Edit mode, `useBoardDraft`, drag/resize, the gallery with
  previews, multi-select, AI-widget delete, save/cancel.

Doing it in one pass would mean the first time we learn whether the existing board survives
is after a large pile of new UI is in place — two suspects instead of one.

## Out of scope

- Responsive breakpoints (a phone layout for the grid). The grid is fixed at 3 columns.
- Per-user boards or sharing.
- Reordering or renaming widgets from the gallery.
- Undo/redo in the builder.
