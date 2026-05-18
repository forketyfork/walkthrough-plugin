# Walkthrough Popup Geometry Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the walkthrough popup's screen position and size across walkthrough sessions and IDE restarts, with overlap-avoidance and root-pane clamping reapplied on every show.

**Architecture:** Extend the application-level `WalkthroughSettings` with four geometry fields and a small `PopupGeometry` data class. Add a pure `clampPopupSize` helper. Replace the in-memory `userMovedPopup` machinery in `WalkthroughOrchestrator` with a unified `applyPopupGeometryForItem` pipeline that loads persisted geometry (or falls back to `movePopupNearItem`), clamps size, runs the existing `avoidLineOverlap` + `constrainPopupScreenLocation` helpers, applies the result, and persists back when the result differs. Save geometry once from `mouseReleased` in the popup interaction handler.

**Tech Stack:** Kotlin 2.3.21, IntelliJ Platform 2026.1, JUnit Jupiter, JetBrains Compose / Jewel.

**Source spec:** `docs/superpowers/specs/2026-05-18-popup-geometry-persistence-design.md`.

---

## Task 1: Extend `WalkthroughSettings` with `PopupGeometry`

**Files:**
- Modify: `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughSettings.kt`
- Create: `src/test/kotlin/com/forketyfork/walkthrough/WalkthroughSettingsGeometryTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/forketyfork/walkthrough/WalkthroughSettingsGeometryTest.kt`:

```kotlin
package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class WalkthroughSettingsGeometryTest {

    @Test
    fun loadGeometryReturnsNullWhenStateIsUnset() {
        val settings = WalkthroughSettings()
        assertNull(settings.loadGeometry())
    }

    @Test
    fun saveGeometryThenLoadGeometryRoundTrips() {
        val settings = WalkthroughSettings()
        val geometry = PopupGeometry(x = 120, y = 240, width = 720, height = 480)

        settings.saveGeometry(geometry)

        assertEquals(geometry, settings.loadGeometry())
    }

    @Test
    fun loadGeometryReturnsNullWhenAnyFieldIsSentinel() {
        val settings = WalkthroughSettings()
        settings.saveGeometry(PopupGeometry(x = 100, y = 100, width = 800, height = 600))
        val state = settings.state
        state.popupX = Int.MIN_VALUE

        assertNull(settings.loadGeometry())
    }

    @Test
    fun stateSerializationRoundTripsGeometry() {
        val original = WalkthroughSettings()
        original.saveGeometry(PopupGeometry(x = 10, y = 20, width = 600, height = 400))
        val serialized = original.state

        val restored = WalkthroughSettings()
        restored.loadState(serialized)

        assertEquals(
            PopupGeometry(x = 10, y = 20, width = 600, height = 400),
            restored.loadGeometry()
        )
    }

    @Test
    fun saveGeometryIsIdempotent() {
        val settings = WalkthroughSettings()
        val geometry = PopupGeometry(x = 1, y = 2, width = 600, height = 400)
        settings.saveGeometry(geometry)
        val stateBefore = settings.state

        settings.saveGeometry(geometry)

        assertSame(stateBefore, settings.state)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
./gradlew test --tests com.forketyfork.walkthrough.WalkthroughSettingsGeometryTest
```

Expected: compilation failure — `PopupGeometry` is not defined, `loadGeometry()` / `saveGeometry()` / `state.popupX` do not exist.

- [ ] **Step 3: Extend `WalkthroughSettings`**

Replace the body of `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughSettings.kt` with:

```kotlin
package com.forketyfork.walkthrough

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

internal data class PopupGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

@State(
    name = "com.forketyfork.walkthrough.WalkthroughSettings",
    storages = [Storage("com.forketyfork.walkthrough.settings.xml")]
)
internal class WalkthroughSettings : PersistentStateComponent<WalkthroughSettings.State> {
    internal var state = State()
        private set

    var selectedPaletteId: String
        get() = state.selectedPaletteId
        set(value) {
            val palette = WalkthroughPalettes.byId(value)
            if (state.selectedPaletteId == palette.id) {
                return
            }
            state.selectedPaletteId = palette.id
            notifyPaletteChanged(palette)
        }

    val selectedPalette: WalkthroughPalette
        get() = WalkthroughPalettes.byId(selectedPaletteId)

    fun loadGeometry(): PopupGeometry? {
        val s = state
        if (s.popupX == Int.MIN_VALUE ||
            s.popupY == Int.MIN_VALUE ||
            s.popupWidth == Int.MIN_VALUE ||
            s.popupHeight == Int.MIN_VALUE
        ) {
            return null
        }
        return PopupGeometry(s.popupX, s.popupY, s.popupWidth, s.popupHeight)
    }

    fun saveGeometry(geometry: PopupGeometry) {
        val s = state
        if (s.popupX == geometry.x &&
            s.popupY == geometry.y &&
            s.popupWidth == geometry.width &&
            s.popupHeight == geometry.height
        ) {
            return
        }
        s.popupX = geometry.x
        s.popupY = geometry.y
        s.popupWidth = geometry.width
        s.popupHeight = geometry.height
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        this.state.selectedPaletteId = WalkthroughPalettes.byId(state.selectedPaletteId).id
    }

    class State {
        var selectedPaletteId: String = WalkthroughPalettes.default.id
        var popupX: Int = Int.MIN_VALUE
        var popupY: Int = Int.MIN_VALUE
        var popupWidth: Int = Int.MIN_VALUE
        var popupHeight: Int = Int.MIN_VALUE
    }

    private fun notifyPaletteChanged(palette: WalkthroughPalette) {
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(WalkthroughSettingsListener.TOPIC)
            .paletteChanged(palette)
    }

    companion object {
        fun getInstance(): WalkthroughSettings =
            ApplicationManager.getApplication().getService(WalkthroughSettings::class.java)
    }
}
```

Note the change to `state`: it is now exposed as `internal var ... private set` so the geometry test can mutate a single field to verify the partial-sentinel guard. All other callers continue to use the public `loadGeometry()` / `saveGeometry()` / `selectedPaletteId` API.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
./gradlew test --tests com.forketyfork.walkthrough.WalkthroughSettingsGeometryTest
```

Expected: all 5 tests pass.

- [ ] **Step 5: Run lint and build**

Run:
```bash
just lint && just build
```

Expected: both succeed.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/forketyfork/walkthrough/WalkthroughSettings.kt \
        src/test/kotlin/com/forketyfork/walkthrough/WalkthroughSettingsGeometryTest.kt
git commit -m "feat: persist popup geometry in WalkthroughSettings"
```

---

## Task 2: Add pure `clampPopupSize` helper

**Files:**
- Modify: `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt`
- Create: `src/test/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacementTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacementTest.kt`:

```kotlin
package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Dimension

class WalkthroughPopupPlacementTest {

    private val minWidth = WalkthroughPopupLayout.MINIMUM_WIDTH_PX
    private val minHeight = WalkthroughPopupLayout.MINIMUM_HEIGHT_PX

    @Test
    fun clampPopupSizePreservesSizeWithinBounds() {
        val clamped = clampPopupSize(Dimension(720, 480), maxWidth = 1200, maxHeight = 900)

        assertEquals(Dimension(720, 480), clamped)
    }

    @Test
    fun clampPopupSizeRaisesBelowMinimumToMinimum() {
        val clamped = clampPopupSize(
            Dimension(minWidth - 50, minHeight - 50),
            maxWidth = 1200,
            maxHeight = 900
        )

        assertEquals(Dimension(minWidth, minHeight), clamped)
    }

    @Test
    fun clampPopupSizeLowersAboveMaximumToMaximum() {
        val clamped = clampPopupSize(Dimension(2000, 1500), maxWidth = 1200, maxHeight = 900)

        assertEquals(Dimension(1200, 900), clamped)
    }

    @Test
    fun clampPopupSizeUsesMinimumWhenMaxIsBelowMinimum() {
        val clamped = clampPopupSize(
            Dimension(800, 500),
            maxWidth = minWidth - 100,
            maxHeight = minHeight - 100
        )

        assertEquals(Dimension(minWidth, minHeight), clamped)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
./gradlew test --tests com.forketyfork.walkthrough.WalkthroughPopupPlacementTest
```

Expected: compilation failure — `clampPopupSize` is not defined.

- [ ] **Step 3: Implement `clampPopupSize`**

Append to `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt` (after the existing `constrainPopupScreenLocation` function):

```kotlin
internal fun clampPopupSize(size: Dimension, maxWidth: Int, maxHeight: Int): Dimension {
    val minWidth = WalkthroughPopupLayout.MINIMUM_WIDTH_PX
    val minHeight = WalkthroughPopupLayout.MINIMUM_HEIGHT_PX
    val effectiveMaxWidth = maxWidth.coerceAtLeast(minWidth)
    val effectiveMaxHeight = maxHeight.coerceAtLeast(minHeight)
    return Dimension(
        size.width.coerceIn(minWidth, effectiveMaxWidth),
        size.height.coerceIn(minHeight, effectiveMaxHeight)
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
./gradlew test --tests com.forketyfork.walkthrough.WalkthroughPopupPlacementTest
```

Expected: all 4 tests pass.

- [ ] **Step 5: Run lint and build**

Run:
```bash
just lint && just build
```

Expected: both succeed.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt \
        src/test/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacementTest.kt
git commit -m "feat: add clampPopupSize pure helper"
```

---

## Task 3: Wire persistence into orchestrator and interaction handler

This task replaces the in-memory `userMovedPopup` machinery with the
persistence-driven pipeline. The interaction handler's callback contract
changes to fire once on `mouseReleased`, and the orchestrator gains a unified
`applyPopupGeometryForItem` used for both the initial show and every
Next/Previous navigation.

**Files:**
- Modify: `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt`
- Modify: `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupInteraction.kt`
- Modify: `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughOrchestrator.kt`

- [ ] **Step 1: Drop the `onLocationChanged` parameter from `movePopupBy`**

In `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt`, replace `movePopupBy` with:

```kotlin
internal fun movePopupBy(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    deltaX: Float,
    deltaY: Float
) {
    val currentLocation = popup.popupLocationOnScreen() ?: return
    val popupSize = resolvePopupSize(popup) ?: WalkthroughPopupLayout.fallbackSize
    val movedPoint = Point(
        currentLocation.x + deltaX.roundToInt(),
        currentLocation.y + deltaY.roundToInt()
    )
    popup.setPopupScreenLocation(constrainPopupScreenLocation(editor, movedPoint, popupSize))
}
```

The connector repaint is already triggered by `setPopupScreenLocation` itself
(it calls `repaint()` on the surface), so no external callback is needed.

- [ ] **Step 2: Update `WalkthroughPopupInteraction.kt`**

Replace the entire file `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupInteraction.kt` with:

```kotlin
package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import java.awt.Color as AwtColor
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.SwingUtilities

private const val DRAG_HANDLE_HEIGHT_PX = 64
private const val CLOSE_BUTTON_HIT_BOX_PX = 60
private const val RESIZE_HANDLE_SIZE_PX = 26
private const val INTERACTION_LISTENER_CLIENT_PROPERTY = "walkthrough.popup.interaction.listener"
private const val INTERACTION_CONTAINER_LISTENER_CLIENT_PROPERTY =
    "walkthrough.popup.interaction.container.listener"

private enum class PopupInteractionMode {
    Drag,
    Resize
}

internal fun makeComponentHierarchyTransparent(component: Component?) {
    var current = component
    while (current is JComponent) {
        if (current is JRootPane || current is JLayeredPane) {
            break
        }
        current.isOpaque = false
        @Suppress("UseJBColor") // Fully transparent, theme-independent
        current.background = AwtColor(0, 0, 0, 0)
        current = current.parent
    }
}

internal fun installPopupInteractionHandler(
    panel: JComponent,
    popupProvider: () -> WalkthroughPopupSurface?,
    editorProvider: () -> Editor,
    onInteractionEnd: () -> Unit
) {
    var lastScreenPoint: Point? = null
    var interactionMode: PopupInteractionMode? = null

    val interactionListener = object : MouseAdapter() {
        override fun mousePressed(event: MouseEvent) {
            if (event.button != MouseEvent.BUTTON1) {
                interactionMode = null
                lastScreenPoint = null
                return
            }
            interactionMode = when {
                isWithinResizeHandle(panel, event.component, event.point) -> PopupInteractionMode.Resize
                isWithinDragHandle(panel, event.component, event.point) -> PopupInteractionMode.Drag
                else -> null
            }
            lastScreenPoint = interactionMode?.let { event.locationOnScreen }
        }

        override fun mouseDragged(event: MouseEvent) {
            val mode = interactionMode ?: return
            val popup = popupProvider() ?: return
            val previousScreenPoint = lastScreenPoint ?: event.locationOnScreen
            val currentScreenPoint = event.locationOnScreen
            when (mode) {
                PopupInteractionMode.Drag -> movePopupBy(
                    popup = popup,
                    editor = editorProvider(),
                    deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
                    deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat()
                )

                PopupInteractionMode.Resize -> resizePopupBy(
                    popup = popup,
                    panel = panel,
                    editor = editorProvider(),
                    deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
                    deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat()
                )
            }
            lastScreenPoint = currentScreenPoint
        }

        override fun mouseMoved(event: MouseEvent) {
            updateInteractionCursor(panel, event.component, event.point)
        }

        override fun mouseReleased(event: MouseEvent) {
            val hadInteraction = interactionMode != null
            interactionMode = null
            lastScreenPoint = null
            updateInteractionCursor(panel, event.component, event.point)
            if (hadInteraction) {
                onInteractionEnd()
            }
        }

        override fun mouseExited(event: MouseEvent) {
            if (interactionMode == null) {
                event.component.cursor = Cursor.getDefaultCursor()
                panel.cursor = Cursor.getDefaultCursor()
            }
        }
    }

    attachMouseListenersRecursively(panel, interactionListener)
}

private fun attachMouseListenersRecursively(component: Component, listener: MouseAdapter) {
    if (component is JComponent) {
        if (component.getClientProperty(
                INTERACTION_LISTENER_CLIENT_PROPERTY
            ) !== listener
        ) {
            component.addMouseListener(listener)
            component.addMouseMotionListener(listener)
            component.putClientProperty(INTERACTION_LISTENER_CLIENT_PROPERTY, listener)
        }
    } else {
        component.addMouseListener(listener)
        component.addMouseMotionListener(listener)
    }
    if (component is Container) {
        component.components.forEach { child ->
            attachMouseListenersRecursively(child, listener)
        }
        if (component is JComponent &&
            component.getClientProperty(INTERACTION_CONTAINER_LISTENER_CLIENT_PROPERTY) == null
        ) {
            val containerListener = object : ContainerAdapter() {
                override fun componentAdded(event: ContainerEvent) {
                    attachMouseListenersRecursively(event.child, listener)
                }
            }
            component.addContainerListener(containerListener)
            component.putClientProperty(INTERACTION_CONTAINER_LISTENER_CLIENT_PROPERTY, containerListener)
        }
    }
}

private fun updateInteractionCursor(panel: JComponent, component: Component, point: Point) {
    val cursor = when {
        isWithinResizeHandle(panel, component, point) ->
            Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)

        isWithinDragHandle(panel, component, point) ->
            Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

        else -> Cursor.getDefaultCursor()
    }
    component.cursor = cursor
    panel.cursor = cursor
}

private fun isWithinDragHandle(panel: JComponent, component: Component, point: Point): Boolean {
    val pointInPanel = SwingUtilities.convertPoint(component, point, panel)
    return pointInPanel.y <= DRAG_HANDLE_HEIGHT_PX &&
        pointInPanel.x <= panel.width - CLOSE_BUTTON_HIT_BOX_PX
}

private fun isWithinResizeHandle(panel: JComponent, component: Component, point: Point): Boolean {
    val pointInPanel = SwingUtilities.convertPoint(component, point, panel)
    return pointInPanel.x >= panel.width - RESIZE_HANDLE_SIZE_PX &&
        pointInPanel.y >= panel.height - RESIZE_HANDLE_SIZE_PX
}

private fun resizePopupBy(
    popup: WalkthroughPopupSurface,
    panel: JComponent,
    editor: Editor,
    deltaX: Float,
    deltaY: Float
) {
    val currentLocation = popup.popupLocationOnScreen() ?: return
    val currentSize = resolvePopupSize(popup)
        ?: panel.preferredSize
        ?: WalkthroughPopupLayout.fallbackSize
    val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
    val maxWidth = rootPane?.let { pane ->
        val rootLocation = Point(0, 0).also { SwingUtilities.convertPointToScreen(it, pane) }
        (
            rootLocation.x + pane.width - currentLocation.x - WalkthroughPopupLayout.VIEWPORT_PADDING
            ).coerceAtLeast(WalkthroughPopupLayout.MINIMUM_WIDTH_PX)
    } ?: Int.MAX_VALUE
    val maxHeight = rootPane?.let { pane ->
        val rootLocation = Point(0, 0).also { SwingUtilities.convertPointToScreen(it, pane) }
        (
            rootLocation.y + pane.height - currentLocation.y - WalkthroughPopupLayout.VIEWPORT_PADDING
            ).coerceAtLeast(WalkthroughPopupLayout.MINIMUM_HEIGHT_PX)
    } ?: Int.MAX_VALUE
    val targetSize = java.awt.Dimension(
        (currentSize.width + deltaX.toInt()).coerceIn(WalkthroughPopupLayout.MINIMUM_WIDTH_PX, maxWidth),
        (currentSize.height + deltaY.toInt()).coerceIn(WalkthroughPopupLayout.MINIMUM_HEIGHT_PX, maxHeight)
    )

    panel.preferredSize = targetSize
    panel.revalidate()
    popup.popupSize = targetSize
    popup.moveToFitScreen(editor)
}
```

Three changes vs. the current file: the parameter on `installPopupInteractionHandler` is renamed `onPopupMoved` → `onInteractionEnd`; the per-motion callback is dropped from `movePopupBy` / `resizePopupBy` calls inside `mouseDragged`; `mouseReleased` now fires `onInteractionEnd()` when an interaction was in progress.

- [ ] **Step 3: Rewrite the orchestrator to use the persistence pipeline**

Replace the entire file `src/main/kotlin/com/forketyfork/walkthrough/WalkthroughOrchestrator.kt` with:

```kotlin
package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Color as AwtColor
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun showWalkthroughItems(project: Project, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, items, acceptsQuestions = false) != null

fun showWalkthroughItems(project: Project, editor: Editor, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, editor, items, acceptsQuestions = false) != null

fun showWalkthroughSession(
    project: Project,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean
): WalkthroughSession? {
    val fallbackEditor = FileEditorManager.getInstance(project).selectedTextEditor
    val target = items.firstOrNull()
        ?.let { item -> resolveWalkthroughTarget(project, fallbackEditor, item) }
    return target?.let { resolved -> showWalkthroughSession(project, resolved.editor, items, acceptsQuestions) }
}

@Suppress("LongMethod")
fun showWalkthroughSession(
    project: Project,
    editor: Editor,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean
): WalkthroughSession? {
    val firstTarget = items.firstOrNull()
        ?.let { item -> resolveWalkthroughTarget(project, editor, item) }
        ?: return null
    val paletteState = mutableStateOf(WalkthroughSettings.getInstance().selectedPalette)
    val sessionDisposable = Disposer.newCheckedDisposable("WalkthroughPopupSession")
    Disposer.register(project, sessionDisposable)

    val registry = WalkthroughSessionRegistry.getInstance(project)
    registry.swapActive(sessionDisposable)?.let(Disposer::dispose)
    val session = registry.create(items, acceptsQuestions)
    Disposer.register(
        sessionDisposable,
        object : Disposable {
            override fun dispose() {
                registry.remove(session.id)
                registry.clearActive(sessionDisposable)
            }
        }
    )

    var popupRef: WalkthroughPopupSurface? = null
    var currentEditor = firstTarget.editor
    var pendingNavigationId = 0

    fun repaintPopup() {
        popupRef?.let { popup ->
            popup.refreshBounds()
            popup.repaint()
        }
    }

    fun updatePopupPalette(palette: WalkthroughPalette) {
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed) {
                return@invokeLater
            }
            paletteState.value = palette
            popupRef?.updatePalette(palette)
        }
    }

    fun showItem(item: WalkthroughItem) {
        val popup = popupRef ?: return
        val target = resolveWalkthroughTarget(project, currentEditor, item) ?: return
        currentEditor = target.editor
        popup.update(currentEditor, target.popupItem)
        popup.connectorHidden = false
        applyPopupGeometryForItem(popup, currentEditor, target.popupItem)
    }

    fun scheduleItemNavigation(item: WalkthroughItem) {
        pendingNavigationId += 1
        val navigationId = pendingNavigationId
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed || navigationId != pendingNavigationId) {
                return@invokeLater
            }
            showItem(item)
        }
    }

    val panel = createWalkthroughPanel(
        project = project,
        session = session,
        paletteProvider = { paletteState.value },
        onItemDisplayed = ::scheduleItemNavigation,
        onNavigateToSource = ::scheduleItemNavigation,
        onClose = { popupRef?.cancel() }
    )
    makeComponentHierarchyTransparent(panel)

    installPopupInteractionHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        onInteractionEnd = { saveCurrentGeometry(popupRef) }
    )

    project.messageBus.connect(sessionDisposable).subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor
                popupRef?.connectorHidden = selectedEditor?.document !== currentEditor.document
            }
        }
    )
    ApplicationManager.getApplication().messageBus.connect(sessionDisposable).subscribe(
        WalkthroughSettingsListener.TOPIC,
        object : WalkthroughSettingsListener {
            override fun paletteChanged(palette: WalkthroughPalette) {
                updatePopupPalette(palette)
            }
        }
    )

    val popup = WalkthroughPopupSurface(
        content = panel,
        palette = paletteState.value,
        onCloseRequested = {
            popupRef = null
            registry.remove(session.id)
            Disposer.dispose(sessionDisposable)
        }
    )
    popupRef = popup
    Disposer.register(sessionDisposable, popup)
    popup.update(currentEditor, firstTarget.popupItem)
    applyPopupGeometryForItem(popup, currentEditor, firstTarget.popupItem)
    repaintPopup()
    return session
}

private fun saveCurrentGeometry(popup: WalkthroughPopupSurface?) {
    popup ?: return
    val location = popup.popupLocationOnScreen() ?: return
    val size = resolvePopupSize(popup) ?: return
    WalkthroughSettings.getInstance().saveGeometry(
        PopupGeometry(location.x, location.y, size.width, size.height)
    )
}

private fun applyPopupGeometryForItem(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    item: WalkthroughItem
) {
    val settings = WalkthroughSettings.getInstance()
    val persisted = settings.loadGeometry()

    if (persisted == null) {
        movePopupNearItem(popup, editor, item)
    } else {
        val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
        val maxWidth = rootPane?.let { it.width - 2 * WalkthroughPopupLayout.VIEWPORT_PADDING }
            ?: Int.MAX_VALUE
        val maxHeight = rootPane?.let { it.height - 2 * WalkthroughPopupLayout.VIEWPORT_PADDING }
            ?: Int.MAX_VALUE
        val clampedSize = clampPopupSize(
            Dimension(persisted.width, persisted.height),
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        popup.popupSize = clampedSize
        val avoided = avoidLineOverlap(Point(persisted.x, persisted.y), clampedSize, editor, item.line)
        val constrained = constrainPopupScreenLocation(editor, avoided, clampedSize)
        val reAvoided = avoidLineOverlap(constrained, clampedSize, editor, item.line)
        val finalPoint = constrainPopupScreenLocation(editor, reAvoided, clampedSize)
        popup.show(editor, finalPoint)
    }

    val finalLocation = popup.popupLocationOnScreen() ?: return
    val finalSize = resolvePopupSize(popup) ?: return
    val finalGeometry = PopupGeometry(finalLocation.x, finalLocation.y, finalSize.width, finalSize.height)
    if (persisted != finalGeometry) {
        settings.saveGeometry(finalGeometry)
    }
}

private fun createWalkthroughPanel(
    project: Project,
    session: WalkthroughSession,
    paletteProvider: () -> WalkthroughPalette,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onNavigateToSource: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
): JComponent =
    JewelComposePanel {
        WalkthroughItemContent(
            project = project,
            session = session,
            palette = paletteProvider(),
            onItemDisplayed = onItemDisplayed,
            onNavigateToSource = onNavigateToSource,
            onClose = onClose
        )
    }.apply {
        isOpaque = false
        @Suppress("UseJBColor")
        background = AwtColor(0, 0, 0, 0)
        minimumSize = Dimension(
            WalkthroughPopupLayout.MINIMUM_WIDTH_PX,
            WalkthroughPopupLayout.MINIMUM_HEIGHT_PX
        )
        preferredSize = WalkthroughPopupLayout.fallbackSize
    }
```

Notable diffs versus the current file:
- `var userMovedPopup`, `fun onPopupUserMoved()`, and the file-level
  `repositionPopupForItem` helper introduced in the prior commit are removed.
- A new file-level `saveCurrentGeometry(popup)` helper reads the current popup
  rectangle and writes through `WalkthroughSettings.saveGeometry(...)`. Kept
  at file scope (not as an inner function) so its `?: return` branches do not
  inflate the cyclomatic complexity of `showWalkthroughSession`.
- A new file-level `applyPopupGeometryForItem` function implements the
  unified pipeline (load → clamp size → avoid overlap → constrain → apply →
  persist back when changed).
- `showItem` and the initial-show block both call `applyPopupGeometryForItem`
  instead of branching on `userMovedPopup`.
- `installPopupInteractionHandler` is now wired with
  `onInteractionEnd = { saveCurrentGeometry(popupRef) }`.
- A `repaintPopup()` is added after the initial `applyPopupGeometryForItem`
  call to ensure the connector renders against the final bounds (the
  `popup.refreshBounds()` it triggers also picks up the layered pane).
- Adds a missing `import java.awt.Point` for the new pipeline.

- [ ] **Step 4: Verify the project still builds**

Run:
```bash
just build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify lint still passes**

Run:
```bash
just lint
```

Expected: BUILD SUCCESSFUL (detekt clean).

- [ ] **Step 6: Run the full test suite**

Run:
```bash
just test
```

Expected: all tests pass, including the new ones from Tasks 1 and 2.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt \
        src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupInteraction.kt \
        src/main/kotlin/com/forketyfork/walkthrough/WalkthroughOrchestrator.kt
git commit -m "feat: apply persisted popup geometry across walkthrough sessions"
```

---

## Task 4: Manual verification in a sandboxed IDE

The unit tests cover the persistence layer and the pure size-clamp helper. The
integration with Swing/Editor must be validated in `runIde`.

**Files:** none — this task only runs the IDE and exercises the popup.

- [ ] **Step 1: Launch the sandboxed IDE**

Run:
```bash
just run
```

Expected: a sandbox IntelliJ instance opens.

- [ ] **Step 2: Verify drag persistence within a session**

Open any project in the sandbox, trigger a walkthrough via the existing UI or
MCP tooling, drag the popup to a new location, then click Next. The popup
should stay where you dragged it (only nudging aside if it overlaps the new
target line).

- [ ] **Step 3: Verify drag persistence across sessions in the same project**

Close the walkthrough, start another walkthrough in the same project. The
popup should re-appear at the last dragged position.

- [ ] **Step 4: Verify drag persistence across projects**

Open a different project in the sandbox. Start a walkthrough. The popup
should re-appear at the same dragged position (application-wide setting).

- [ ] **Step 5: Verify overlap avoidance**

Drag the popup directly over a future target line, then click Next. The popup
should nudge above or below the target line.

- [ ] **Step 6: Verify resize persistence**

Resize the popup via the bottom-right resize handle, close the walkthrough,
start another. The popup should re-appear at the new size.

- [ ] **Step 7: Verify restart persistence**

Close the sandbox IDE entirely. Run `just run` again. Start a walkthrough.
The popup should re-appear at the last dragged position and size.

- [ ] **Step 8: Update the PR description on success**

If all manual checks pass, the PR description for #24 already covers the
broader fix. Optionally, append a short note that the position is now
remembered application-wide, including across IDE restarts.

```bash
gh pr view 24 --json body --jq .body
# Edit and update via `gh pr edit 24 --body "..."` as needed.
```

---

## Self-review notes

- **Spec coverage check:**
  - Application-wide scope via extension of `WalkthroughSettings` → Task 1.
  - `PopupGeometry` data class with sentinel-based `loadGeometry()` and
    idempotent `saveGeometry()` → Task 1.
  - Pure size-clamping helper → Task 2.
  - Removal of `userMovedPopup`, unified `applyPopupGeometryForItem` pipeline
    (load → clamp size → avoid overlap → constrain → apply → persist when
    changed) → Task 3.
  - Save fires once on `mouseReleased` when an interaction was in progress;
    per-motion callback is dropped → Task 3.
  - Manual coverage of the spec's behavioral test plan (across sessions,
    projects, restart, overlap, resize) → Task 4.
- **Placeholder scan:** none — every step contains the actual code or command.
- **Type consistency:** `PopupGeometry`, `loadGeometry()`, `saveGeometry()`,
  `clampPopupSize()`, `applyPopupGeometryForItem()`, and
  `onInteractionEnd` are spelled the same way across all tasks.
