# Board Builder — Plan 2: the builder

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the read-only board into a builder — pick widgets from a gallery that shows what each one actually looks like, drag and resize them on a 3-column canvas, and save explicitly.

**Architecture:** `DashboardPage` gains two modes. **View** renders the saved board through the existing `BoardGrid`, locked. **Edit** renders the same grid unlocked, over a *draft* held by `useBoardDraft` — nothing reaches the server until Save. `WidgetGallery` replaces `AddWidgetDrawer`: a multi-select grid of cards, each running its own widget for a live mini preview, with AI widgets deletable from the library.

**Tech Stack:** React 19 · Vite 8 · antd 6 · TanStack Query 5 · **react-grid-layout 2.2.4** · Chart.js 4

**Spec:** `docs/superpowers/specs/2026-08-27-board-builder-design.md`
**Builds on:** `docs/superpowers/plans/2026-08-27-board-builder-plan-1-data-model.md` — read its **"Findings during execution"** section first; two of them shape this plan.

## Global Constraints

- **Frontend only.** Everything is in `D:\HMS\healtconnectfe`. Plan 1 already did the backend; no Java changes here.
- **`react-grid-layout` v2 API, not v1.** No `WidthProvider` (use `useContainerWidth({ measureBeforeMount: true })`); `cols`/`rowHeight`/`margin` live in `gridConfig`; `isDraggable`/`isResizable` live in `dragConfig`/`resizeConfig`; `compactType` is `compactor={verticalCompactor}`.
- **`layout` is INITIAL state only.** `GridLayout` ignores later `layout` prop changes. `BoardGrid` therefore takes a `layoutKey` prop used as its React `key`. **The caller decides when to remount** — and in edit mode it must *not* remount on drag, or the interaction dies mid-gesture. See Task 3.
- **Board item shape:** `{ widgetId, code, name, type, x, y, w, h }`. **Gallery widget shape** (from `WidgetSummaryResponse`): `{ id, code, name, description, module, type, enabled, status }`. Note `widgetId` vs `id` — they are different keys for the same number.
- **Grid constants** come from `boardLayout.js`: `COLS = 3`, `DEFAULT_H = 4`, `ROW_HEIGHT = 80`.
- **The server enforces `x + w <= 3`** and returns 400 otherwise. `cols={3}` keeps the grid honest, but never hand-build a position that breaks it.
- **There is no frontend test runner.** `package.json` has only `dev`, `build`, `lint`, `preview`. Every task therefore ends with `npm run build`, `npm run lint`, and a browser check. This is a real weakness — `useBoardDraft` is pure logic that deserves unit tests — but adding Vitest is a separate decision the user has not made. Do not add it as a side effect of this plan.
- **After any field rename, grep.** Plan 1 shipped a silent bug because `WidgetCard` still read `item.width` after the rename to `w`, and nothing complained. There is no compiler here.

### The user's app is already running

Dev server on **5173**, Spring Boot with devtools on **8080**, both started by the user. Do **not** start a second one. To open a browser tab: `preview_start` with `{url: "http://localhost:5173"}`. If the pane becomes unresponsive, `tabs_create` then `navigate` with the new `tabId`.

### The user's board is live data

Board 1 "Rustam board" holds **13 widgets**: row 1 is `w=2` + `w=1`, then four rows of `1+1+1` / `1+1`, `y` stepping by 4. **Write down that layout before you start testing**, because drag and resize now change it for real. Restore it when you are done, and verify with `curl -s http://localhost:8080/api/v1/boards/1`.

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `src/features/dashboard/boardLayout.js` | modify | `appendPacked`, `applyRglLayout`, `toSavePayload`; `nextPosition`/`setWidth` deleted |
| `src/features/dashboard/useBoardDraft.js` | create | The unsaved layout and every mutation on it |
| `src/features/dashboard/WidgetBody.jsx` | create | Renders rows by widget type; shared by the board card and the gallery card |
| `src/features/dashboard/WidgetPreviewCard.jsx` | create | One gallery card: name, live mini preview, tick, delete |
| `src/features/dashboard/WidgetGallery.jsx` | create | The picker modal: Library + Ask AI, multi-select |
| `src/features/dashboard/WidgetCard.jsx` | modify | Uses `WidgetBody`; gains `editable`; Segmented control removed |
| `src/features/dashboard/BoardGrid.jsx` | modify | Gains `editable`, `layoutKey`, `onLayoutChange` |
| `src/features/dashboard/DashboardPage.jsx` | modify | View/edit modes, Save/Cancel, unsaved guard |
| `src/features/dashboard/AskAiPanel.jsx` | modify | Adds to the gallery's selection, not straight to the board |
| `src/features/dashboard/widgetsApi.js` | modify | `getWidgets(module)`, `deleteWidget(id)` |
| `src/features/dashboard/aiApi.js` | modify | `discardWidget` removed — re-uses `deleteWidget` |
| `src/features/dashboard/aiHooks.js` | modify | `useDiscardWidget` now wraps `deleteWidget` |
| `src/features/dashboard/boardsHooks.js` | modify | `useGalleryWidgets`, `useDeleteWidget`; `useAvailableWidgets` deleted |
| `src/features/dashboard/AddWidgetDrawer.jsx` | delete | Replaced by `WidgetGallery` |

---

### Task 1: The data layer — layout helpers, widget lists, delete

No UI yet. Deliverable: the functions and hooks the rest of the plan consumes, with the app still building and the board still rendering.

**Files:**
- Modify: `src/features/dashboard/boardLayout.js`
- Modify: `src/features/dashboard/widgetsApi.js`
- Modify: `src/features/dashboard/aiApi.js`
- Modify: `src/features/dashboard/aiHooks.js`
- Modify: `src/features/dashboard/boardsHooks.js`

**Interfaces:**
- Consumes: `BoardResponse.Item` `{ widgetId, code, name, type, x, y, w, h }`; `WidgetSummaryResponse` `{ id, code, name, description, module, type, enabled, status }`
- Produces:
  - `appendPacked(items, widgets) -> items[]`
  - `applyRglLayout(items, rglLayout) -> items[]`
  - `toSavePayload(items) -> [{widgetId,x,y,w,h}]` (unchanged)
  - `getWidgets(module) -> Promise<Page>`, `deleteWidget(id) -> Promise`
  - `useGalleryWidgets() -> { data, isLoading, error }`
  - `useDeleteWidget() -> mutation`

- [ ] **Step 1: Replace the layout helpers**

`nextPosition` and `setWidth` both die here: adding now happens in batches, and width comes from dragging. Replace the whole of `src/features/dashboard/boardLayout.js`:

```js
// Pure helpers for the board's 3-column grid. No React, no network - just maths on the
// item list, so the components stay about wiring rather than layout arithmetic.

export const COLS = 3
export const DEFAULT_H = 4      // matches BoardService.DEFAULT_HEIGHT on the backend
export const ROW_HEIGHT = 80    // pixels per grid row unit

// The row below everything currently on the board.
function bottomOf(items) {
  return items.reduce((lowest, item) => Math.max(lowest, (item.y || 0) + (item.h || DEFAULT_H)), 0)
}

// Append widgets under what is already there, packing them 3 to a row rather than
// stacking each on its own row. Widgets already on the board are skipped, so this is
// safe to call with a selection that overlaps the current board.
//
// `widgets` are gallery widgets ({ id, code, name, type }), NOT board items -
// the id lives under `id` there and becomes `widgetId` here.
export function appendPacked(items = [], widgets = []) {
  const next = [...items]
  let x = 0
  let y = bottomOf(items)

  for (const widget of widgets) {
    if (next.some((item) => item.widgetId === widget.id)) {
      continue
    }
    if (x + 1 > COLS) {   // row is full - start the next one
      x = 0
      y += DEFAULT_H
    }
    next.push({
      widgetId: widget.id,
      code: widget.code,
      name: widget.name,
      type: widget.type,
      x,
      y,
      w: 1,
      h: DEFAULT_H,
    })
    x += 1
  }
  return next
}

// Fold react-grid-layout's [{i,x,y,w,h}] back onto our items. RGL keys by a STRING id,
// ours is a number, hence the String() on both sides.
export function applyRglLayout(items = [], rglLayout = []) {
  const byId = new Map(rglLayout.map((entry) => [String(entry.i), entry]))
  return items.map((item) => {
    const entry = byId.get(String(item.widgetId))
    return entry ? { ...item, x: entry.x, y: entry.y, w: entry.w, h: entry.h } : item
  })
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

- [ ] **Step 2: Let the widget API take a module, and delete a widget**

Replace `src/features/dashboard/widgetsApi.js`:

```js
import axiosClient from '../../api/axiosClient.js'

// List widgets of one module. The gallery asks for WIDGET (hand-built) and PROMPT
// (AI-generated) separately and merges them.
// Returns a Spring "page" object; the hook reads its .content array.
export function getWidgets(module = 'WIDGET') {
  return axiosClient.get('/widgets', { params: { module, page: 0, size: 100 } })
}

// Run a widget and get its data rows. body is optional (filters/sort/paging).
export function getWidgetData(idOrCode, body = {}) {
  return axiosClient.post(`/widgets/${idOrCode}/data`, body)
}

// Remove a widget from the library entirely (a soft delete on the backend).
// Used both to discard an unwanted AI draft and to tidy an approved AI widget away.
export function deleteWidget(id) {
  return axiosClient.delete(`/widgets/${id}`)
}
```

- [ ] **Step 3: Point the AI flow at the shared delete**

`aiApi.js` had its own `discardWidget` hitting the same endpoint. One endpoint, one function. In `src/features/dashboard/aiApi.js`, delete this block:

```js
// Throw away a draft we decided not to keep (soft delete on the backend), so rejected
// drafts do not pile up in the widget table.
export function discardWidget(id) {
  return axiosClient.delete(`/widgets/${id}`)
}
```

Then in `src/features/dashboard/aiHooks.js`, change the import line:

```js
import { generateQuery, approveWidget, discardWidget } from './aiApi.js'
```

to:

```js
import { generateQuery, approveWidget } from './aiApi.js'
import { deleteWidget } from './widgetsApi.js'
```

and change the last hook:

```js
export function useDiscardWidget() {
  return useMutation({ mutationFn: discardWidget })
}
```

to:

```js
// Discarding an AI draft is the same operation as deleting a widget from the library.
export function useDiscardWidget() {
  return useMutation({ mutationFn: deleteWidget })
}
```

- [ ] **Step 4: Fetch both widget modules, and add a delete hook**

In `src/features/dashboard/boardsHooks.js`, add `useMemo` to the React import at the top of the file:

```js
import { useMemo } from 'react'
```

Replace `useAvailableWidgets`:

```js
export function useAvailableWidgets() {
  return useQuery({
    queryKey: ['widgets', 'WIDGET'],
    queryFn: getWidgets,
    select: (page) => page?.content ?? [], // unwrap the Spring page
  })
}
```

with:

```js
// Everything the gallery can offer: hand-built widgets plus APPROVED AI ones.
// Two calls rather than one, because the backend filters by a single module. React Query
// caches both, so reopening the gallery costs nothing.
export function useGalleryWidgets() {
  const built = useQuery({
    queryKey: ['widgets', 'WIDGET'],
    queryFn: () => getWidgets('WIDGET'),
    select: (page) => page?.content ?? [], // unwrap the Spring page
  })
  const ai = useQuery({
    queryKey: ['widgets', 'PROMPT'],
    queryFn: () => getWidgets('PROMPT'),
    // Drafts are half-finished by definition - only approved AI widgets belong in a picker.
    select: (page) => (page?.content ?? []).filter((w) => w.status === 'APPROVED'),
  })

  // useMemo, not a bare array literal: a fresh array every render would retrigger any
  // effect or memo downstream that depends on this list.
  const data = useMemo(() => [...(built.data ?? []), ...(ai.data ?? [])], [built.data, ai.data])

  return {
    data,
    isLoading: built.isLoading || ai.isLoading,
    error: built.error || ai.error,
  }
}

// Remove a widget from the library. Invalidates BOTH gallery lists so the card disappears
// without a reload.
export function useDeleteWidget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: deleteWidget,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['widgets'] }),
  })
}
```

Finally update the import line near the top of `boardsHooks.js`:

```js
import { getWidgets, getWidgetData } from './widgetsApi.js'
```

to:

```js
import { getWidgets, getWidgetData, deleteWidget } from './widgetsApi.js'
```

- [ ] **Step 5: Fix the now-broken import in `AddWidgetDrawer`**

`AddWidgetDrawer.jsx` imports `useAvailableWidgets`, which no longer exists — the build will fail. It is deleted in Task 2, but leaving the tree unbuildable between tasks is worse. Point it at the new hook for now: in `src/features/dashboard/AddWidgetDrawer.jsx` change

```js
import { useAvailableWidgets } from './boardsHooks.js'
```

to

```js
import { useGalleryWidgets } from './boardsHooks.js'
```

and inside `WidgetLibrary` change

```js
  const { data: widgets = [], isLoading, error } = useAvailableWidgets()
```

to

```js
  const { data: widgets = [], isLoading, error } = useGalleryWidgets()
```

- [ ] **Step 6: Build, lint, and check nothing regressed**

```bash
npm run build
```
Expected: `✓ built in …`. (The "chunks are larger than 500 kB" warning is pre-existing.)

```bash
npm run lint
```
Expected: no output.

```bash
grep -rn "useAvailableWidgets\|nextPosition\|setWidth\|discardWidget" src/ || echo "CLEAN"
```
Expected: `CLEAN`. Any hit is a dangling reference to something this task deleted.

Then open the app (`preview_start` with `{url: "http://localhost:5173"}`), and confirm with `read_console_messages` (`onlyErrors: true`) that there are no new errors and the board still shows 13 widgets. The "Add widget" drawer should now also list AI widgets — that is the `useGalleryWidgets` change working.

- [ ] **Step 7: Report, do not commit**

```bash
git status --short src/features/dashboard/
```

> Every file in `src/features/dashboard/` is the user's untracked work. Committing sweeps their tree into your commit, and a partial commit breaks the build because these files import each other. **Do not commit without asking.** Report what changed and let the user decide.

---

### Task 2: The gallery

**Files:**
- Create: `src/features/dashboard/WidgetBody.jsx`
- Create: `src/features/dashboard/WidgetPreviewCard.jsx`
- Create: `src/features/dashboard/WidgetGallery.jsx`
- Modify: `src/features/dashboard/WidgetCard.jsx`
- Modify: `src/features/dashboard/AskAiPanel.jsx`
- Delete: `src/features/dashboard/AddWidgetDrawer.jsx`

**Interfaces:**
- Consumes: `useGalleryWidgets()`, `useDeleteWidget()` (Task 1); `useWidgetData(idOrCode, pageSize)` (existing)
- Produces:
  - `WidgetBody({ type, rows })` — default export, renders rows by widget type
  - `WidgetPreviewCard({ widget, selected, disabled, onToggle })`
  - `WidgetGallery({ open, onClose, onConfirm, existingWidgetIds })` — `onConfirm(widgets)` receives the full gallery-widget objects, not ids

- [ ] **Step 1: Pull the rendering out of `WidgetCard`**

The gallery card and the board card must draw a widget the same way. Create `src/features/dashboard/WidgetBody.jsx`:

```jsx
import { Statistic, Table, Empty } from 'antd'
import { Chart as ChartJS, registerables } from 'chart.js'
import { Bar, Line, Pie } from 'react-chartjs-2'

ChartJS.register(...registerables) // one-time Chart.js setup

// Draws a widget's rows according to its type. Shared by the board card and the gallery
// preview card so the two can never drift apart.
//   COUNT -> a big number, TABLE -> a table, BAR/LINE/PIE -> a chart.
export default function WidgetBody({ type, rows = [], compact = false }) {
  if (rows.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No data" />
  }

  const columns = Object.keys(rows[0])

  if (type === 'COUNT') {
    // show the first value of the first row as a big number
    return <Statistic value={rows[0][columns[0]]} />
  }

  if (type === 'TABLE') {
    const tableCols = columns.map((c) => ({ title: c, dataIndex: c, key: c }))
    const dataSource = rows.map((r, i) => ({ key: i, ...r }))
    return (
      <Table
        size="small"
        columns={tableCols}
        dataSource={dataSource}
        pagination={compact ? false : { pageSize: 5 }}
        scroll={{ x: true }}
      />
    )
  }

  // charts: use the first column as labels and the second as numeric values
  const labelCol = columns[0]
  const valueCol = columns[1] ?? columns[0]
  const chartData = {
    labels: rows.map((r) => r[labelCol]),
    datasets: [
      {
        label: valueCol,
        data: rows.map((r) => Number(r[valueCol]) || 0),
        backgroundColor: ['#1677ff', '#52c41a', '#faad14', '#eb2f96', '#722ed1', '#13c2c2', '#fa541c'],
      },
    ],
  }

  // maintainAspectRatio must be OFF inside a sized box. With it on, Chart.js keeps its own
  // ratio and either overflows or leaves dead space when the box changes shape.
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: type === 'PIE' && !compact } },
  }

  const chart =
    type === 'BAR' ? <Bar data={chartData} options={options} />
    : type === 'LINE' ? <Line data={chartData} options={options} />
    : type === 'PIE' ? <Pie data={chartData} options={options} />
    : null

  if (!chart) return <Empty description={`Unsupported type: ${type}`} />
  return (
    <div style={{ position: 'relative', height: '100%', minHeight: compact ? 90 : 140 }}>
      {chart}
    </div>
  )
}
```

- [ ] **Step 2: Rewrite `WidgetCard` to use it, drop the Segmented control**

Width now comes from dragging, so the `1 / 2 / 3` control goes — this is the spec's decision 6, deferred from Plan 1. The trash only appears in edit mode. Replace the whole of `src/features/dashboard/WidgetCard.jsx`:

```jsx
import { Card, Spin, Alert, Popconfirm, Button } from 'antd'
import { DeleteOutlined, HolderOutlined } from '@ant-design/icons'
import { useWidgetData } from './boardsHooks.js'
import WidgetBody from './WidgetBody.jsx'

// Renders ONE widget on a board. It fetches its own data and hands the rows to WidgetBody.
//
// In edit mode it also shows a drag handle and a remove button. Width and height come from
// dragging the card's edges now, so there is no width control here any more.
export default function WidgetCard({ item, editable = false, onRemove }) {
  const { data, isLoading, error } = useWidgetData(item.code)
  const rows = data?.rows ?? []

  return (
    <Card
      title={item.name}
      size="small"
      // Fill the grid cell: the cell has a fixed pixel height, and the body scrolls
      // rather than the card overflowing it.
      style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
      styles={{ body: { flex: 1, overflow: 'auto' } }}
      extra={
        editable && (
          <Popconfirm title="Remove from board?" onConfirm={() => onRemove(item.widgetId)}>
            <Button size="small" type="text" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        )
      }
    >
      {editable && (
        // The whole card is draggable; this just tells the user so.
        <div style={{ textAlign: 'center', color: '#bbb', lineHeight: 1, marginBottom: 4 }}>
          <HolderOutlined />
        </div>
      )}
      {isLoading ? (
        <Spin />
      ) : error ? (
        <Alert type="error" message={error.message || 'Failed to load'} />
      ) : (
        <WidgetBody type={item.type} rows={rows} />
      )}
    </Card>
  )
}
```

- [ ] **Step 3: Write the gallery card**

Create `src/features/dashboard/WidgetPreviewCard.jsx`:

```jsx
import { Card, Tag, Spin, Checkbox, Popconfirm, Button, Typography, theme } from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import { useWidgetData } from './boardsHooks.js'
import WidgetBody from './WidgetBody.jsx'

const { Text } = Typography

// One card in the widget gallery: the widget's name, a LIVE mini preview of what it
// actually renders, and a tick to select it.
//
// The preview is the point - a name and a type tag tell you almost nothing, whereas
// seeing the real number or chart tells you whether you want it.
//
// A preview whose query fails degrades to a muted line and the card STAYS SELECTABLE:
// a broken preview should not stop you adding a widget you already know you want, nor
// make the whole gallery look broken.
export default function WidgetPreviewCard({ widget, selected, disabled, onToggle, onDelete }) {
  const { token } = theme.useToken()
  const { data, isLoading, error } = useWidgetData(widget.code, 5)
  const rows = data?.rows ?? []

  const isAi = widget.module === 'PROMPT'

  return (
    <Card
      size="small"
      hoverable={!disabled}
      onClick={() => !disabled && onToggle(widget)}
      style={{
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.55 : 1,
        borderColor: selected ? token.colorPrimary : undefined,
        borderWidth: selected ? 2 : 1,
      }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Checkbox checked={selected || disabled} disabled={disabled} />
          <span style={{ fontWeight: 500, whiteSpace: 'normal' }}>{widget.name}</span>
        </div>
      }
      extra={
        isAi && (
          // Deleting is restricted to AI widgets on purpose: those accumulate one per
          // question asked, whereas hand-built widgets are shared and other boards use them.
          <Popconfirm
            title={`Delete "${widget.name}" from the library?`}
            description="It disappears from every board."
            onConfirm={(e) => {
              e?.stopPropagation?.()
              onDelete(widget)
            }}
            onCancel={(e) => e?.stopPropagation?.()}
          >
            <Button
              size="small"
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Popconfirm>
        )
      }
    >
      <div style={{ height: 130, overflow: 'hidden' }}>
        {isLoading ? (
          <Spin size="small" />
        ) : error ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            Preview unavailable
          </Text>
        ) : (
          <WidgetBody type={widget.type} rows={rows} compact />
        )}
      </div>
      <div style={{ marginTop: 8 }}>
        <Tag>{widget.type}</Tag>
        {isAi && <Tag color="purple">AI</Tag>}
      </div>
    </Card>
  )
}
```

- [ ] **Step 4: Write the gallery itself**

Create `src/features/dashboard/WidgetGallery.jsx`:

```jsx
import { useEffect, useState } from 'react'
import { Modal, Tabs, Row, Col, Spin, Alert, Empty, Button, Space, Typography, App } from 'antd'
import { useGalleryWidgets, useDeleteWidget } from './boardsHooks.js'
import WidgetPreviewCard from './WidgetPreviewCard.jsx'
import AskAiPanel from './AskAiPanel.jsx'

const { Text } = Typography

// The widget picker. Tick as many as you like, then "Add selected".
//
// Widgets already on the board come through in existingWidgetIds: they show as ticked and
// disabled, so the same widget can never be added twice.
export default function WidgetGallery({ open, onClose, onConfirm, existingWidgetIds = [] }) {
  const { message } = App.useApp()
  const { data: widgets, isLoading, error } = useGalleryWidgets()
  const deleteWidget = useDeleteWidget()

  const [selected, setSelected] = useState([]) // gallery widget objects, not ids

  // Start clean each time the gallery opens - a stale selection from last time would be
  // added silently.
  useEffect(() => {
    if (open) setSelected([])
  }, [open])

  const toggle = (widget) =>
    setSelected((current) =>
      current.some((w) => w.id === widget.id)
        ? current.filter((w) => w.id !== widget.id)
        : [...current, widget],
    )

  const handleDelete = (widget) =>
    deleteWidget.mutate(widget.id, {
      onSuccess: () => {
        setSelected((current) => current.filter((w) => w.id !== widget.id))
        message.success(`Deleted "${widget.name}"`)
      },
      onError: (e) => message.error(e.message || 'Could not delete the widget'),
    })

  const handleConfirm = () => {
    onConfirm(selected)
    onClose()
  }

  const library = isLoading ? (
    <Spin />
  ) : error ? (
    <Alert type="error" message={error.message || 'Failed to load widgets'} />
  ) : widgets.length === 0 ? (
    <Empty description="No widgets yet. Try the Ask AI tab." />
  ) : (
    <Row gutter={[16, 16]}>
      {widgets.map((widget) => (
        <Col key={widget.id} xs={24} sm={12} md={8}>
          <WidgetPreviewCard
            widget={widget}
            selected={selected.some((w) => w.id === widget.id)}
            disabled={existingWidgetIds.includes(widget.id)}
            onToggle={toggle}
            onDelete={handleDelete}
          />
        </Col>
      ))}
    </Row>
  )

  return (
    <Modal
      title="Select widgets"
      open={open}
      onCancel={onClose}
      width={980}
      styles={{ body: { maxHeight: '65vh', overflow: 'auto' } }}
      footer={
        <Space>
          <Text type="secondary">
            {selected.length === 0 ? 'Nothing selected' : `${selected.length} selected`}
          </Text>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" disabled={selected.length === 0} onClick={handleConfirm}>
            Add selected
          </Button>
        </Space>
      }
    >
      <Tabs
        defaultActiveKey="library"
        items={[
          { key: 'library', label: 'Library', children: library },
          {
            key: 'ai',
            label: 'Ask AI',
            children: <AskAiPanel onAdd={(widget) => setSelected((c) => [...c, widget])} />,
          },
        ]}
      />
    </Modal>
  )
}
```

- [ ] **Step 5: Make `AskAiPanel` add to the selection**

It currently approves a draft, calls `onAdd({ id, name })`, and closes the drawer. In the gallery it should approve and drop the widget into the pending selection, leaving the modal open. Two changes in `src/features/dashboard/AskAiPanel.jsx`.

Change the signature:

```jsx
export default function AskAiPanel({ onAdd, onClose }) {
```

to:

```jsx
// onAdd receives a GALLERY WIDGET ({ id, code, name, type, module }) so the gallery can
// treat an AI result exactly like any other selected card.
export default function AskAiPanel({ onAdd }) {
```

Then replace `handleAdd`:

```jsx
  const handleAdd = () => {
    approve.mutate(draft.widgetId, {
      onSuccess: () => {
        onAdd({ id: draft.widgetId, name: draft.question })
        setDraft(null)
        setQuestion('')
        onClose?.()
      },
      onError: (e) => message.error(e.message || 'Could not add the widget'),
    })
  }
```

with:

```jsx
  const handleAdd = () => {
    approve.mutate(draft.widgetId, {
      onSuccess: () => {
        // Shape it like a gallery widget. AI drafts are always TABLE (SqlDraftService
        // hardcodes it), and always PROMPT.
        onAdd({
          id: draft.widgetId,
          code: draft.code,
          name: draft.question,
          type: 'TABLE',
          module: 'PROMPT',
          status: 'APPROVED',
        })
        setDraft(null)
        setQuestion('')
        message.success('Added to your selection')
      },
      onError: (e) => message.error(e.message || 'Could not add the widget'),
    })
  }
```

Finally change the button label so it says what it now does. There is one occurrence, the text inside the primary button in the draft-review block:

```jsx
              Add to board
```
becomes
```jsx
              Add to selection
```

- [ ] **Step 6: Delete the old drawer**

```bash
rm src/features/dashboard/AddWidgetDrawer.jsx
grep -rn "AddWidgetDrawer" src/ || echo "CLEAN"
```

Expected: `CLEAN` **except** for `DashboardPage.jsx`, which still imports and renders it — Task 4 replaces that. Until then the build is broken, which is why Tasks 2, 3 and 4 are verified together at the end of Task 4.

- [ ] **Step 7: Sanity-check what you just wrote**

```bash
grep -rn "onWidthChange\|Segmented" src/features/dashboard/ || echo "CLEAN"
```

Expected: hits only in `DashboardPage.jsx` and `BoardGrid.jsx` (both fixed in Tasks 3 and 4). If `WidgetCard.jsx` still appears, Step 2 did not take.

---

### Task 3: The grid, made editable

**Files:**
- Modify: `src/features/dashboard/BoardGrid.jsx`

**Interfaces:**
- Consumes: `applyRglLayout` (Task 1) — used by the caller, not here; `WidgetCard({ item, editable, onRemove })` (Task 2)
- Produces: `BoardGrid({ items, layoutKey, editable, onLayoutChange, onRemove })` where `onLayoutChange(rglLayout)` hands back RGL's `[{i,x,y,w,h}]`

> **The trap this task exists to avoid.** Plan 1 found that `GridLayout` reads `layout` only on mount, and worked around it by keying the grid on a signature of every item's position. If that survived into edit mode, **every drag would remount the grid mid-gesture** and the interaction would die. So `BoardGrid` no longer computes the key itself — the caller passes `layoutKey`, and in edit mode the caller bumps it only on add, remove or reset, never on drag.

- [ ] **Step 1: Rewrite `BoardGrid`**

Replace the whole of `src/features/dashboard/BoardGrid.jsx`:

```jsx
import { useMemo } from 'react'
import { Empty } from 'antd'
import GridLayout, { useContainerWidth, verticalCompactor } from 'react-grid-layout'
import WidgetCard from './WidgetCard.jsx'
import { COLS, ROW_HEIGHT } from './boardLayout.js'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

// Draws the widgets of one board on a 3-column grid. The SAME component renders the saved
// board and the editable draft - only `editable` differs - so the board cannot shift
// position when you enter edit mode.
//
// This is react-grid-layout v2, whose props differ from the v1 API most examples show:
// there is no WidthProvider (useContainerWidth replaces it), and cols/rowHeight/margin/
// isDraggable/isResizable/compactType all moved into config objects.
//
// TWO THINGS THAT WILL BITE YOU:
//
// 1. measureBeforeMount: true is required. Without it useContainerWidth reports its
//    default width of 1280 on the first render and the third column hangs off the edge.
//
// 2. `layout` is the INITIAL layout only - after mount GridLayout keeps its own state and
//    ignores the prop. So the grid is remounted via `layoutKey`. The CALLER owns that key
//    precisely because the right moment to remount differs between modes: in view mode
//    any change should remount, but in edit mode remounting on a drag would kill the drag.
export default function BoardGrid({
  items = [],
  layoutKey = 'static',
  editable = false,
  onLayoutChange,
  onRemove,
}) {
  const { width, containerRef, mounted } = useContainerWidth({ measureBeforeMount: true })

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
    return <Empty description="This board is empty. Add some widgets to get started." />
  }

  return (
    <div ref={containerRef}>
      {mounted && (
        <GridLayout
          key={layoutKey}
          className="layout"
          layout={layout}
          width={width}
          gridConfig={{ cols: COLS, rowHeight: ROW_HEIGHT, margin: [16, 16] }}
          // Buttons and scrollable tables inside a card must not start a drag.
          dragConfig={{ enabled: editable, cancel: '.ant-btn, .ant-table-wrapper, .ant-popover' }}
          resizeConfig={{ enabled: editable }}
          compactor={verticalCompactor}
          onLayoutChange={editable ? onLayoutChange : undefined}
        >
          {items.map((item) => (
            <div key={String(item.widgetId)}>
              <WidgetCard item={item} editable={editable} onRemove={onRemove} />
            </div>
          ))}
        </GridLayout>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Confirm no stale props remain**

```bash
grep -n "onWidthChange" src/features/dashboard/BoardGrid.jsx || echo "CLEAN"
```

Expected: `CLEAN`.

---

### Task 4: The draft, and the two modes

The task that makes it all work. The build is broken until this one finishes.

**Files:**
- Create: `src/features/dashboard/useBoardDraft.js`
- Modify: `src/features/dashboard/DashboardPage.jsx`

**Interfaces:**
- Consumes: `appendPacked`, `applyRglLayout`, `toSavePayload` (Task 1); `WidgetGallery` (Task 2); `BoardGrid` (Task 3); `useBoard`, `useSaveBoard` (existing)
- Produces: `useBoardDraft(board)` returning `{ items, revision, isDirty, addWidgets, removeWidget, applyLayout, reset }`

- [ ] **Step 1: Write the draft hook**

Create `src/features/dashboard/useBoardDraft.js`:

```js
import { useCallback, useMemo, useState } from 'react'
import { appendPacked, applyRglLayout, toSavePayload } from './boardLayout.js'

// Holds the board layout you are editing, before it is saved.
//
// This lives in its own file rather than inside DashboardPage for two reasons: the page
// stays a thin orchestrator instead of growing into a 400-line file, and the placement
// logic ends up somewhere it can be reasoned about on its own.
//
// About `revision`: BoardGrid must be remounted when the layout changes for a reason
// OTHER than a drag, because react-grid-layout ignores `layout` prop changes after mount.
// So revision is bumped by reset/add/remove - and deliberately NOT by applyLayout, since
// remounting mid-drag would kill the drag.
export function useBoardDraft(board) {
  const [items, setItems] = useState([])
  const [revision, setRevision] = useState(0)

  // Copy the saved board into the draft. Called when entering edit mode and on cancel.
  const reset = useCallback(() => {
    setItems(board?.items ?? [])
    setRevision((r) => r + 1)
  }, [board])

  const addWidgets = useCallback((widgets) => {
    setItems((current) => appendPacked(current, widgets))
    setRevision((r) => r + 1)
  }, [])

  const removeWidget = useCallback((widgetId) => {
    setItems((current) => current.filter((item) => item.widgetId !== widgetId))
    setRevision((r) => r + 1)
  }, [])

  // Drag / resize. No revision bump: the grid already shows this, and remounting would
  // interrupt the gesture.
  const applyLayout = useCallback((rglLayout) => {
    setItems((current) => applyRglLayout(current, rglLayout))
  }, [])

  // Compare only what actually gets saved - name/code/type are not editable here.
  const isDirty = useMemo(() => {
    const saved = JSON.stringify(toSavePayload(board?.items ?? []))
    const draft = JSON.stringify(toSavePayload(items))
    return saved !== draft
  }, [board, items])

  return { items, revision, isDirty, addWidgets, removeWidget, applyLayout, reset }
}
```

- [ ] **Step 2: Rewrite `DashboardPage`**

Replace the whole of `src/features/dashboard/DashboardPage.jsx`:

```jsx
import { useEffect, useState } from 'react'
import { Card, Select, Button, Space, Modal, Input, Empty, Spin, Popconfirm, Tag, App } from 'antd'
import {
  PlusOutlined,
  AppstoreAddOutlined,
  DeleteOutlined,
  EditOutlined,
  SaveOutlined,
  CloseOutlined,
} from '@ant-design/icons'
import {
  useBoards,
  useBoard,
  useCreateBoard,
  useSaveBoard,
  useDeleteBoard,
} from './boardsHooks.js'
import BoardGrid from './BoardGrid.jsx'
import WidgetGallery from './WidgetGallery.jsx'
import { useBoardDraft } from './useBoardDraft.js'
import { toSavePayload } from './boardLayout.js'

export default function DashboardPage() {
  const { message, modal } = App.useApp()
  const { data: boards = [], isLoading: boardsLoading } = useBoards()

  const [boardId, setBoardId] = useState(null)
  const [mode, setMode] = useState('view') // 'view' | 'edit'
  const [galleryOpen, setGalleryOpen] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [newName, setNewName] = useState('')

  // pick the first board once boards load (if none selected)
  useEffect(() => {
    if (!boardId && boards.length > 0) setBoardId(boards[0].id)
  }, [boards, boardId])

  const { data: board, isLoading: boardLoading } = useBoard(boardId)
  const createBoard = useCreateBoard()
  const saveBoard = useSaveBoard()
  const deleteBoard = useDeleteBoard()
  const draft = useBoardDraft(board)

  const editing = mode === 'edit'
  const savedItems = board?.items ?? []
  const shownItems = editing ? draft.items : savedItems

  // In view mode any layout change should remount the grid (there is no drag to protect).
  // In edit mode the draft's revision decides, and it deliberately ignores drags.
  const layoutKey = editing
    ? `edit-${boardId}-${draft.revision}`
    : `view-${boardId}-${savedItems.map((i) => [i.widgetId, i.x, i.y, i.w, i.h].join(',')).join('|')}`

  const startEditing = () => {
    draft.reset()
    setMode('edit')
  }

  const leaveEditing = () => {
    draft.reset()
    setMode('view')
  }

  const confirmDiscard = (onOk) => {
    if (!draft.isDirty) {
      onOk()
      return
    }
    modal.confirm({
      title: 'Discard your changes?',
      content: 'The layout you have arranged has not been saved.',
      okText: 'Discard',
      okButtonProps: { danger: true },
      onOk,
    })
  }

  const handleSave = () =>
    saveBoard.mutate(
      { id: boardId, payload: { items: toSavePayload(draft.items) } },
      {
        onSuccess: () => {
          setMode('view')
          message.success('Board saved')
        },
        onError: (e) => message.error(e.message || 'Save failed'),
      },
    )

  const handleSelectBoard = (nextId) =>
    editing ? confirmDiscard(() => { leaveEditing(); setBoardId(nextId) }) : setBoardId(nextId)

  const handleCreate = () => {
    if (!newName.trim()) return
    createBoard.mutate(
      { name: newName.trim() },
      {
        onSuccess: (created) => {
          setBoardId(created.id)
          setCreateOpen(false)
          setNewName('')
          // A brand-new board has nothing to arrange, so go straight to picking widgets.
          setMode('edit')
          setGalleryOpen(true)
          message.success('Board created')
        },
        onError: (e) => message.error(e.message || 'Create failed'),
      },
    )
  }

  const handleDeleteBoard = () => {
    deleteBoard.mutate(boardId, {
      onSuccess: () => {
        setBoardId(null)
        setMode('view')
        message.success('Board deleted')
      },
    })
  }

  if (boardsLoading) return <Spin />

  return (
    <Card
      title={
        <Space wrap>
          <Select
            style={{ minWidth: 220 }}
            placeholder="Select a board"
            value={boardId ?? undefined}
            onChange={handleSelectBoard}
            options={boards.map((b) => ({ label: b.name, value: b.id }))}
            notFoundContent="No boards yet"
          />
          <Button icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New board
          </Button>
          {editing && <Tag color="processing">Editing</Tag>}
        </Space>
      }
      extra={
        boardId &&
        (editing ? (
          <Space>
            <Button icon={<AppstoreAddOutlined />} onClick={() => setGalleryOpen(true)}>
              Add widgets
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saveBoard.isPending}
              onClick={handleSave}
            >
              Save
            </Button>
            <Button icon={<CloseOutlined />} onClick={() => confirmDiscard(leaveEditing)}>
              Cancel
            </Button>
          </Space>
        ) : (
          <Space>
            <Button type="primary" icon={<EditOutlined />} onClick={startEditing}>
              Edit
            </Button>
            <Popconfirm title="Delete this board?" onConfirm={handleDeleteBoard}>
              <Button danger icon={<DeleteOutlined />}>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        ))
      }
    >
      {!boardId ? (
        <Empty description="No board selected. Create one to get started.">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New board
          </Button>
        </Empty>
      ) : boardLoading ? (
        <Spin />
      ) : (
        <BoardGrid
          items={shownItems}
          layoutKey={layoutKey}
          editable={editing}
          onLayoutChange={draft.applyLayout}
          onRemove={draft.removeWidget}
        />
      )}

      <WidgetGallery
        open={galleryOpen}
        onClose={() => setGalleryOpen(false)}
        onConfirm={draft.addWidgets}
        existingWidgetIds={draft.items.map((i) => i.widgetId)}
      />

      <Modal
        title="Create a new board"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={createBoard.isPending}
        okText="Create"
      >
        <Input
          placeholder="Board name"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onPressEnter={handleCreate}
          autoFocus
        />
      </Modal>
    </Card>
  )
}
```

- [ ] **Step 3: Build and lint**

```bash
npm run build
```
Expected: `✓ built in …`, no errors. If it fails on a missing export, something from Tasks 1–3 was skipped.

```bash
npm run lint
```
Expected: no output.

```bash
grep -rn "AddWidgetDrawer\|useAvailableWidgets\|onWidthChange\|nextPosition\|setWidth" src/ || echo "CLEAN"
```
Expected: `CLEAN`.

---

### Task 5: Verify the whole thing against the real board

**Files:** none — verification only.

- [ ] **Step 1: Record the current layout so you can restore it**

```bash
curl -s http://localhost:8080/api/v1/boards/1 > /tmp/board-before.json
tr '{' '\n' < /tmp/board-before.json | grep -o '"widgetId":[0-9]*.*"x":[0-9]*,"y":[0-9]*,"w":[0-9]*,"h":[0-9]*'
```

Expected: 13 rows. **Keep this output** — Step 8 restores it.

- [ ] **Step 2: Open the app and check view mode**

`preview_start` with `{url: "http://localhost:5173"}`, then `read_console_messages` (`onlyErrors: true`).

Expected: no errors. The board shows 13 widgets, an **Edit** button, and **no** `1/2/3` controls or trash icons on the cards.

- [ ] **Step 3: Enter edit mode and drag**

Click **Edit**. Each card should gain a small handle icon and a trash button, and a blue "Editing" tag appears by the board name.

Drag a card to a different position. Check with `javascript_tool`:

```js
(() => { const items=[...document.querySelectorAll('.react-grid-item')]; return JSON.stringify({ count: items.length, positions: items.slice(0,4).map(e => ({ n: e.querySelector('.ant-card-head-title')?.innerText.trim().slice(0,22), left: Math.round(e.getBoundingClientRect().left), top: Math.round(e.getBoundingClientRect().top) })) }); })()
```

Expected: the dragged card is somewhere new and the count is still 13. **Critically: the drag must complete in one gesture.** If the card snaps back or the grid flickers, `layoutKey` is changing on drag — check that `applyLayout` does not bump `revision`.

- [ ] **Step 4: Resize, and confirm the 3-column limit**

Drag a card's bottom-right corner right and down. It should grow. Then try to make a card in the **right-hand** column 3 wide — the grid should refuse to let it exceed the row.

```js
(() => { const items=[...document.querySelectorAll('.react-grid-item')]; const grid=document.querySelector('.react-grid-layout').getBoundingClientRect(); return JSON.stringify({ overflow: items.some(e => e.getBoundingClientRect().right > grid.right + 2) }); })()
```

Expected: `{"overflow":false}`.

- [ ] **Step 5: Add widgets from the gallery**

Click **Add widgets**. Expected: a modal of cards, each showing a real number/chart/table — not just a name. Widgets already on the board are ticked and greyed out.

Tick two unused widgets, click **Add selected**. Expected: the modal closes and both appear at the bottom of the board, **side by side** (`appendPacked` puts them on the same row), giving 15 cards.

If a card shows "Preview unavailable", that widget's query failed — it should still be selectable. That is intended.

- [ ] **Step 6: Cancel discards, Save persists**

With those two still unsaved, click **Cancel**. Expected: a confirm dialog; on Discard, the board returns to 13 widgets in their original positions, and **nothing was written** — verify:

```bash
curl -s http://localhost:8080/api/v1/boards/1 | tr ',' '\n' | grep -c widgetId
```
Expected: `13`.

Now click **Edit**, add one widget, move it, and click **Save**. Expected: "Board saved", back to view mode. Verify it stuck:

```bash
curl -s http://localhost:8080/api/v1/boards/1 | tr ',' '\n' | grep -c widgetId
```
Expected: `14`. Reload the page — the layout must come back exactly as saved.

- [ ] **Step 7: Delete an AI widget from the library**

Open **Add widgets**. AI widgets carry a purple **AI** tag and a trash button; hand-built ones have no trash button — confirm that difference, because it is the safety rule from the spec.

Delete one AI widget you do not want. Expected: a confirm naming the widget, then the card disappears without a reload. Verify:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/widgets/{code-of-deleted-widget}
```
Expected: `404`.

- [ ] **Step 8: Put the user's board back**

Rebuild the payload from Step 1's output and PUT it:

```bash
curl -s -w "\nHTTP=%{http_code}\n" -X PUT http://localhost:8080/api/v1/boards/1 -H "Content-Type: application/json" -d '{"items":[ ... the 13 items from Step 1, each as {"widgetId":N,"x":N,"y":N,"w":N,"h":N} ... ]}'
```

Expected: `HTTP=200`. Then confirm:

```bash
curl -s http://localhost:8080/api/v1/boards/1 | tr '{' '\n' | grep -o '"code":"[^"]*".*"x":[0-9]*,"y":[0-9]*,"w":[0-9]*'
```

Expected: identical to Step 1 — 13 widgets, `2+1` on the first row, then `1+1+1` rows.

- [ ] **Step 9: Final build, lint, and report**

```bash
npm run build && npm run lint
```
Expected: builds clean, lint silent.

```bash
git status --short src/features/dashboard/
```

**Do not commit.** Every file here is the user's untracked work with your edits on top, and a partial commit breaks the build because these files import each other. Report what changed and let the user decide.

---

## Done when

- View mode shows the saved board with an **Edit** button and no per-card controls.
- Edit mode drags and resizes in one gesture, with no snap-back.
- A row never exceeds 3 columns, and nothing overflows the grid.
- The gallery shows a **live preview** on each card; a failing preview degrades to "Preview unavailable" and stays selectable.
- Multi-select adds several widgets at once, packed onto a row; already-added widgets are ticked and disabled.
- AI widgets carry an **AI** tag and a delete button; hand-built widgets do not.
- **Cancel** writes nothing; **Save** persists and survives a reload.
- The user's board is restored to its original 13-widget layout.
- `npm run build` and `npm run lint` are clean.

## Known gaps, stated rather than hidden

- **No frontend tests.** `useBoardDraft`, `appendPacked` and `applyRglLayout` are pure logic that deserve unit tests, but this project has no test runner and adding Vitest is a decision for the user, not a side effect of this plan.
- **An approved AI widget stays on the board it was created from only.** The gallery now lists PROMPT widgets, so this limitation from the spec is actually resolved — an AI widget can be added to any board.
- **No responsive breakpoints.** The grid is fixed at 3 columns at every screen size, per the spec's out-of-scope list.
- **No undo/redo** in the builder. Cancel is the only escape.
