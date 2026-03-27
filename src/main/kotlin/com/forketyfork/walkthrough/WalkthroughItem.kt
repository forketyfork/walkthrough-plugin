package com.forketyfork.walkthrough

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.ui.component.Text
import java.awt.BasicStroke
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.Color as AwtColor
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private val PopupCornerRadius = 24.dp
private val PopupTextMinWidth = 340.dp
private val PopupTextMaxWidth = 640.dp
private val PopupTextMaxHeight = 320.dp
private const val PopupViewportPadding = 18
private const val PopupLineSpacing = 10
private val PopupFallbackSize = Dimension(460, 220)
private const val PopupConnectorLayerOffset = 5
private const val PopupAvoidAnimationDurationMs = 220
private const val PopupAvoidAnimationTimerDelayMs = 16
private const val PopupAvoidAnimationClientProperty = "walkthrough.popup.avoid.animation"
private const val PopupDragHandleHeightPx = 64
private const val PopupCloseButtonHitBoxPx = 60

data class WalkthroughItem(
    val text: String,
    val file: String? = null,
    val line: Int? = null
)

fun showWalkthroughItems(project: Project, editor: Editor, items: List<WalkthroughItem>) {
    var popupRef: JBPopup? = null
    var connectorRef: PopupConnectorOverlay? = null
    var panelRef: JComponent? = null
    var currentEditor = editor
    var currentItem = items.first()

    val panel = JewelComposePanel {
        WalkthroughItemContent(
            items = items,
            onItemDisplayed = { item ->
                val popup = popupRef
                val panelComponent = panelRef
                if (popup != null && panelComponent != null) {
                    currentItem = item
                    currentEditor = navigateToItem(project, currentEditor, item) ?: currentEditor
                    movePopupNearItem(popup, panelComponent, currentEditor, item) {
                        connectorRef?.repaintConnector()
                    }
                    connectorRef?.update(currentEditor, item)
                }
            },
            onClose = { popupRef?.cancel() }
        )
    }.apply {
        isOpaque = false
    }
    panelRef = panel
    installPopupDragHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        lineProvider = { currentItem.line },
        onPopupMoved = { connectorRef?.repaintConnector() }
    )

    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, panel)
        .setProject(project)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnClickOutside(false)
        .setShowBorder(false)
        .setShowShadow(false)
        .setLocateWithinScreenBounds(true)
        .createPopup()
        .also { createdPopup ->
            createdPopup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    stopPopupAvoidAnimation(createdPopup)
                    connectorRef?.dispose()
                    connectorRef = null
                }
            })
        }

    popupRef = popup
    connectorRef = PopupConnectorOverlay(popup).also {
        it.update(currentEditor, items.first())
    }
    movePopupNearItem(popup, panel, currentEditor, items.first()) {
        connectorRef?.repaintConnector()
    }
}

private fun navigateToItem(project: Project, fallbackEditor: Editor, item: WalkthroughItem): Editor? {
    val fileEditorManager = FileEditorManager.getInstance(project)

    if (item.file != null) {
        val basePath = project.basePath ?: return null
        val virtualFile = LocalFileSystem.getInstance().findFileByPath("$basePath/${item.file}") ?: return null
        val lineIndex = (item.line ?: 1).coerceAtLeast(1) - 1
        return fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true)
    }

    val currentEditor = fileEditorManager.selectedTextEditor ?: fallbackEditor
    if (item.line != null) {
        val logicalLine = (item.line - 1).coerceIn(0, currentEditor.document.lineCount - 1)
        currentEditor.caretModel.moveToLogicalPosition(LogicalPosition(logicalLine, 0))
        currentEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    return currentEditor
}

private fun movePopupNearItem(
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
        ?: popup.content?.preferredSize.usableSize()
        ?: popup.size.usableSize()
        ?: PopupFallbackSize

    popup.setSize(popupSize)

    val screenPoint = calculatePopupScreenPoint(editor, popupSize, item.line)
    if (popup.isVisible) {
        popup.setLocation(screenPoint)
        popup.moveToFitScreen()
    } else {
        popup.showInScreenCoordinates(editor.contentComponent, screenPoint)
        popup.moveToFitScreen()
    }
    onLocationChanged?.invoke()
    movePopupOutOfLineIfNeeded(popup, editor, item.line, onLocationChanged)
}

private fun movePopupBy(
    popup: JBPopup,
    editor: Editor,
    line: Int?,
    deltaX: Float,
    deltaY: Float,
    onLocationChanged: (() -> Unit)? = null
) {
    stopPopupAvoidAnimation(popup)
    val currentLocation = popupScreenLocation(popup) ?: return
    val popupSize = popup.size.usableSize()
        ?: popup.content?.size.usableSize()
        ?: popup.content?.preferredSize.usableSize()
        ?: PopupFallbackSize
    val movedPoint = Point(
        currentLocation.x + deltaX.roundToInt(),
        currentLocation.y + deltaY.roundToInt()
    )
    val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
    if (rootPane != null && rootPane.isShowing) {
        val rootLocation = Point(0, 0).also { SwingUtilities.convertPointToScreen(it, rootPane) }
        val maxX = (rootLocation.x + rootPane.width - popupSize.width - PopupViewportPadding).coerceAtLeast(rootLocation.x + PopupViewportPadding)
        val maxY = (rootLocation.y + rootPane.height - popupSize.height - PopupViewportPadding).coerceAtLeast(rootLocation.y + PopupViewportPadding)
        movedPoint.x = movedPoint.x.coerceIn(rootLocation.x + PopupViewportPadding, maxX)
        movedPoint.y = movedPoint.y.coerceIn(rootLocation.y + PopupViewportPadding, maxY)
    }
    popup.setLocation(movedPoint)
    popup.moveToFitScreen()
    onLocationChanged?.invoke()
    movePopupOutOfLineIfNeeded(popup, editor, line, onLocationChanged)
}

private fun Dimension?.usableSize(): Dimension? =
    this?.takeIf { it.width > 0 && it.height > 0 }

private fun popupScreenLocation(popup: JBPopup): Point? =
    runCatching { popup.locationOnScreen }.getOrNull()

private fun popupScreenBounds(popup: JBPopup): Rectangle2D.Float? {
    val location = popupScreenLocation(popup) ?: return null
    val size = popup.size.usableSize()
        ?: popup.content?.size.usableSize()
        ?: popup.content?.preferredSize.usableSize()
        ?: return null
    return Rectangle2D.Float(location.x.toFloat(), location.y.toFloat(), size.width.toFloat(), size.height.toFloat())
}

private fun movePopupOutOfLineIfNeeded(
    popup: JBPopup,
    editor: Editor,
    line: Int?,
    onLocationChanged: (() -> Unit)? = null
) {
    val currentLocation = popupScreenLocation(popup) ?: return
    val popupSize = popup.size.usableSize()
        ?: popup.content?.size.usableSize()
        ?: popup.content?.preferredSize.usableSize()
        ?: return
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
    val timer = Timer(PopupAvoidAnimationTimerDelayMs) {
        val elapsed = (System.currentTimeMillis() - animationStart).coerceAtMost(PopupAvoidAnimationDurationMs.toLong())
        val progress = elapsed.toFloat() / PopupAvoidAnimationDurationMs
        val easedProgress = cubicEaseInOut(progress.coerceIn(0f, 1f))
        val currentX = lerp(startLocation.x.toFloat(), targetLocation.x.toFloat(), easedProgress)
        val currentY = lerp(startLocation.y.toFloat(), targetLocation.y.toFloat(), easedProgress)
        popup.setLocation(Point(currentX.roundToInt(), currentY.roundToInt()))
        onLocationChanged?.invoke()

        if (elapsed >= PopupAvoidAnimationDurationMs) {
            popup.setLocation(targetLocation)
            popup.moveToFitScreen()
            onLocationChanged?.invoke()
            stopPopupAvoidAnimation(popup)
        }
    }.apply {
        isRepeats = true
        start()
    }

    popup.content?.putClientProperty(PopupAvoidAnimationClientProperty, timer)
}

private fun stopPopupAvoidAnimation(popup: JBPopup) {
    (popup.content?.getClientProperty(PopupAvoidAnimationClientProperty) as? Timer)?.stop()
    popup.content?.putClientProperty(PopupAvoidAnimationClientProperty, null)
}

private fun installPopupDragHandler(
    panel: JComponent,
    popupProvider: () -> JBPopup?,
    editorProvider: () -> Editor,
    lineProvider: () -> Int?,
    onPopupMoved: () -> Unit
) {
    var lastScreenPoint: Point? = null
    var dragging = false

    val dragListener = object : MouseAdapter() {
        override fun mousePressed(event: MouseEvent) {
            if (event.button != MouseEvent.BUTTON1 || !isWithinDragHandle(panel, event.component, event.point)) {
                dragging = false
                lastScreenPoint = null
                return
            }
            dragging = true
            lastScreenPoint = event.locationOnScreen
        }

        override fun mouseDragged(event: MouseEvent) {
            if (!dragging) {
                return
            }
            val popup = popupProvider() ?: return
            val previousScreenPoint = lastScreenPoint ?: event.locationOnScreen
            val currentScreenPoint = event.locationOnScreen
            movePopupBy(
                popup = popup,
                editor = editorProvider(),
                line = lineProvider(),
                deltaX = (currentScreenPoint.x - previousScreenPoint.x).toFloat(),
                deltaY = (currentScreenPoint.y - previousScreenPoint.y).toFloat(),
                onLocationChanged = onPopupMoved
            )
            lastScreenPoint = currentScreenPoint
        }

        override fun mouseReleased(event: MouseEvent) {
            dragging = false
            lastScreenPoint = null
        }
    }

    attachMouseListenersRecursively(panel, dragListener)
}

private fun attachMouseListenersRecursively(component: Component, listener: MouseAdapter) {
    component.addMouseListener(listener)
    component.addMouseMotionListener(listener)
    if (component is Container) {
        component.components.forEach { child ->
            attachMouseListenersRecursively(child, listener)
        }
    }
}

private fun isWithinDragHandle(panel: JComponent, component: Component, point: Point): Boolean {
    val pointInPanel = SwingUtilities.convertPoint(component, point, panel)
    return pointInPanel.y <= PopupDragHandleHeightPx &&
        pointInPanel.x <= panel.width - PopupCloseButtonHitBoxPx
}

private fun cubicEaseInOut(progress: Float): Float =
    if (progress < 0.5f) {
        4f * progress * progress * progress
    } else {
        val inverted = -2f * progress + 2f
        1f - (inverted * inverted * inverted) / 2f
    }

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun calculatePopupScreenPoint(editor: Editor, popupSize: Dimension, line: Int?): Point {
    val visibleArea = editor.scrollingModel.visibleArea
    val targetLine = resolveTargetLine(editor, line)
    val lineStartOffset = editor.document.getLineStartOffset(targetLine)
    val linePoint = editor.visualPositionToXY(editor.offsetToVisualPosition(lineStartOffset))

    val viewportLineY = linePoint.y - visibleArea.y
    val maxY = (visibleArea.height - popupSize.height - PopupViewportPadding).coerceAtLeast(PopupViewportPadding)
    val belowY = viewportLineY + editor.lineHeight + PopupLineSpacing
    val aboveY = viewportLineY - popupSize.height - PopupLineSpacing

    val targetY = when {
        belowY <= maxY -> belowY
        aboveY >= PopupViewportPadding -> aboveY
        else -> viewportLineY.coerceIn(PopupViewportPadding, maxY)
    }

    val targetX = (visibleArea.width - popupSize.width - PopupViewportPadding).coerceAtLeast(PopupViewportPadding)
    return Point(targetX, targetY).also {
        SwingUtilities.convertPointToScreen(it, editor.contentComponent)
    }
}

private fun resolveTargetLine(editor: Editor, line: Int?): Int {
    val lineCount = editor.document.lineCount.coerceAtLeast(1)
    val targetLine = if (line != null) {
        line - 1
    } else {
        editor.caretModel.primaryCaret.logicalPosition.line
    }
    return targetLine.coerceIn(0, lineCount - 1)
}

private data class LineScreenGeometry(
    val anchorX: Float,
    val topY: Float,
    val bottomY: Float,
    val centerY: Float,
    val viewportTopY: Float,
    val viewportBottomY: Float
)

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
    val lineTopY = contentOrigin.y + lineEndPoint.y.toFloat()
    val lineBottomY = lineTopY + editor.lineHeight
    return LineScreenGeometry(
        anchorX = contentOrigin.x + anchorX,
        topY = lineTopY,
        bottomY = lineBottomY,
        centerY = lineTopY + editor.lineHeight / 2f,
        viewportTopY = contentOrigin.y + visibleArea.y.toFloat(),
        viewportBottomY = contentOrigin.y + visibleArea.y.toFloat() + visibleArea.height
    )
}

private fun avoidLineOverlap(
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

    val minY = lineGeometry.viewportTopY + PopupViewportPadding
    val maxY = (lineGeometry.viewportBottomY - popupSize.height - PopupViewportPadding).coerceAtLeast(minY)
    val aboveY = lineGeometry.topY - popupSize.height - PopupLineSpacing
    val belowY = lineGeometry.bottomY + PopupLineSpacing
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

private fun calculateLineScreenPoint(editor: Editor, line: Int?): Point {
    val lineGeometry = calculateLineScreenGeometry(editor, line)
    return Point(lineGeometry.anchorX.roundToInt(), lineGeometry.centerY.roundToInt())
}

private class PopupConnectorOverlay(
    private val popup: JBPopup
) : JComponent(), VisibleAreaListener {
    private var editor: Editor? = null
    private var item: WalkthroughItem? = null
    private var layeredPane: JLayeredPane? = null
    private val layeredPaneResizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
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
                pane.add(this, Integer.valueOf(JLayeredPane.POPUP_LAYER - PopupConnectorLayerOffset))
            }
        }
        refreshBounds()
        repaintConnector()
    }

    fun repaintConnector() {
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
        val currentEditor = editor
        val currentItem = item
        if (currentEditor != null && currentItem != null) {
            movePopupOutOfLineIfNeeded(popup, currentEditor, currentItem.line, this::repaintConnector)
        }
        repaintConnector()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val currentEditor = editor ?: return
        val currentItem = item ?: return
        val pane = layeredPane ?: return
        val popupBounds = popupScreenBounds(popup) ?: return
        val targetPointOnScreen = calculateLineScreenPoint(currentEditor, currentItem.line)

        val popupOrigin = Point(popupBounds.x.roundToInt(), popupBounds.y.roundToInt()).also {
            SwingUtilities.convertPointFromScreen(it, pane)
        }
        val targetPoint = Point(targetPointOnScreen).also {
            SwingUtilities.convertPointFromScreen(it, pane)
        }
        val popupRect = Rectangle2D.Float(
            popupOrigin.x.toFloat(),
            popupOrigin.y.toFloat(),
            popupBounds.width,
            popupBounds.height
        )
        val arrowTarget = Point2D.Float(targetPoint.x.toFloat(), targetPoint.y.toFloat())
        val anchor = nearestBorderAnchor(popupRect, arrowTarget)
        val connector = buildConnector(anchor, arrowTarget)

        val g2 = graphics.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = AwtColor(255, 88, 88, 80)
        g2.stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(connector.path)
        g2.color = AwtColor(255, 136, 136, 235)
        g2.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(connector.path)
        drawArrowHead(g2, connector.end, connector.endControl)
        g2.dispose()
    }

    private fun refreshBounds() {
        layeredPane?.let { pane ->
            setBounds(0, 0, pane.width, pane.height)
            if (parent !== pane) {
                pane.add(this, Integer.valueOf(JLayeredPane.POPUP_LAYER - PopupConnectorLayerOffset))
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
    val inset = 20f
    val minY = popupRect.y + inset
    val maxY = (popupRect.y + popupRect.height - inset).coerceAtLeast(minY)
    val minX = popupRect.x + inset
    val maxX = (popupRect.x + popupRect.width - inset).coerceAtLeast(minX)
    val candidates = listOf(
        ConnectorAnchor(Point2D.Float(popupRect.x, target.y.coerceIn(minY, maxY)), ConnectorSide.Left),
        ConnectorAnchor(Point2D.Float(popupRect.x + popupRect.width, target.y.coerceIn(minY, maxY)), ConnectorSide.Right),
        ConnectorAnchor(Point2D.Float(target.x.coerceIn(minX, maxX), popupRect.y), ConnectorSide.Top),
        ConnectorAnchor(Point2D.Float(target.x.coerceIn(minX, maxX), popupRect.y + popupRect.height), ConnectorSide.Bottom)
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
    val startPull = (abs(dx) * 0.32f).coerceAtLeast(72f)
    val verticalPull = (abs(dy) * 0.28f).coerceAtLeast(54f)
    val startControl = when (anchor.side) {
        ConnectorSide.Left -> Point2D.Float(anchor.point.x - startPull, anchor.point.y)
        ConnectorSide.Right -> Point2D.Float(anchor.point.x + startPull, anchor.point.y)
        ConnectorSide.Top -> Point2D.Float(anchor.point.x, anchor.point.y - verticalPull)
        ConnectorSide.Bottom -> Point2D.Float(anchor.point.x, anchor.point.y + verticalPull)
    }
    val endControl = if (abs(dx) >= abs(dy)) {
        Point2D.Float(
            end.x - dx.sign.coerceNonZero() * (abs(dx) * 0.24f).coerceAtLeast(48f),
            end.y - dy * 0.12f
        )
    } else {
        Point2D.Float(
            end.x - dx * 0.12f,
            end.y - dy.sign.coerceNonZero() * (abs(dy) * 0.24f).coerceAtLeast(42f)
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

private fun Float.coerceNonZero(): Float = if (this == 0f) 1f else this

private fun drawArrowHead(graphics: Graphics2D, end: Point2D.Float, endControl: Point2D.Float) {
    val angle = atan2((end.y - endControl.y).toDouble(), (end.x - endControl.x).toDouble())
    val spread = Math.toRadians(24.0)
    val headLength = 13.0
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
    graphics.color = AwtColor(255, 102, 102, 215)
    graphics.fill(arrow)
}

@Composable
fun WalkthroughItemContent(
    items: List<WalkthroughItem>,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val item = items[currentIndex]
    val scrollState = rememberScrollState()
    val transition = rememberInfiniteTransition()
    val gradientShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glowShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(currentIndex, item.text, item.file, item.line) {
        scrollState.scrollTo(0)
        onItemDisplayed(item)
    }

    val shape = RoundedCornerShape(PopupCornerRadius)
    val scrollbarStyle = ScrollbarStyle(
        minimalHeight = 28.dp,
        thickness = 6.dp,
        shape = CircleShape,
        hoverDurationMillis = 300,
        unhoverColor = Color(0x66818CF8),
        hoverColor = Color(0xFFE879F9)
    )
    val showScrollbar = scrollState.maxValue > 0

    CompositionLocalProvider(LocalScrollbarStyle provides scrollbarStyle) {
        Box(
            modifier = Modifier
                .shadow(26.dp, shape, clip = false)
                .clip(shape)
                .drawWithCache {
                    val cornerRadius = CornerRadius(PopupCornerRadius.toPx(), PopupCornerRadius.toPx())
                    val backgroundBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF160326),
                            Color(0xFF3D0A68),
                            Color(0xFF7C1B9A),
                            Color(0xFF1D4ED8)
                        ),
                        start = Offset(size.width * (gradientShift - 0.7f), -size.height * 0.2f),
                        end = Offset(size.width * (gradientShift + 0.5f), size.height * 1.1f)
                    )
                    val glowBrush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x66F0ABFC),
                            Color(0x55C084FC),
                            Color.Transparent
                        ),
                        center = Offset(size.width * (0.18f + glowShift * 0.62f), size.height * 0.2f),
                        radius = size.minDimension * 0.95f
                    )
                    val borderBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFA855F7),
                            Color(0xFFE879F9),
                            Color(0xFF60A5FA),
                            Color(0xFFA855F7)
                        ),
                        start = Offset(size.width * (gradientShift - 1f), 0f),
                        end = Offset(size.width * gradientShift, size.height)
                    )

                    onDrawBehind {
                        drawRoundRect(brush = backgroundBrush, cornerRadius = cornerRadius)
                        drawRoundRect(brush = glowBrush, cornerRadius = cornerRadius)
                        drawRoundRect(
                            color = Color(0x9A0A0114),
                            cornerRadius = cornerRadius
                        )
                        drawRoundRect(
                            brush = borderBrush,
                            cornerRadius = cornerRadius,
                            alpha = 0.9f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
        ) {
            AiCloseButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                onClick = onClose
            )

            Column(
                modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 58.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    AiBadge()
                    if (items.size > 1) {
                        Text(
                            text = "${currentIndex + 1} / ${items.size}",
                            color = Color(0xFFE9D5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (item.line != null) {
                        Text(
                            text = "Line ${item.line}",
                            color = Color(0xFFE9D5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .widthIn(min = PopupTextMinWidth, max = PopupTextMaxWidth)
                        .heightIn(max = PopupTextMaxHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 14.dp, end = 28.dp, bottom = 14.dp)
                            .verticalScroll(scrollState)
                    ) {
                        MarkdownContent(item.text)
                    }

                    if (showScrollbar) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(end = 6.dp, top = 10.dp, bottom = 10.dp)
                        ) {
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(scrollState),
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }

                if (items.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        AiNavButton(
                            label = "Previous",
                            enabled = currentIndex > 0,
                            emphasized = false,
                            onClick = { currentIndex-- }
                        )
                        AiNavButton(
                            label = "Next",
                            enabled = currentIndex < items.lastIndex,
                            emphasized = true,
                            onClick = { currentIndex++ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFA855F7), Color(0xFFE879F9), Color(0xFF60A5FA))
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "DESTINATION AI",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun AiCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✕",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AiNavButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val backgroundBrush = if (emphasized) {
        Brush.linearGradient(
            colors = listOf(Color(0xFFA855F7), Color(0xFFE879F9), Color(0xFF7C3AED))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.08f))
        )
    }
    val borderColor = if (emphasized) {
        Color(0xFFF0ABFC)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val textStyle = if (emphasized) {
        TextStyle(fontWeight = FontWeight.Bold)
    } else {
        TextStyle(fontWeight = FontWeight.Medium)
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundBrush, CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 13.sp,
            style = textStyle
        )
    }
}
