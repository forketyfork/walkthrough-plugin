package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt

internal fun movePopupNearItem(
    popup: JBPopup,
    panel: JComponent,
    editor: Editor,
    item: WalkthroughItem,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    panel.revalidate()
    panel.doLayout()
    popup.pack(true, true)

    val popupSize = panel.preferredSize.usableSize()
        ?: popup.content.preferredSize.usableSize()
        ?: popup.size.usableSize()
        ?: WalkthroughPopupLayout.fallbackSize

    popup.setSize(popupSize)

    val screenPoint = calculatePopupScreenPoint(editor, popupSize, item.line)
    if (popup.isVisible) {
        popup.setLocation(screenPoint)
    } else {
        popup.showInScreenCoordinates(editor.contentComponent, screenPoint)
        makePopupHierarchyTransparent(popup, panel)
    }
    popup.moveToFitScreen()
    onLocationChanged?.invoke()
    movePopupOutOfLineIfNeeded(popup, editor, item.line, onLocationChanged)
}

internal fun movePopupBy(
    popup: JBPopup,
    editor: Editor,
    deltaX: Float,
    deltaY: Float,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    val currentLocation = popupScreenLocation(popup) ?: return
    val popupSize = resolvePopupSize(popup) ?: WalkthroughPopupLayout.fallbackSize
    val movedPoint = Point(
        currentLocation.x + deltaX.roundToInt(),
        currentLocation.y + deltaY.roundToInt()
    )
    val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
    if (rootPane != null && rootPane.isShowing) {
        val rootLocation = Point(0, 0).also { SwingUtilities.convertPointToScreen(it, rootPane) }
        val horizontalInset = WalkthroughPopupLayout.VIEWPORT_PADDING
        val verticalInset = WalkthroughPopupLayout.VIEWPORT_PADDING
        val maxX = (
            rootLocation.x + rootPane.width - popupSize.width - horizontalInset
            ).coerceAtLeast(rootLocation.x + horizontalInset)
        val maxY = (
            rootLocation.y + rootPane.height - popupSize.height - verticalInset
            ).coerceAtLeast(rootLocation.y + verticalInset)
        movedPoint.x = movedPoint.x.coerceIn(rootLocation.x + horizontalInset, maxX)
        movedPoint.y = movedPoint.y.coerceIn(rootLocation.y + verticalInset, maxY)
    }
    popup.setLocation(movedPoint)
    popup.moveToFitScreen()
    onLocationChanged?.invoke()
}

internal fun resolvePopupSize(popup: JBPopup): Dimension? {
    val popupContent = popup.content
    return popup.size.usableSize()
        ?: popupContent.size.usableSize()
        ?: popupContent.preferredSize.usableSize()
}

private fun Dimension?.usableSize(): Dimension? =
    this?.takeIf { it.width > 0 && it.height > 0 }

private fun popupScreenLocation(popup: JBPopup): Point? =
    runCatching { popup.locationOnScreen }.getOrNull()

internal fun popupScreenBounds(popup: JBPopup): Rectangle2D.Float? =
    popupScreenLocation(popup)?.let { location ->
        resolvePopupSize(popup)?.let { size ->
            Rectangle2D.Float(
                location.x.toFloat(),
                location.y.toFloat(),
                size.width.toFloat(),
                size.height.toFloat()
            )
        }
    }

private fun movePopupOutOfLineIfNeeded(
    popup: JBPopup,
    editor: Editor,
    line: Int?,
    onLocationChanged: (() -> Unit)? = null
) {
    val currentLocation = popupScreenLocation(popup) ?: return
    val popupSize = resolvePopupSize(popup) ?: return
    val adjustedLocation = avoidLineOverlap(currentLocation, popupSize, editor, line)
    if (adjustedLocation != currentLocation) {
        animatePopupTo(popup, adjustedLocation, onLocationChanged)
    }
}

private fun animatePopupTo(
    popup: JBPopup,
    targetLocation: Point,
    onLocationChanged: (() -> Unit)? = null
) {
    val startLocation = popupScreenLocation(popup) ?: return
    if (startLocation == targetLocation) {
        return
    }

    stopPopupAvoidAnimation(popup)

    val animationStart = System.currentTimeMillis()
    val timer = Timer(WalkthroughPopupLayout.AVOID_ANIMATION_TIMER_DELAY_MS) {
        val elapsed = (
            System.currentTimeMillis() - animationStart
            ).coerceAtMost(WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS.toLong())
        val progress = elapsed.toFloat() / WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS
        val easedProgress = cubicEaseInOut(progress.coerceIn(0f, 1f))
        val currentX = lerp(startLocation.x.toFloat(), targetLocation.x.toFloat(), easedProgress)
        val currentY = lerp(startLocation.y.toFloat(), targetLocation.y.toFloat(), easedProgress)
        popup.setLocation(Point(currentX.roundToInt(), currentY.roundToInt()))
        onLocationChanged?.invoke()

        if (elapsed >= WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS) {
            popup.setLocation(targetLocation)
            popup.moveToFitScreen()
            onLocationChanged?.invoke()
            stopPopupAvoidAnimation(popup)
        }
    }.apply {
        isRepeats = true
        start()
    }

    popup.content.putClientProperty(WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY, timer)
}

internal fun stopPopupAvoidAnimation(popup: JBPopup) {
    val timer = popup.content.getClientProperty(
        WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY
    ) as? Timer
    if (timer != null) {
        timer.stop()
    }
    popup.content.putClientProperty(WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY, null)
}
