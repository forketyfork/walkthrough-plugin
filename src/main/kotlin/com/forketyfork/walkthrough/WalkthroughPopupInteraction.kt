package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
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

internal fun makePopupHierarchyTransparent(popup: JBPopup, panel: JComponent) {
    makeComponentHierarchyTransparent(popup.content)
    makeComponentHierarchyTransparent(panel)
}

private fun makeComponentHierarchyTransparent(component: Component?) {
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
    popupProvider: () -> JBPopup?,
    editorProvider: () -> Editor,
    onPopupMoved: () -> Unit
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
                    deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat(),
                    onLocationChanged = onPopupMoved
                )

                PopupInteractionMode.Resize -> resizePopupBy(
                    popup = popup,
                    panel = panel,
                    editor = editorProvider(),
                    deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
                    deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat(),
                    onLocationChanged = onPopupMoved
                )
            }
            lastScreenPoint = currentScreenPoint
        }

        override fun mouseMoved(event: MouseEvent) {
            updateInteractionCursor(panel, event.component, event.point)
        }

        override fun mouseReleased(event: MouseEvent) {
            interactionMode = null
            lastScreenPoint = null
            updateInteractionCursor(panel, event.component, event.point)
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
    popup: JBPopup,
    panel: JComponent,
    editor: Editor,
    deltaX: Float,
    deltaY: Float,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    val currentLocation = popup.locationOnScreen
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
    popup.size = targetSize
    popup.moveToFitScreen()
    onLocationChanged?.invoke()
}
