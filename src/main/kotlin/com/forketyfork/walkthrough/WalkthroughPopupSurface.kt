package com.forketyfork.walkthrough

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

internal object WalkthroughConnectorStyle {
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

    @Suppress("UseJBColor")
    val strokeColor = AwtColor(255, 136, 136, 235)

    @Suppress("UseJBColor")
    val arrowFillColor = AwtColor(255, 102, 102, 215)
}

private data class ConnectorPaintContext(
    val editor: Editor,
    val item: WalkthroughItem,
    val popupBounds: Rectangle2D.Float
)

internal class WalkthroughPopupSurface(
    val content: JComponent,
    private val onCloseRequested: () -> Unit
) : JComponent(), VisibleAreaListener, Disposable {
    private var editor: Editor? = null
    private var item: WalkthroughItem? = null
    private var layeredPane: JLayeredPane? = null
    var connectorHidden: Boolean = false
        set(value) {
            field = value
            repaint()
        }
    private val layeredPaneResizeListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            refreshBounds()
            editor?.let(::moveToFitScreen)
            repaint()
        }
    }

    init {
        isOpaque = false
        layout = null
        add(content)
        content.isVisible = false
        content.setBounds(0, 0, 0, 0)
        content.registerKeyboardAction(
            { cancel() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
        )
    }

    override fun contains(x: Int, y: Int): Boolean =
        content.isVisible && content.bounds.contains(x, y)

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
                pane.setLayer(this, JLayeredPane.POPUP_LAYER)
                pane.add(this)
            }
        }
        refreshBounds()
        repaint()
    }

    override fun dispose() {
        stopPopupAvoidAnimation(this)
        editor?.scrollingModel?.removeVisibleAreaListener(this)
        editor = null
        item = null
        detachFromLayeredPane()
    }

    override fun visibleAreaChanged(event: VisibleAreaEvent) {
        if (event.oldRectangle == event.newRectangle) {
            return
        }
        refreshBounds()
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (connectorHidden) return
        val context = currentPaintContext() ?: return
        val targetPoint = Point(calculateLineScreenPoint(context.editor, context.item.line)).also {
            SwingUtilities.convertPointFromScreen(it, this)
        }
        val arrowTarget = Point2D.Float(targetPoint.x.toFloat(), targetPoint.y.toFloat())
        val connector = buildConnector(
            nearestBorderAnchor(context.popupBounds, arrowTarget),
            arrowTarget
        )

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
        val pane = layeredPane ?: return
        setBounds(0, 0, pane.width, pane.height)
        if (parent !== pane) {
            pane.setLayer(this, JLayeredPane.POPUP_LAYER)
            pane.add(this)
        }
    }

    fun cancel() {
        onCloseRequested()
    }

    private fun currentPaintContext(): ConnectorPaintContext? {
        val currentEditor = editor
        val currentItem = item
        val popupBounds = content.bounds.takeIf { bounds ->
            content.isVisible && bounds.width > 0 && bounds.height > 0
        }
        return if (currentEditor != null && currentItem != null && popupBounds != null) {
            ConnectorPaintContext(
                editor = currentEditor,
                item = currentItem,
                popupBounds = Rectangle2D.Float(
                    popupBounds.x.toFloat(),
                    popupBounds.y.toFloat(),
                    popupBounds.width.toFloat(),
                    popupBounds.height.toFloat()
                )
            )
        } else {
            null
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

internal fun WalkthroughPopupSurface.show(editor: Editor, screenPoint: Point) {
    content.isVisible = true
    setPopupScreenLocation(screenPoint)
    moveToFitScreen(editor)
    content.requestFocusInWindow()
    repaint()
}

internal fun WalkthroughPopupSurface.popupLocationOnScreen(): Point? =
    if (content.isShowing) {
        Point(0, 0).also { SwingUtilities.convertPointToScreen(it, content) }
    } else {
        null
    }

internal fun WalkthroughPopupSurface.setPopupScreenLocation(screenPoint: Point) {
    val localPoint = Point(screenPoint)
    SwingUtilities.convertPointFromScreen(localPoint, this)
    content.setBounds(localPoint.x, localPoint.y, content.width, content.height)
    repaint()
}

internal fun WalkthroughPopupSurface.moveToFitScreen(editor: Editor) {
    val currentLocation = popupLocationOnScreen() ?: return
    val popupSize = resolvePopupSize(this) ?: return
    val constrainedLocation = constrainPopupScreenLocation(editor, currentLocation, popupSize)
    if (constrainedLocation != currentLocation) {
        setPopupScreenLocation(constrainedLocation)
    }
}

internal var WalkthroughPopupSurface.popupSize: Dimension
    get() = resolvePopupSize(this) ?: WalkthroughPopupLayout.fallbackSize
    set(value) {
        content.preferredSize = value
        content.setBounds(content.x, content.y, value.width, value.height)
        content.revalidate()
        repaint()
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
