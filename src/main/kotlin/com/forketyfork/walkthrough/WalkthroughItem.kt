package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Color as AwtColor
import java.awt.Dimension
import javax.swing.JComponent

internal object WalkthroughPopupLayout {
    val fallbackSize = Dimension(560, 300)
    const val MINIMUM_WIDTH_PX = 520
    const val MINIMUM_HEIGHT_PX = 260
    const val VIEWPORT_PADDING = 18
    const val LINE_SPACING = 10
    const val AVOID_ANIMATION_DURATION_MS = 220
    const val AVOID_ANIMATION_TIMER_DELAY_MS = 16
    const val AVOID_ANIMATION_CLIENT_PROPERTY = "walkthrough.popup.avoid.animation"
}

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

    fun repaintConnector() {
        connectorRef?.let { connector ->
            connector.refreshBounds()
            connector.repaint()
        }
    }

    val panel = JewelComposePanel {
        WalkthroughItemContent(
            project = project,
            items = items,
            onItemDisplayed = { item ->
                val popup = popupRef
                val panelComponent = panelRef
                if (popup != null && panelComponent != null) {
                    currentEditor = navigateToItem(project, currentEditor, item) ?: currentEditor
                    movePopupNearItem(popup, panelComponent, currentEditor, item, ::repaintConnector)
                    connectorRef?.update(currentEditor, item)
                }
            },
            onClose = { popupRef?.cancel() }
        )
    }.apply {
        isOpaque = false
        background = AwtColor(0, 0, 0, 0)
        minimumSize = Dimension(
            WalkthroughPopupLayout.MINIMUM_WIDTH_PX,
            WalkthroughPopupLayout.MINIMUM_HEIGHT_PX
        )
        preferredSize = WalkthroughPopupLayout.fallbackSize
    }
    panelRef = panel

    installPopupInteractionHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        onPopupMoved = ::repaintConnector
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
            makePopupHierarchyTransparent(createdPopup, panel)
            createdPopup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    stopPopupAvoidAnimation(createdPopup)
                    connectorRef?.dispose()
                    connectorRef = null
                }
            })
        }

    popupRef = popup
    connectorRef = PopupConnectorOverlay(popup).also { overlay ->
        overlay.update(currentEditor, items.first())
    }
    movePopupNearItem(popup, panel, currentEditor, items.first(), ::repaintConnector)
}

private fun navigateToItem(project: Project, fallbackEditor: Editor, item: WalkthroughItem): Editor? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    return item.file
        ?.let { relativePath -> openItemEditor(project, fileEditorManager, item, relativePath) }
        ?: moveCaretToItemLine(fileEditorManager.selectedTextEditor ?: fallbackEditor, item.line)
}

private fun openItemEditor(
    project: Project,
    fileEditorManager: FileEditorManager,
    item: WalkthroughItem,
    relativePath: String
): Editor? {
    val virtualFile = findWalkthroughFile(project, relativePath) ?: return null
    val lineIndex = (item.line ?: 1).coerceAtLeast(1) - 1
    return fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true)
}

private fun findWalkthroughFile(project: Project, relativePath: String) =
    project.basePath
        ?.let { "$it/$relativePath" }
        ?.let(LocalFileSystem.getInstance()::findFileByPath)

private fun moveCaretToItemLine(editor: Editor, line: Int?): Editor {
    if (line != null) {
        val logicalLine = (line - 1).coerceIn(0, editor.document.lineCount - 1)
        editor.caretModel.moveToLogicalPosition(LogicalPosition(logicalLine, 0))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }
    return editor
}
