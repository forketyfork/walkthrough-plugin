package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Color as AwtColor
import java.awt.Dimension
import javax.swing.JComponent

fun showWalkthroughItems(project: Project, editor: Editor, items: List<WalkthroughItem>) {
    if (items.isEmpty()) return
    val sessionDisposable = Disposer.newDisposable("WalkthroughPopupSession")
    Disposer.register(project, sessionDisposable)

    var popupRef: WalkthroughPopupSurface? = null
    var currentEditor = editor

    fun repaintPopup() {
        popupRef?.let { popup ->
            popup.refreshBounds()
            popup.repaint()
        }
    }

    val panel = createWalkthroughPanel(
        project = project,
        items = items,
        onItemDisplayed = { item ->
            popupRef?.let { popup ->
                currentEditor = navigateToItem(project, currentEditor, item)
                popup.update(currentEditor, item)
                movePopupNearItem(popup, currentEditor, item, ::repaintPopup)
            }
        },
        onClose = { popupRef?.cancel() }
    )
    makeComponentHierarchyTransparent(panel)

    installPopupInteractionHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        onPopupMoved = ::repaintPopup
    )

    val popup = WalkthroughPopupSurface(
        content = panel,
        onCloseRequested = { Disposer.dispose(sessionDisposable) }
    )
    popupRef = popup
    Disposer.register(sessionDisposable, popup)
    popup.update(currentEditor, items.first())
    movePopupNearItem(popup, currentEditor, items.first(), ::repaintPopup)
}

private fun createWalkthroughPanel(
    project: Project,
    items: List<WalkthroughItem>,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
): JComponent =
    JewelComposePanel {
        WalkthroughItemContent(
            project = project,
            items = items,
            onItemDisplayed = onItemDisplayed,
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

private fun navigateToItem(project: Project, fallbackEditor: Editor, item: WalkthroughItem): Editor {
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
