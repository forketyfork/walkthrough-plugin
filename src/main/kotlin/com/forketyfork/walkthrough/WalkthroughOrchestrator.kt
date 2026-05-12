package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Color as AwtColor
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingUtilities

fun showWalkthroughItems(project: Project, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, items, acceptsQuestions = false) != null

fun showWalkthroughItems(project: Project, editor: Editor, items: List<WalkthroughItem>): Boolean =
    showWalkthroughSession(project, editor, items, acceptsQuestions = false) != null

fun showWalkthroughSession(
    project: Project,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean
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
    acceptsQuestions: Boolean
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
    Disposer.register(
        sessionDisposable,
        object : Disposable {
            override fun dispose() {
                registry.remove(session.id)
                registry.clearActive(sessionDisposable)
            }
        }
    )

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
        movePopupNearItem(popup, currentEditor, target.popupItem, ::repaintPopup)
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
        onClose = { popupRef?.cancel() }
    )
    makeComponentHierarchyTransparent(panel)

    installPopupInteractionHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        onPopupMoved = ::repaintPopup
    )

    project.messageBus.connect(sessionDisposable).subscribe(
        FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val selectedEditor = FileEditorManager.getInstance(project).selectedTextEditor
                popupRef?.connectorHidden = selectedEditor?.document !== currentEditor.document
            }
        }
    )
    ApplicationManager.getApplication().messageBus.connect(sessionDisposable).subscribe(
        WalkthroughSettingsListener.TOPIC,
        object : WalkthroughSettingsListener {
            override fun paletteChanged(palette: WalkthroughPalette) {
                updatePopupPalette(palette)
            }
        }
    )

    val popup = WalkthroughPopupSurface(
        content = panel,
        palette = paletteState.value,
        onCloseRequested = {
            popupRef = null
            registry.remove(session.id)
            Disposer.dispose(sessionDisposable)
        }
    )
    popupRef = popup
    Disposer.register(sessionDisposable, popup)
    popup.update(currentEditor, firstTarget.popupItem)
    movePopupNearItem(popup, currentEditor, firstTarget.popupItem, ::repaintPopup)
    return session
}

private fun createWalkthroughPanel(
    project: Project,
    session: WalkthroughSession,
    paletteProvider: () -> WalkthroughPalette,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onNavigateToSource: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
): JComponent =
    JewelComposePanel {
        WalkthroughItemContent(
            project = project,
            session = session,
            palette = paletteProvider(),
            onItemDisplayed = onItemDisplayed,
            onNavigateToSource = onNavigateToSource,
            onClose = onClose
        )
    }.apply {
        isOpaque = false
        @Suppress("UseJBColor")
        background = AwtColor(0, 0, 0, 0)
        minimumSize = Dimension(
            WalkthroughPopupLayout.MINIMUM_WIDTH_PX,
            WalkthroughPopupLayout.MINIMUM_HEIGHT_PX
        )
        preferredSize = WalkthroughPopupLayout.fallbackSize
    }
