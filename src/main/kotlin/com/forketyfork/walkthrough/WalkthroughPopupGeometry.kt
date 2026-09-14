package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

private const val ARROW_VIEWPORT_INSET_PX = 12f

internal data class CurlyBraceGeometry(
    val leftX: Float,
    val topY: Float,
    val bottomY: Float,
    val width: Float,
    val opensRight: Boolean = true,
) {
    val arrowPoint: Point2D.Float
        get() = Point2D.Float(
            if (opensRight) leftX + width else leftX,
            (topY + bottomY) / 2f,
        )
}

internal data class WalkthroughTargetScreenGeometry(val arrowPoint: Point2D.Float, val brace: CurlyBraceGeometry?)

private data class LineScreenGeometry(
    val anchorX: Float,
    val lineStartX: Float,
    val topY: Float,
    val bottomY: Float,
    val centerY: Float,
    val viewportLeftX: Float,
    val viewportRightX: Float,
    val viewportTopY: Float,
    val viewportBottomY: Float,
)

internal fun calculatePopupScreenPoint(editor: Editor, popupSize: Dimension, line: Int?, endLine: Int? = null): Point {
    val visibleArea = editor.scrollingModel.visibleArea
    val targetLine = resolveTargetLine(editor, line)
    val targetEndLine = if (
        endLine != null &&
        isResolvableWalkthroughEndLine(line, endLine, editor.document.lineCount)
    ) {
        resolveTargetLine(editor, endLine)
    } else {
        targetLine
    }
    val lineStartOffset = editor.document.getLineStartOffset(targetLine)
    val linePoint = editor.visualPositionToXY(editor.offsetToVisualPosition(lineStartOffset))
    val rangeBottomPoint = editor.offsetToXY(editor.document.getLineStartOffset(targetEndLine))

    val viewportLineY = linePoint.y - visibleArea.y
    val viewportRangeBottomY = if (targetEndLine == targetLine) {
        viewportLineY + editor.lineHeight
    } else {
        rangeBottomPoint.y - visibleArea.y + editor.lineHeight
    }
    val minY = WalkthroughPopupLayout.VIEWPORT_PADDING
    val maxY = (
        visibleArea.height - popupSize.height - WalkthroughPopupLayout.VIEWPORT_PADDING
        ).coerceAtLeast(minY)
    val belowY = viewportRangeBottomY + WalkthroughPopupLayout.LINE_SPACING
    val aboveY = viewportLineY - popupSize.height - WalkthroughPopupLayout.LINE_SPACING
    val belowFits = belowY in minY..maxY
    val aboveFits = aboveY in minY..maxY

    val targetY = when {
        belowFits -> belowY

        aboveFits -> aboveY

        else -> {
            val belowSpace = visibleArea.height - viewportRangeBottomY
            val aboveSpace = viewportLineY
            if (belowSpace >= aboveSpace) belowY else aboveY
        }
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

private fun calculateLineScreenGeometry(editor: Editor, line: Int?, endLine: Int? = null): LineScreenGeometry {
    val targetLine = resolveTargetLine(editor, line)
    val document = editor.document
    val targetEndLine = if (endLine != null && isResolvableWalkthroughEndLine(line, endLine, document.lineCount)) {
        resolveTargetLine(editor, endLine)
    } else {
        targetLine
    }
    val lineStartOffset = document.getLineStartOffset(targetLine)
    val lineEndOffset = document.getLineEndOffset(targetLine)
    val lineStartPoint = editor.offsetToXY(lineStartOffset)
    val lineEndPoint = editor.offsetToXY(lineEndOffset)
    val rangeEndPoint = editor.offsetToXY(document.getLineStartOffset(targetEndLine))
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
    val lineTopY = contentOrigin.y + if (targetEndLine == targetLine) {
        lineEndPoint.y.toFloat()
    } else {
        lineStartPoint.y.toFloat()
    }
    val lineBottomY = if (targetEndLine == targetLine) {
        lineTopY + editor.lineHeight
    } else {
        contentOrigin.y + rangeEndPoint.y.toFloat() + editor.lineHeight
    }
    return LineScreenGeometry(
        anchorX = contentOrigin.x + anchorX,
        lineStartX = contentOrigin.x + lineStartPoint.x.toFloat(),
        topY = lineTopY,
        bottomY = lineBottomY,
        centerY = lineTopY + editor.lineHeight / 2f,
        viewportLeftX = viewportLeftX,
        viewportRightX = viewportRightX,
        viewportTopY = contentOrigin.y + visibleArea.y.toFloat(),
        viewportBottomY = contentOrigin.y + visibleArea.y.toFloat() + visibleArea.height,
    )
}

internal fun avoidLineOverlap(
    popupLocation: Point,
    popupSize: Dimension,
    editor: Editor,
    line: Int?,
    endLine: Int? = null,
): Point {
    val lineGeometry = calculateLineScreenGeometry(editor, line, endLine)
    val popupTop = popupLocation.y.toFloat()
    val popupBottom = popupTop + popupSize.height
    val overlapsLine = popupBottom > lineGeometry.topY && popupTop < lineGeometry.bottomY
    if (!overlapsLine) {
        return popupLocation
    }

    val outerBounds = calculatePopupYBounds(editor, lineGeometry)
    val minY = outerBounds.first
    val maxY = (
        outerBounds.second - popupSize.height - WalkthroughPopupLayout.VIEWPORT_PADDING
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
        preferBelow -> belowY
        else -> aboveY
    }

    return Point(popupLocation.x, adjustedY.roundToInt())
}

private fun calculatePopupYBounds(editor: Editor, lineGeometry: LineScreenGeometry): Pair<Float, Float> {
    val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
    return if (rootPane != null && rootPane.isShowing) {
        val rootLocation = Point(0, 0).also { SwingUtilities.convertPointToScreen(it, rootPane) }
        val top = rootLocation.y.toFloat() + WalkthroughPopupLayout.VIEWPORT_PADDING
        val bottom = (rootLocation.y + rootPane.height).toFloat()
        top to bottom
    } else {
        val top = lineGeometry.viewportTopY + WalkthroughPopupLayout.VIEWPORT_PADDING
        top to lineGeometry.viewportBottomY
    }
}

internal fun calculateLineScreenPoint(editor: Editor, line: Int?, popupBounds: Rectangle2D? = null): Point {
    val lineGeometry = calculateLineScreenGeometry(editor, line)
    val minX = lineGeometry.viewportLeftX + ARROW_VIEWPORT_INSET_PX
    val maxX = (
        lineGeometry.viewportRightX - ARROW_VIEWPORT_INSET_PX
        ).coerceAtLeast(minX)
    val minY = lineGeometry.viewportTopY + ARROW_VIEWPORT_INSET_PX
    val maxY = (
        lineGeometry.viewportBottomY - ARROW_VIEWPORT_INSET_PX
        ).coerceAtLeast(minY)
    val anchorX = if (popupBounds != null && popupBounds.maxX <= lineGeometry.lineStartX) {
        lineGeometry.lineStartX
    } else {
        lineGeometry.anchorX
    }
    return Point(
        anchorX.coerceIn(minX, maxX).roundToInt(),
        lineGeometry.centerY.coerceIn(minY, maxY).roundToInt(),
    )
}

internal fun calculateWalkthroughTargetScreenGeometry(
    editor: Editor,
    item: WalkthroughItem,
    popupBounds: Rectangle2D? = null,
): WalkthroughTargetScreenGeometry {
    val line = item.line
    val endLine = item.endLine
    val brace = if (line != null && endLine != null && hasBraceRange(editor, line, endLine)) {
        calculateRangeBraceGeometry(editor, line, endLine, popupBounds)
    } else {
        null
    }
    return WalkthroughTargetScreenGeometry(
        arrowPoint = brace?.arrowPoint ?: calculateLineScreenPoint(editor, item.line, popupBounds).toPoint2D(),
        brace = brace,
    )
}

private fun hasBraceRange(editor: Editor, line: Int, endLine: Int): Boolean =
    endLine >= line && isResolvableWalkthroughEndLine(line, endLine, editor.document.lineCount)

private fun calculateRangeBraceGeometry(
    editor: Editor,
    line: Int,
    endLine: Int,
    popupBounds: Rectangle2D?,
): CurlyBraceGeometry? {
    val firstLine = resolveTargetLine(editor, line)
    val lastLine = resolveTargetLine(editor, endLine)
    return if (lastLine < firstLine) {
        null
    } else {
        val document = editor.document
        val visibleArea = editor.scrollingModel.visibleArea
        val firstLineTop = editor.offsetToXY(document.getLineStartOffset(firstLine)).y.toFloat()
        val lastLineBottom = (
            editor.offsetToXY(document.getLineStartOffset(lastLine)).y + editor.lineHeight
            ).toFloat()
        val visibleTop = visibleArea.y.toFloat()
        val visibleBottom = (visibleArea.y + visibleArea.height).toFloat()
        val topY = firstLineTop.coerceAtLeast(visibleTop)
        val bottomY = lastLineBottom.coerceAtMost(visibleBottom)
        if (bottomY <= topY) {
            null
        } else {
            val contentOrigin = Point(0, 0).also {
                SwingUtilities.convertPointToScreen(it, editor.contentComponent)
            }
            val rangeLeftX = minOf(
                editor.offsetToXY(document.getLineStartOffset(firstLine)).x,
                editor.offsetToXY(document.getLineStartOffset(lastLine)).x,
            ).toFloat()
            val rangeRightX = maxOf(
                editor.offsetToXY(document.getLineEndOffset(firstLine)).x,
                editor.offsetToXY(document.getLineEndOffset(lastLine)).x,
            ).toFloat()
            val minX = visibleArea.x.toFloat() + WalkthroughConnectorStyle.BRACE_VIEWPORT_INSET
            val maxX = (
                visibleArea.x + visibleArea.width - WalkthroughConnectorStyle.BRACE_VIEWPORT_INSET -
                    WalkthroughConnectorStyle.BRACE_WIDTH
                ).coerceAtLeast(minX)
            val popupIsToLeft = popupBounds != null && popupBounds.maxX <= contentOrigin.x + rangeLeftX
            val braceLeftX = if (popupIsToLeft) {
                (rangeLeftX - WalkthroughConnectorStyle.BRACE_GAP - WalkthroughConnectorStyle.BRACE_WIDTH)
                    .coerceIn(minX, maxX)
            } else {
                (rangeRightX + WalkthroughConnectorStyle.BRACE_GAP)
                    .coerceIn(minX, maxX)
            }
            CurlyBraceGeometry(
                leftX = contentOrigin.x + braceLeftX,
                topY = contentOrigin.y + topY,
                bottomY = contentOrigin.y + bottomY,
                width = WalkthroughConnectorStyle.BRACE_WIDTH,
                opensRight = !popupIsToLeft,
            )
        }
    }
}

private fun Point.toPoint2D() = Point2D.Float(x.toFloat(), y.toFloat())

internal fun reverseLinearShift(elapsedMs: Long, halfPeriodMs: Int): Float {
    val period = 2L * halfPeriodMs
    val phase = (elapsedMs % period).toFloat() / halfPeriodMs.toFloat()
    return if (phase < 1f) phase else 2f - phase
}
