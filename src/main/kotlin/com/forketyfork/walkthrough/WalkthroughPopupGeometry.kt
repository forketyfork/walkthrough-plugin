package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

private const val ARROW_VIEWPORT_INSET_PX = 12f
private const val ANIMATION_MIDPOINT = 0.5f
private const val ACCELERATE_FACTOR = 4f
private const val INVERTED_PROGRESS_FACTOR = -2f
private const val INVERTED_PROGRESS_OFFSET = 2f
private const val ANIMATION_COMPLETION = 1f
private const val EASING_DIVISOR = 2f

private data class LineScreenGeometry(
    val anchorX: Float,
    val topY: Float,
    val bottomY: Float,
    val centerY: Float,
    val viewportLeftX: Float,
    val viewportRightX: Float,
    val viewportTopY: Float,
    val viewportBottomY: Float
)

internal fun calculatePopupScreenPoint(editor: Editor, popupSize: Dimension, line: Int?): Point {
    val visibleArea = editor.scrollingModel.visibleArea
    val targetLine = resolveTargetLine(editor, line)
    val lineStartOffset = editor.document.getLineStartOffset(targetLine)
    val linePoint = editor.visualPositionToXY(editor.offsetToVisualPosition(lineStartOffset))

    val viewportLineY = linePoint.y - visibleArea.y
    val minY = WalkthroughPopupLayout.VIEWPORT_PADDING
    val maxY = (
        visibleArea.height - popupSize.height - WalkthroughPopupLayout.VIEWPORT_PADDING
        ).coerceAtLeast(minY)
    val belowY = viewportLineY + editor.lineHeight + WalkthroughPopupLayout.LINE_SPACING
    val aboveY = viewportLineY - popupSize.height - WalkthroughPopupLayout.LINE_SPACING
    val belowFits = belowY in minY..maxY
    val aboveFits = aboveY in minY..maxY

    val targetY = when {
        belowFits -> belowY
        aboveFits -> aboveY
        else -> viewportLineY.coerceIn(minY, maxY)
    }

    val targetX = (
        visibleArea.width - popupSize.width - WalkthroughPopupLayout.VIEWPORT_PADDING
        ).coerceAtLeast(WalkthroughPopupLayout.VIEWPORT_PADDING)
    return Point(targetX, targetY).also {
        SwingUtilities.convertPointToScreen(it, editor.contentComponent)
    }
}

private fun resolveTargetLine(editor: Editor, line: Int?): Int {
    val lineCount = editor.document.lineCount.coerceAtLeast(1)
    val targetLine = if (line != null) line - 1 else editor.caretModel.primaryCaret.logicalPosition.line
    return targetLine.coerceIn(0, lineCount - 1)
}

private fun calculateLineScreenGeometry(editor: Editor, line: Int?): LineScreenGeometry {
    val targetLine = resolveTargetLine(editor, line)
    val lineStartOffset = editor.document.getLineStartOffset(targetLine)
    val lineEndOffset = editor.document.getLineEndOffset(targetLine)
    val lineStartPoint = editor.offsetToXY(lineStartOffset)
    val lineEndPoint = editor.offsetToXY(lineEndOffset)
    val visibleArea = editor.scrollingModel.visibleArea
    val visibleLeft = visibleArea.x.toFloat()
    val visibleRight = (visibleArea.x + visibleArea.width).toFloat()
    val lineLeft = minOf(lineStartPoint.x, lineEndPoint.x).toFloat()
    val lineRight = maxOf(lineStartPoint.x, lineEndPoint.x).toFloat()

    val anchorX = when {
        lineEndPoint.x.toFloat() in visibleLeft..visibleRight -> lineEndPoint.x.toFloat()
        else -> {
            val visibleLineLeft = maxOf(lineLeft, visibleLeft)
            val visibleLineRight = minOf(lineRight, visibleRight)
            if (visibleLineLeft < visibleLineRight) {
                (visibleLineLeft + visibleLineRight) / 2f
            } else {
                lineEndPoint.x.toFloat().coerceIn(visibleLeft, visibleRight)
            }
        }
    }

    val contentOrigin = Point(0, 0).also {
        SwingUtilities.convertPointToScreen(it, editor.contentComponent)
    }
    val viewportLeftX = contentOrigin.x + visibleArea.x.toFloat()
    val viewportRightX = viewportLeftX + visibleArea.width
    val lineTopY = contentOrigin.y + lineEndPoint.y.toFloat()
    val lineBottomY = lineTopY + editor.lineHeight
    return LineScreenGeometry(
        anchorX = contentOrigin.x + anchorX,
        topY = lineTopY,
        bottomY = lineBottomY,
        centerY = lineTopY + editor.lineHeight / 2f,
        viewportLeftX = viewportLeftX,
        viewportRightX = viewportRightX,
        viewportTopY = contentOrigin.y + visibleArea.y.toFloat(),
        viewportBottomY = contentOrigin.y + visibleArea.y.toFloat() + visibleArea.height
    )
}

internal fun avoidLineOverlap(
    popupLocation: Point,
    popupSize: Dimension,
    editor: Editor,
    line: Int?
): Point {
    val lineGeometry = calculateLineScreenGeometry(editor, line)
    val popupTop = popupLocation.y.toFloat()
    val popupBottom = popupTop + popupSize.height
    val overlapsLine = popupBottom > lineGeometry.topY && popupTop < lineGeometry.bottomY
    if (!overlapsLine) {
        return popupLocation
    }

    val minY = lineGeometry.viewportTopY + WalkthroughPopupLayout.VIEWPORT_PADDING
    val maxY = (
        lineGeometry.viewportBottomY - popupSize.height - WalkthroughPopupLayout.VIEWPORT_PADDING
        ).coerceAtLeast(minY)
    val aboveY = lineGeometry.topY - popupSize.height - WalkthroughPopupLayout.LINE_SPACING
    val belowY = lineGeometry.bottomY + WalkthroughPopupLayout.LINE_SPACING
    val aboveFits = aboveY >= minY
    val belowFits = belowY <= maxY
    val aboveSpace = lineGeometry.topY - minY
    val belowSpace = maxY - lineGeometry.bottomY
    val preferBelow = belowSpace >= aboveSpace

    val adjustedY = when {
        preferBelow && belowFits -> belowY
        !preferBelow && aboveFits -> aboveY
        belowFits -> belowY
        aboveFits -> aboveY
        preferBelow -> maxY
        else -> minY
    }

    return Point(popupLocation.x, adjustedY.roundToInt())
}

internal fun calculateLineScreenPoint(editor: Editor, line: Int?): Point {
    val lineGeometry = calculateLineScreenGeometry(editor, line)
    val minX = lineGeometry.viewportLeftX + ARROW_VIEWPORT_INSET_PX
    val maxX = (
        lineGeometry.viewportRightX - ARROW_VIEWPORT_INSET_PX
        ).coerceAtLeast(minX)
    val minY = lineGeometry.viewportTopY + ARROW_VIEWPORT_INSET_PX
    val maxY = (
        lineGeometry.viewportBottomY - ARROW_VIEWPORT_INSET_PX
        ).coerceAtLeast(minY)
    return Point(
        lineGeometry.anchorX.coerceIn(minX, maxX).roundToInt(),
        lineGeometry.centerY.coerceIn(minY, maxY).roundToInt()
    )
}

internal fun cubicEaseInOut(progress: Float): Float =
    if (progress < ANIMATION_MIDPOINT) {
        ACCELERATE_FACTOR * progress * progress * progress
    } else {
        val inverted = INVERTED_PROGRESS_FACTOR * progress + INVERTED_PROGRESS_OFFSET
        ANIMATION_COMPLETION - (inverted * inverted * inverted) / EASING_DIVISOR
    }

internal fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

internal fun reverseLinearShift(elapsedMs: Long, halfPeriodMs: Int): Float {
    val period = 2L * halfPeriodMs
    val phase = (elapsedMs % period).toFloat() / halfPeriodMs.toFloat()
    return if (phase < 1f) phase else 2f - phase
}
