# Walkthrough Popup Geometry Persistence

## Goal

Persist the walkthrough popup's position and size in project-scoped settings so
the popup re-appears in the same place across walkthrough sessions (and across
IDE restarts), not just across Next/Previous clicks within a single session. On
every show, apply the persisted geometry and then nudge the popup out of the way
if it would overlap the target line. Save the geometry on every drag and resize.

## Background

Today the popup geometry is computed at show time and lives only in the popup
Swing component. A recent change (commit `6681cc7`) introduced an in-memory
`userMovedPopup` flag so that, within a single walkthrough session, clicking
Next does not snap the popup back next to the target line. That fix is correct
in spirit but limited: it does not survive ending one walkthrough and starting
another, and it does not survive an IDE restart. Issue
[#23](https://github.com/forketyfork/walkthrough-plugin/issues/23) tracks the
broader bug.

The plugin already has two relevant pieces:

- `WalkthroughSettings` — an application-level `PersistentStateComponent` for
  the color palette. Wrong scope for popup geometry (per-application, not
  per-project).
- `WalkthroughHistoryService` — a project-level service that writes to
  `.idea/walkthroughs/`. Correct pattern to mirror for project-scoped state.

## Decisions

| Decision                       | Choice                                                  |
| ------------------------------ | ------------------------------------------------------- |
| Scope                          | Per-project (under `.idea/`)                            |
| Coordinate system              | Absolute screen coordinates                             |
| First-time fallback            | Existing `movePopupNearItem` placement                  |
| Behavior on Next within session| Same as first show: load → avoid overlap → constrain    |
| Replacement of `userMovedPopup`| Yes — the in-memory flag is removed                     |
| Persistence triggers           | Once on mouse release after a drag or resize, and once on initial show when the position is adjusted from what was loaded |

## Architecture

### New component: `WalkthroughPopupGeometryService`

Project-level `PersistentStateComponent` storing the most recently observed
popup geometry.

- Registered in `plugin.xml` as a `projectService`.
- `@Storage("walkthroughPopup.xml")` → file lives under `.idea/walkthroughPopup.xml`.
- State fields: `x: Int`, `y: Int`, `width: Int`, `height: Int`.
- "Unset" sentinel: all four fields default to `Int.MIN_VALUE`. The service
  exposes `loadGeometry(): PopupGeometry?` returning `null` when unset.
- Mutation API: `saveGeometry(x, y, width, height)` updates state in place.
  Writes are idempotent — saving the same geometry twice is a no-op.

```kotlin
internal data class PopupGeometry(val x: Int, val y: Int, val width: Int, val height: Int)
```

### Unified placement pipeline

`WalkthroughOrchestrator` invokes a single `applyPopupGeometryForItem` function
both on first show and on every Next/Previous navigation:

1. **Load** persisted geometry from `WalkthroughPopupGeometryService`.
2. **Choose starting rectangle:**
   - If persisted geometry exists, use it.
   - Else, fall back to `movePopupNearItem` (existing default placement).
3. **Clamp size** to `[MINIMUM_WIDTH_PX, rootPaneWidth - 2 * VIEWPORT_PADDING]`
   (and the height equivalent). This guards against a persisted size that no
   longer fits on the current monitor / window.
4. **Avoid overlap** with the target line by passing the candidate point
   through the existing `avoidLineOverlap` helper.
5. **Constrain** to the editor's root pane via the existing
   `constrainPopupScreenLocation`.
6. **Apply** the final rectangle to the popup.
7. **Persist back** if the final geometry differs from the loaded geometry
   (e.g., the overlap-avoid step shifted the popup, or the size was clamped).
   This way the user does not see the popup jump on the next show.

The function is pure with respect to the persistence service in this sense:
given persisted geometry, target line, and root-pane bounds, it returns a
deterministic final rectangle. That separation lets us unit-test the reducer
without Swing.

### Save hooks (drag and resize)

The orchestrator already wires a `onPopupMoved` callback through
`installPopupInteractionHandler`. The recent commit repurposed it to flip
`userMovedPopup`; this design replaces that with a save fired only when the
interaction completes:

- Rename `onPopupMoved` → `onInteractionEnd` in `WalkthroughPopupInteraction`.
- Stop invoking the callback from inside `movePopupBy` / `resizePopupBy`. Drop
  the `onLocationChanged` parameter from both helpers — per-motion redraws are
  already handled by `setPopupScreenLocation` (which calls `repaint()` on the
  surface) and the `popupSize` setter (which also calls `repaint()`), so no
  external trigger is needed for the connector to follow the popup during a
  drag.
- Fire `onInteractionEnd()` from `mouseReleased`, but only when
  `interactionMode != null` (i.e. the press began on the drag handle or resize
  handle). This avoids firing on stray clicks elsewhere on the popup.
- Signature stays `() -> Unit`. Inside the callback the orchestrator queries
  `popup.popupLocationOnScreen()` and `resolvePopupSize(popup)` and calls
  `geometryService.saveGeometry(x, y, w, h)`. Keeping the interaction module
  ignorant of the geometry service preserves the existing module boundary.

The result: exactly one save per completed drag and one per completed resize,
regardless of how many `mouseDragged` events fired between press and release.
A press-release with no motion still triggers one save, which is a harmless
no-op because the persisted geometry is unchanged.

### Files touched

| File                                       | Change                                                                                                                              |
| ------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| `WalkthroughPopupGeometryService.kt` (new) | `PersistentStateComponent` with load/save API and `PopupGeometry` data class.                                                       |
| `META-INF/plugin.xml`                      | Register `<projectService>` for `WalkthroughPopupGeometryService`.                                                                  |
| `WalkthroughOrchestrator.kt`               | Remove `userMovedPopup` and `onPopupUserMoved`. Replace `repositionPopupForItem` with `applyPopupGeometryForItem`. Wire save hook. |
| `WalkthroughPopupPlacement.kt`             | Add a helper that takes a starting `Rectangle`, target line, editor, and returns the constrained, overlap-avoided rectangle.        |
| `WalkthroughPopupInteraction.kt`           | Rename `onPopupMoved` → `onGeometryChanged`; pass screen rectangle to the callback.                                                 |

## Edge cases

- **No persisted geometry yet (first walkthrough in project):** fall back to
  `movePopupNearItem`. The result is then persisted, so the second walkthrough
  starts from the same spot.
- **Persisted position is off-screen (monitor unplugged, IDE window moved):**
  `constrainPopupScreenLocation` clamps the popup back inside the root pane.
  The clamped rectangle is then persisted, so the next show is stable.
- **Persisted size larger than current root pane:** clamped to the root-pane
  width/height minus viewport padding. Clamped size is persisted.
- **Persisted position overlaps the new target line:** `avoidLineOverlap`
  shifts the popup above or below the line. Shifted position is persisted.
- **Switching editors mid-walkthrough:** geometry is in screen coordinates, so
  it applies the same way regardless of which editor's layered pane currently
  hosts the popup.

## Testing

- **Unit (no Swing):** the new pure reducer
  `applyPopupGeometryForItem(starting, line, editor, rootPaneBounds) →
  finalRect` is testable. Cover: (a) persisted-and-fine path, (b) overlap-
  avoidance shifts it, (c) constraint clamps it back, (d) size clamp for
  oversized persisted geometry.
- **Manual (`runIde`):**
  - Start walkthrough → drag popup → click Next → popup stays where dragged.
  - Close walkthrough → start another → popup re-appears at last drag position.
  - Drag popup over a future target line → click Next → popup nudges aside.
  - Restart IDE → start walkthrough → popup re-appears at last drag position.

## Non-goals

- Persisting per-walkthrough geometry. Geometry is project-wide; all
  walkthroughs in a project share the same remembered placement.
- Persisting connector style, palette overrides, or other UI state.
- Multi-window persistence subtleties (e.g., remembering different positions
  per monitor). Screen coords plus constraint clamping are accepted as
  sufficient.
