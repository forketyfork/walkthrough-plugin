package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

internal fun movePopupNearItem(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    item: WalkthroughItem,
    onLocationChanged: (() -> Unit)? = null
) {
    popup.content.revalidate()
    popup.content.doLayout()

    val popupSize = popup.content.preferredSize.usableSize()
        ?: popup.content.size.usableSize()
        ?: WalkthroughPopupLayout.fallbackSize

    popup.popupSize = popupSize

    val initialPoint = calculatePopupScreenPoint(editor, popupSize, item.line)
    val avoidedPoint = avoidLineOverlap(initialPoint, popupSize, editor, item.line)
    val constrainedPoint = constrainPopupScreenLocation(editor, avoidedPoint, popupSize)
    val reAvoidedPoint = avoidLineOverlap(constrainedPoint, popupSize, editor, item.line)
    val finalPoint = constrainPopupScreenLocation(editor, reAvoidedPoint, popupSize)
    popup.show(editor, finalPoint)
    onLocationChanged?.invoke()
}

internal fun movePopupBy(
    popup: WalkthroughPopupSurface,
    editor: Editor,
    deltaX: Float,
    deltaY: Float,
    onLocationChanged: (() -> Unit)? = null
) {
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
