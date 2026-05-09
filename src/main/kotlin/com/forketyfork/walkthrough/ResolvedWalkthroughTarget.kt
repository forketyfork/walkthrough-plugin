package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

internal data class ResolvedWalkthroughTarget(
    val editor: Editor,
    val popupItem: WalkthroughItem
)

internal fun resolveWalkthroughTarget(
    project: Project,
    fallbackEditor: Editor?,
    item: WalkthroughItem
): ResolvedWalkthroughTarget? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val fileTarget = item.file
        ?.let { relativePath -> resolveFileTarget(project, fileEditorManager, item, relativePath) }
    val fallbackItem = if (item.file != null && fileTarget == null) item.withFallbackAnchor() else item
    return fileTarget ?: resolveFallbackTarget(fileEditorManager, fallbackEditor, fallbackItem)
}

private fun resolveFallbackTarget(
    fileEditorManager: FileEditorManager,
    fallbackEditor: Editor?,
    item: WalkthroughItem
): ResolvedWalkthroughTarget? {
    val editor = fileEditorManager.selectedTextEditor ?: fallbackEditor ?: return null
    val popupItem = if (isResolvableWalkthroughLine(item.line, editor.document.lineCount)) {
        moveCaretToLine(editor, item.line)
        item
    } else {
        item.withFallbackAnchor()
    }
    return ResolvedWalkthroughTarget(editor, popupItem)
}

private fun resolveFileTarget(
    project: Project,
    fileEditorManager: FileEditorManager,
    item: WalkthroughItem,
    relativePath: String
): ResolvedWalkthroughTarget? {
    val virtualFile = findWalkthroughFile(project, relativePath)
    val lineCount = virtualFile?.lineCount()
    val editor = if (virtualFile != null && lineCount != null && isResolvableWalkthroughLine(item.line, lineCount)) {
        openEditor(project, fileEditorManager, virtualFile, item)
    } else {
        null
    }
    return editor?.let { ResolvedWalkthroughTarget(it, item) }
}

private fun findWalkthroughFile(project: Project, relativePath: String) =
    resolveProjectRelativeWalkthroughPath(project.basePath, relativePath)
        ?.toString()
        ?.let(LocalFileSystem.getInstance()::findFileByPath)

private fun VirtualFile.lineCount(): Int? =
    FileDocumentManager.getInstance().getDocument(this)?.lineCount

private fun openEditor(
    project: Project,
    fileEditorManager: FileEditorManager,
    virtualFile: VirtualFile,
    item: WalkthroughItem
): Editor? {
    val lineIndex = (item.line ?: 1).coerceAtLeast(1) - 1
    return runCatching {
        fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true)
    }.getOrNull()
}

private fun moveCaretToLine(editor: Editor, line: Int?) {
    if (line == null) return
    val lineIndex = line - 1
    editor.caretModel.moveToLogicalPosition(LogicalPosition(lineIndex, 0))
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
}
