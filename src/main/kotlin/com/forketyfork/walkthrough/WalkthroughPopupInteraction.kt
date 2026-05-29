package com.forketyfork.walkthrough

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.awt.RelativePoint
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import java.awt.Color as AwtColor

private const val DRAG_HANDLE_HEIGHT_PX = 64
private const val CLOSE_BUTTON_HIT_BOX_PX = 60
private const val RESIZE_HANDLE_SIZE_PX = 26

private enum class PopupInteractionMode {
    Drag,
    Resize,
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

/**
 * Routes popup drag and resize gestures through an [IdeGlassPane] mouse preprocessor. The editor
 * file tabs install their own [com.intellij.ui.MouseDragHelper] preprocessor (weight `2`) on the
 * same glass pane; registering with the default weight `0` makes this listener run first, so
 * consuming a gesture that starts on the popup prevents the covered tabs from reordering.
 */
internal fun installPopupInteractionHandler(
    editor: Editor,
    parentDisposable: Disposable,
    popupProvider: () -> WalkthroughPopupSurface?,
    editorProvider: () -> Editor?,
    onInteractionEnd: () -> Unit,
): Boolean {
    val glassPane = findGlassPane(editor) ?: return false
    val listener = WalkthroughPopupInteractionListener(
        glassPane = glassPane,
        popupProvider = popupProvider,
        editorProvider = editorProvider,
        onInteractionEnd = onInteractionEnd,
    )
    glassPane.addMousePreprocessor(listener, parentDisposable)
    glassPane.addMouseMotionPreprocessor(listener, parentDisposable)
    return true
}

private fun findGlassPane(editor: Editor): IdeGlassPane? {
    val component = editor.contentComponent
    if (!component.isShowing) {
        return null
    }
    return runCatching { IdeGlassPaneUtil.find(component) }.getOrNull()
}

private class WalkthroughPopupInteractionListener(
    private val glassPane: IdeGlassPane,
    private val popupProvider: () -> WalkthroughPopupSurface?,
    private val editorProvider: () -> Editor?,
    private val onInteractionEnd: () -> Unit,
) : MouseAdapter() {
    private var interactionMode: PopupInteractionMode? = null
    private var lastScreenPoint: Point? = null

    override fun mousePressed(event: MouseEvent) {
        val mode = popupProvider()
            ?.takeIf { event.button == MouseEvent.BUTTON1 }
            ?.let { handleRegionAt(it, event) }
        if (mode == null) {
            reset()
            return
        }
        interactionMode = mode
        lastScreenPoint = RelativePoint(event).screenPoint
        event.consume()
    }

    override fun mouseDragged(event: MouseEvent) {
        val mode = interactionMode ?: return
        val popup = popupProvider()
        val editor = editorProvider()
        if (popup == null || editor == null) {
            return
        }
        val currentScreenPoint = RelativePoint(event).screenPoint
        val previousScreenPoint = lastScreenPoint ?: currentScreenPoint
        handlePopupDrag(popup.content, mode, popup, editor, previousScreenPoint, currentScreenPoint)
        lastScreenPoint = currentScreenPoint
        event.consume()
    }

    override fun mouseReleased(event: MouseEvent) {
        if (interactionMode == null) {
            return
        }
        reset()
        event.consume()
        onInteractionEnd()
    }

    override fun mouseMoved(event: MouseEvent) {
        val popup = popupProvider()
        glassPane.setCursor(popup?.let { resolveCursor(it, event) }, this)
    }

    private fun resolveCursor(popup: WalkthroughPopupSurface, event: MouseEvent): Cursor? =
        when (handleRegionAt(popup, event)) {
            PopupInteractionMode.Resize -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
            PopupInteractionMode.Drag -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            null -> if (isWithinPopup(popup, event)) Cursor.getDefaultCursor() else null
        }

    private fun reset() {
        interactionMode = null
        lastScreenPoint = null
    }
}

private fun pointInContent(popup: WalkthroughPopupSurface, event: MouseEvent): Point? {
    val contentLocation = popup.popupLocationOnScreen() ?: return null
    val screenPoint = RelativePoint(event).screenPoint
    return Point(screenPoint.x - contentLocation.x, screenPoint.y - contentLocation.y)
}

private fun isWithinPopup(popup: WalkthroughPopupSurface, event: MouseEvent): Boolean {
    val point = pointInContent(popup, event) ?: return false
    val content = popup.content
    return point.x in 0..content.width && point.y in 0..content.height
}

private fun handleRegionAt(popup: WalkthroughPopupSurface, event: MouseEvent): PopupInteractionMode? {
    val point = pointInContent(popup, event) ?: return null
    val content = popup.content
    val withinBounds = point.x in 0..content.width && point.y in 0..content.height
    return when {
        !withinBounds -> null
        resizeHandleContains(content.width, content.height, point) -> PopupInteractionMode.Resize
        dragHandleContains(content.width, point) -> PopupInteractionMode.Drag
        else -> null
    }
}

internal fun dragHandleContains(width: Int, point: Point): Boolean =
    point.y in 0..DRAG_HANDLE_HEIGHT_PX && point.x in 0..(width - CLOSE_BUTTON_HIT_BOX_PX)

internal fun resizeHandleContains(width: Int, height: Int, point: Point): Boolean =
    point.x in (width - RESIZE_HANDLE_SIZE_PX)..width && point.y in (height - RESIZE_HANDLE_SIZE_PX)..height

@Suppress("LongParameterList")
private fun handlePopupDrag(
    panel: JComponent,
    mode: PopupInteractionMode,
    popup: WalkthroughPopupSurface,
    editor: Editor,
    previousScreenPoint: Point,
    currentScreenPoint: Point,
) {
    when (mode) {
        PopupInteractionMode.Drag -> movePopupBy(
            popup = popup,
            editor = editor,
            deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
            deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat(),
        )

        PopupInteractionMode.Resize -> resizePopupBy(
            popup = popup,
            panel = panel,
            editor = editor,
            deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
            deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat(),
        )
    }
}

private fun resizePopupBy(
    popup: WalkthroughPopupSurface,
    panel: JComponent,
    editor: Editor,
    deltaX: Float,
    deltaY: Float,
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
    val targetSize = Dimension(
        (currentSize.width + deltaX.toInt()).coerceIn(WalkthroughPopupLayout.MINIMUM_WIDTH_PX, maxWidth),
        (currentSize.height + deltaY.toInt()).coerceIn(WalkthroughPopupLayout.MINIMUM_HEIGHT_PX, maxHeight),
    )

    panel.preferredSize = targetSize
    panel.revalidate()
    popup.popupSize = targetSize
    popup.moveToFitScreen(editor)
}
