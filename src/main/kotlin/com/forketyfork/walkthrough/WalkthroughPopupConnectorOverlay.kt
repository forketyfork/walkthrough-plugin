package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal object WalkthroughConnectorStyle {
    const val LAYER_OFFSET = 5
    const val BORDER_INSET = 20f
    const val START_PULL_FACTOR = 0.32f
    const val START_PULL_MINIMUM = 72f
    const val VERTICAL_PULL_FACTOR = 0.28f
    const val VERTICAL_PULL_MINIMUM = 54f
    const val END_PULL_FACTOR = 0.24f
    const val END_HORIZONTAL_PULL_MINIMUM = 48f
    const val END_VERTICAL_PULL_MINIMUM = 42f
    const val END_CURVE_FACTOR = 0.12f
    const val DEFAULT_SIGN = 1f
    const val ARROW_SPREAD_DEGREES = 24.0
    const val ARROW_HEAD_LENGTH = 13.0
    const val STROKE_WIDTH = 3.5f
    @Suppress("UseJBColor") // Decorative connector color painted on a glass pane overlay
    val strokeColor = AwtColor(255, 136, 136, 235)
    @Suppress("UseJBColor") // Decorative connector color painted on a glass pane overlay
    val arrowFillColor = AwtColor(255, 102, 102, 215)
}

private data class ConnectorPaintContext(
    val editor: Editor,
    val item: WalkthroughItem,
    val pane: JLayeredPane,
    val popupBounds: Rectangle2D.Float
)

internal class PopupConnectorOverlay(
    private val popup: JBPopup
) : JComponent(), VisibleAreaListener {
    private var editor: Editor? = null
    private var item: WalkthroughItem? = null
    private var layeredPane: JLayeredPane? = null
    private val layeredPaneResizeListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            refreshBounds()
        }
    }

    init {
        isOpaque = false
    }

    override fun contains(x: Int, y: Int): Boolean = false

    fun update(editor: Editor, item: WalkthroughItem) {
        if (this.editor !== editor) {
            this.editor?.scrollingModel?.removeVisibleAreaListener(this)
            this.editor = editor
            editor.scrollingModel.addVisibleAreaListener(this)
        }
        this.item = item
        val targetLayeredPane = SwingUtilities.getRootPane(editor.contentComponent)?.layeredPane
        if (layeredPane !== targetLayeredPane) {
            detachFromLayeredPane()
            layeredPane = targetLayeredPane?.also { pane ->
                pane.addComponentListener(layeredPaneResizeListener)
                pane.setLayer(this, JLayeredPane.POPUP_LAYER - WalkthroughConnectorStyle.LAYER_OFFSET)
                pane.add(this)
            }
        }
        refreshBounds()
        repaint()
    }

    fun dispose() {
        editor?.scrollingModel?.removeVisibleAreaListener(this)
        editor = null
        item = null
        detachFromLayeredPane()
    }

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        refreshBounds()
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val context = currentPaintContext() ?: return
        val targetPointOnScreen = calculateLineScreenPoint(context.editor, context.item.line)
        val popupOrigin = Point(
            context.popupBounds.x.roundToInt(),
            context.popupBounds.y.roundToInt()
        ).also {
            SwingUtilities.convertPointFromScreen(it, context.pane)
        }
        val targetPoint = Point(targetPointOnScreen).also {
            SwingUtilities.convertPointFromScreen(it, context.pane)
        }
        val popupRect = Rectangle2D.Float(
            popupOrigin.x.toFloat(),
            popupOrigin.y.toFloat(),
            context.popupBounds.width,
            context.popupBounds.height
        )
        val arrowTarget = Point2D.Float(targetPoint.x.toFloat(), targetPoint.y.toFloat())
        val connector = buildConnector(nearestBorderAnchor(popupRect, arrowTarget), arrowTarget)

        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
            graphics2D.color = WalkthroughConnectorStyle.strokeColor
            graphics2D.stroke = BasicStroke(
                WalkthroughConnectorStyle.STROKE_WIDTH,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND
            )
            graphics2D.draw(connector.path)
            drawArrowHead(graphics2D, connector.end, connector.endControl)
        } finally {
            graphics2D.dispose()
        }
    }

    fun refreshBounds() {
        layeredPane?.let { pane ->
            setBounds(0, 0, pane.width, pane.height)
            if (parent !== pane) {
                pane.setLayer(this, JLayeredPane.POPUP_LAYER - WalkthroughConnectorStyle.LAYER_OFFSET)
                pane.add(this)
            }
        }
    }

    private fun currentPaintContext(): ConnectorPaintContext? =
        editor?.let { currentEditor ->
            item?.let { currentItem ->
                layeredPane?.let { pane ->
                    popupScreenBounds(popup)?.let { popupBounds ->
                        ConnectorPaintContext(currentEditor, currentItem, pane, popupBounds)
                    }
                }
            }
        }

    private fun detachFromLayeredPane() {
        layeredPane?.let { pane ->
            pane.removeComponentListener(layeredPaneResizeListener)
            pane.remove(this)
            pane.repaint()
        }
        layeredPane = null
    }
}

private enum class ConnectorSide {
    Left,
    Right,
    Top,
    Bottom
}

private data class ConnectorAnchor(
    val point: Point2D.Float,
    val side: ConnectorSide
)

private data class ConnectorPath(
    val path: Path2D.Float,
    val end: Point2D.Float,
    val endControl: Point2D.Float
)

private fun nearestBorderAnchor(popupRect: Rectangle2D.Float, target: Point2D.Float): ConnectorAnchor {
    val inset = WalkthroughConnectorStyle.BORDER_INSET
    val minY = popupRect.y + inset
    val maxY = (popupRect.y + popupRect.height - inset).coerceAtLeast(minY)
    val minX = popupRect.x + inset
    val maxX = (popupRect.x + popupRect.width - inset).coerceAtLeast(minX)
    val candidates = listOf(
        ConnectorAnchor(
            Point2D.Float(popupRect.x, target.y.coerceIn(minY, maxY)),
            ConnectorSide.Left
        ),
        ConnectorAnchor(
            Point2D.Float(popupRect.x + popupRect.width, target.y.coerceIn(minY, maxY)),
            ConnectorSide.Right
        ),
        ConnectorAnchor(
            Point2D.Float(target.x.coerceIn(minX, maxX), popupRect.y),
            ConnectorSide.Top
        ),
        ConnectorAnchor(
            Point2D.Float(target.x.coerceIn(minX, maxX), popupRect.y + popupRect.height),
            ConnectorSide.Bottom
        )
    )
    return candidates.minBy { anchor ->
        val dx = anchor.point.x - target.x
        val dy = anchor.point.y - target.y
        dx * dx + dy * dy
    }
}

private fun buildConnector(anchor: ConnectorAnchor, end: Point2D.Float): ConnectorPath {
    val dx = end.x - anchor.point.x
    val dy = end.y - anchor.point.y
    val startPull = (abs(dx) * WalkthroughConnectorStyle.START_PULL_FACTOR)
        .coerceAtLeast(WalkthroughConnectorStyle.START_PULL_MINIMUM)
    val verticalPull = (abs(dy) * WalkthroughConnectorStyle.VERTICAL_PULL_FACTOR)
        .coerceAtLeast(WalkthroughConnectorStyle.VERTICAL_PULL_MINIMUM)
    val startControl = when (anchor.side) {
        ConnectorSide.Left -> Point2D.Float(anchor.point.x - startPull, anchor.point.y)
        ConnectorSide.Right -> Point2D.Float(anchor.point.x + startPull, anchor.point.y)
        ConnectorSide.Top -> Point2D.Float(anchor.point.x, anchor.point.y - verticalPull)
        ConnectorSide.Bottom -> Point2D.Float(anchor.point.x, anchor.point.y + verticalPull)
    }
    val nonZeroDxSign = dx.sign.takeIf { it != 0f } ?: WalkthroughConnectorStyle.DEFAULT_SIGN
    val nonZeroDySign = dy.sign.takeIf { it != 0f } ?: WalkthroughConnectorStyle.DEFAULT_SIGN
    val endControl = if (abs(dx) >= abs(dy)) {
        Point2D.Float(
            end.x - nonZeroDxSign * (abs(dx) * WalkthroughConnectorStyle.END_PULL_FACTOR)
                .coerceAtLeast(WalkthroughConnectorStyle.END_HORIZONTAL_PULL_MINIMUM),
            end.y - dy * WalkthroughConnectorStyle.END_CURVE_FACTOR
        )
    } else {
        Point2D.Float(
            end.x - dx * WalkthroughConnectorStyle.END_CURVE_FACTOR,
            end.y - nonZeroDySign * (abs(dy) * WalkthroughConnectorStyle.END_PULL_FACTOR)
                .coerceAtLeast(WalkthroughConnectorStyle.END_VERTICAL_PULL_MINIMUM)
        )
    }
    val path = Path2D.Float().apply {
        moveTo(anchor.point.x.toDouble(), anchor.point.y.toDouble())
        curveTo(
            startControl.x.toDouble(),
            startControl.y.toDouble(),
            endControl.x.toDouble(),
            endControl.y.toDouble(),
            end.x.toDouble(),
            end.y.toDouble()
        )
    }
    return ConnectorPath(path, end, endControl)
}

private fun drawArrowHead(graphics: Graphics2D, end: Point2D.Float, endControl: Point2D.Float) {
    val angle = atan2((end.y - endControl.y).toDouble(), (end.x - endControl.x).toDouble())
    val spread = Math.toRadians(WalkthroughConnectorStyle.ARROW_SPREAD_DEGREES)
    val headLength = WalkthroughConnectorStyle.ARROW_HEAD_LENGTH
    val leftX = end.x - (headLength * cos(angle - spread)).toFloat()
    val leftY = end.y - (headLength * sin(angle - spread)).toFloat()
    val rightX = end.x - (headLength * cos(angle + spread)).toFloat()
    val rightY = end.y - (headLength * sin(angle + spread)).toFloat()
    val arrow = Path2D.Float().apply {
        moveTo(end.x.toDouble(), end.y.toDouble())
        lineTo(leftX.toDouble(), leftY.toDouble())
        lineTo(rightX.toDouble(), rightY.toDouble())
        closePath()
    }
    graphics.color = WalkthroughConnectorStyle.arrowFillColor
    graphics.fill(arrow)
}
