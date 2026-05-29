package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.SwingUtilities
import java.awt.Color as AwtColor

fun showWalkthroughItems(project: Project, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, items, acceptsQuestions = false) != null

fun showWalkthroughItems(project: Project, editor: Editor, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, editor, items, acceptsQuestions = false) != null

fun showWalkthroughSession(
    project: Project,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean,
): WalkthroughSession? {
    val fallbackEditor = FileEditorManager.getInstance(project).selectedTextEditor
    val target = items.firstOrNull()
        ?.let { item -> resolveWalkthroughTarget(project, fallbackEditor, item) }
    return target?.let { resolved -> showWalkthroughSession(project, resolved.editor, items, acceptsQuestions) }
}

@Suppress("LongMethod")
fun showWalkthroughSession(
    project: Project,
    editor: Editor,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean,
): WalkthroughSession? {
    val firstTarget = items.firstOrNull()
        ?.let { item -> resolveWalkthroughTarget(project, editor, item) }
        ?: return null
    val paletteState = mutableStateOf(WalkthroughSettings.getInstance().selectedPalette)
    val sessionDisposable = Disposer.newCheckedDisposable("WalkthroughPopupSession")
    Disposer.register(project, sessionDisposable)

    val registry = WalkthroughSessionRegistry.getInstance(project)
    registry.swapActive(sessionDisposable)?.let(Disposer::dispose)
    val session = registry.create(items, acceptsQuestions)
    Disposer.register(sessionDisposable) {
        registry.remove(session.id)
        registry.clearActive(sessionDisposable)
    }

    var popupRef: WalkthroughPopupSurface? = null
    var currentEditor = firstTarget.editor
    var pendingNavigationId = 0

    fun repaintPopup() {
        popupRef?.let { popup ->
            popup.refreshBounds()
            popup.repaint()
        }
    }

    fun updatePopupPalette(palette: WalkthroughPalette) {
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed) {
                return@invokeLater
            }
            paletteState.value = palette
            popupRef?.updatePalette(palette)
        }
    }

    fun showItem(item: WalkthroughItem) {
        val popup = popupRef ?: return
        val target = resolveWalkthroughTarget(project, currentEditor, item) ?: return
        currentEditor = target.editor
        popup.update(currentEditor, target.popupItem)
        popup.connectorHidden = false
        applyPopupGeometryForItem(popup, currentEditor, target.popupItem)
    }

    fun scheduleItemNavigation(item: WalkthroughItem) {
        pendingNavigationId += 1
        val navigationId = pendingNavigationId
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed || navigationId != pendingNavigationId) {
                return@invokeLater
            }
            showItem(item)
        }
    }

    val panel = createWalkthroughPanel(
        project = project,
        session = session,
        paletteProvider = { paletteState.value },
        onItemDisplayed = ::scheduleItemNavigation,
        onNavigateToSource = ::scheduleItemNavigation,
        onClose = { popupRef?.cancel() },
    )
    makeComponentHierarchyTransparent(panel)

    project.messageBus.connect(sessionDisposable).subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor
                popupRef?.connectorHidden = selectedEditor?.document !== currentEditor.document
            }
        },
    )
    ApplicationManager.getApplication().messageBus.connect(sessionDisposable).subscribe(
        WalkthroughSettingsListener.TOPIC,
        object : WalkthroughSettingsListener {
            override fun paletteChanged(palette: WalkthroughPalette) {
                updatePopupPalette(palette)
            }
        },
    )

    val popup = WalkthroughPopupSurface(
        content = panel,
        palette = paletteState.value,
        onCloseRequested = {
            popupRef = null
            registry.remove(session.id)
            Disposer.dispose(sessionDisposable)
        },
        onInteractionEnd = { saveCurrentGeometry(popupRef) },
    )
    popupRef = popup
    Disposer.register(sessionDisposable, popup)
    popup.update(currentEditor, firstTarget.popupItem)
    applyPopupGeometryForItem(popup, currentEditor, firstTarget.popupItem)
    repaintPopup()
    return session
}

internal fun saveCurrentGeometry(popup: WalkthroughPopupSurface?) {
    val location = popup?.popupLocationOnScreen() ?: return
    val size = resolvePopupSize(popup) ?: return
    WalkthroughSettings.getInstance().saveGeometry(
        PopupGeometry(location.x, location.y, size.width, size.height),
    )
}

internal fun applyPopupGeometryForItem(popup: WalkthroughPopupSurface, editor: Editor, item: WalkthroughItem) {
    val settings = WalkthroughSettings.getInstance()
    val persisted = settings.loadGeometry()

    if (persisted == null) {
        movePopupNearItem(popup, editor, item)
    } else {
        val rootPane = SwingUtilities.getRootPane(editor.contentComponent)
        val maxWidth = rootPane?.let { it.width - 2 * WalkthroughPopupLayout.VIEWPORT_PADDING }
            ?: Int.MAX_VALUE
        val maxHeight = rootPane?.let { it.height - 2 * WalkthroughPopupLayout.VIEWPORT_PADDING }
            ?: Int.MAX_VALUE
        val clampedSize = clampPopupSize(
            Dimension(persisted.width, persisted.height),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
        )
        popup.popupSize = clampedSize
        val avoided = avoidLineOverlap(Point(persisted.x, persisted.y), clampedSize, editor, item.line)
        val constrained = constrainPopupScreenLocation(editor, avoided, clampedSize)
        val reAvoided = avoidLineOverlap(constrained, clampedSize, editor, item.line)
        val finalPoint = constrainPopupScreenLocation(editor, reAvoided, clampedSize)
        popup.show(editor, finalPoint)
    }

    val finalLocation = popup.popupLocationOnScreen() ?: return
    val finalSize = resolvePopupSize(popup) ?: return
    val finalGeometry = PopupGeometry(finalLocation.x, finalLocation.y, finalSize.width, finalSize.height)
    if (persisted != finalGeometry) {
        settings.saveGeometry(finalGeometry)
    }
}

@Suppress("LongParameterList")
internal fun createWalkthroughPanel(
    project: Project,
    session: WalkthroughSession,
    paletteProvider: () -> WalkthroughPalette,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onNavigateToSource: (WalkthroughItem) -> Unit,
    onClose: () -> Unit,
): JComponent = JewelComposePanel {
    WalkthroughItemContent(
        project = project,
        session = session,
        palette = paletteProvider(),
        onItemDisplay = onItemDisplayed,
        onNavigateToSource = onNavigateToSource,
        onClose = onClose,
    )
}.apply {
    isOpaque = false
    @Suppress("UseJBColor")
    background = AwtColor(0, 0, 0, 0)
    minimumSize = Dimension(
        WalkthroughPopupLayout.MINIMUM_WIDTH_PX,
        WalkthroughPopupLayout.MINIMUM_HEIGHT_PX,
    )
    preferredSize = WalkthroughPopupLayout.fallbackSize
}
