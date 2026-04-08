package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt

internal fun movePopupNearItem(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    item: WalkthroughItem,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    popup.content.revalidate()
    popup.content.doLayout()

    val popupSize = popup.content.preferredSize.usableSize()
        ?: popup.content.size.usableSize()
        ?: WalkthroughPopupLayout.fallbackSize

    popup.popupSize = popupSize

    val screenPoint = calculatePopupScreenPoint(editor, popupSize, item.line)
    popup.show(editor, screenPoint)
    onLocationChanged?.invoke()
    movePopupOutOfLineIfNeeded(popup, editor, item.line, onLocationChanged)
}

internal fun movePopupBy(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    deltaX: Float,
    deltaY: Float,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    val currentLocation = popup.popupLocationOnScreen() ?: return
    val popupSize = resolvePopupSize(popup) ?: WalkthroughPopupLayout.fallbackSize
    val movedPoint = Point(
        currentLocation.x + deltaX.roundToInt(),
        currentLocation.y + deltaY.roundToInt()
    )
    popup.setPopupScreenLocation(constrainPopupScreenLocation(editor, movedPoint, popupSize))
    onLocationChanged?.invoke()
}

internal fun resolvePopupSize(popup: WalkthroughPopupSurface): Dimension? =
    popup.content.size.usableSize()
        ?: popup.content.preferredSize.usableSize()

private fun Dimension?.usableSize(): Dimension? =
    this?.takeIf { it.width > 0 && it.height > 0 }

private fun movePopupOutOfLineIfNeeded(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    line: Int?,
    onLocationChanged: (() -> Unit)? = null
) {
    val currentLocation = popup.popupLocationOnScreen() ?: return
    val popupSize = resolvePopupSize(popup) ?: return
    val adjustedLocation = avoidLineOverlap(currentLocation, popupSize, editor, line)
    if (adjustedLocation != currentLocation) {
        animatePopupTo(popup, adjustedLocation, onLocationChanged)
    }
}

private fun animatePopupTo(
    popup: WalkthroughPopupSurface,
    targetLocation: Point,
    onLocationChanged: (() -> Unit)? = null
) {
    val startLocation = popup.popupLocationOnScreen()
    if (startLocation == null || startLocation == targetLocation) return

    stopPopupAvoidAnimation(popup)
    if (WalkthroughDebugOptions.disablePopupAvoidAnimation) {
        popup.setPopupScreenLocation(targetLocation)
        onLocationChanged?.invoke()
    } else {
        val animationStart = System.currentTimeMillis()
        val timer = Timer(WalkthroughPopupLayout.AVOID_ANIMATION_TIMER_DELAY_MS) {
            val elapsed = (
                System.currentTimeMillis() - animationStart
                ).coerceAtMost(WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS.toLong())
            val progress = elapsed.toFloat() / WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS
            val easedProgress = cubicEaseInOut(progress.coerceIn(0f, 1f))
            val currentX = lerp(startLocation.x.toFloat(), targetLocation.x.toFloat(), easedProgress)
            val currentY = lerp(startLocation.y.toFloat(), targetLocation.y.toFloat(), easedProgress)
            popup.setPopupScreenLocation(Point(currentX.roundToInt(), currentY.roundToInt()))
            onLocationChanged?.invoke()

            if (elapsed >= WalkthroughPopupLayout.AVOID_ANIMATION_DURATION_MS) {
                popup.setPopupScreenLocation(targetLocation)
                onLocationChanged?.invoke()
                stopPopupAvoidAnimation(popup)
            }
        }.apply {
            isRepeats = true
            start()
        }

        popup.content.putClientProperty(WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY, timer)
    }
}

internal fun stopPopupAvoidAnimation(popup: WalkthroughPopupSurface) {
    val timer = popup.content.getClientProperty(
        WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY
    ) as? Timer
    timer?.stop()
    popup.content.putClientProperty(WalkthroughPopupLayout.AVOID_ANIMATION_CLIENT_PROPERTY, null)
}

internal fun constrainPopupScreenLocation(
    editor: Editor,
    location: Point,
    popupSize: Dimension
): Point {
    val constrainedLocation = Point(location)
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
        constrainedLocation.x = constrainedLocation.x.coerceIn(rootLocation.x + horizontalInset, maxX)
        constrainedLocation.y = constrainedLocation.y.coerceIn(rootLocation.y + verticalInset, maxY)
    }
    return constrainedLocation
}
