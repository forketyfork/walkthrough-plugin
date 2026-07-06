package com.forketyfork.walkthrough

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

internal data class ResolvedWalkthroughTarget(val editor: Editor, val popupItem: WalkthroughItem)

internal fun resolveWalkthroughTarget(
    project: Project,
    fallbackEditor: Editor?,
    item: WalkthroughItem,
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
    item: WalkthroughItem,
): ResolvedWalkthroughTarget? {
    val editor = fileEditorManager.selectedTextEditor ?: fallbackEditor ?: return null
    val popupItem = if (isResolvableWalkthroughLine(item.line, editor.document.lineCount)) {
        val resolvedItem = item.withResolvedEndLine(editor.document.lineCount)
        moveCaretToLine(editor, resolvedItem.line, resolvedItem.endLine)
        resolvedItem
    } else {
        item.withFallbackAnchor()
    }
    return ResolvedWalkthroughTarget(editor, popupItem)
}

private fun resolveFileTarget(
    project: Project,
    fileEditorManager: FileEditorManager,
    item: WalkthroughItem,
    relativePath: String,
): ResolvedWalkthroughTarget? {
    val virtualFile = findWalkthroughFile(project, relativePath)
    val resolvedItem = virtualFile?.lineCount()
        ?.takeIf { lineCount -> isResolvableWalkthroughLine(item.line, lineCount) }
        ?.let(item::withResolvedEndLine)
    val editor = if (virtualFile != null && resolvedItem != null) {
        openEditor(project, fileEditorManager, virtualFile, resolvedItem)
    } else {
        null
    }
    return if (editor != null && resolvedItem != null) ResolvedWalkthroughTarget(editor, resolvedItem) else null
}

private fun findWalkthroughFile(project: Project, relativePath: String) =
    resolveProjectRelativeWalkthroughPath(project.basePath, relativePath)
        ?.toString()
        ?.let(LocalFileSystem.getInstance()::findFileByPath)

private fun VirtualFile.lineCount(): Int? = WriteIntentReadAction.compute<Int?> {
    FileDocumentManager.getInstance().getDocument(this)?.lineCount
}

private fun openEditor(
    project: Project,
    fileEditorManager: FileEditorManager,
    virtualFile: VirtualFile,
    item: WalkthroughItem,
): Editor? {
    val lineIndex = (item.line ?: 1).coerceAtLeast(1) - 1
    return runCatching {
        fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true)
    }.getOrNull()?.also { editor -> applyLineRangeSelection(editor, item.line, item.endLine) }
}

internal fun moveCaretToLine(editor: Editor, line: Int?, endLine: Int? = null) {
    if (line == null) return
    val lineIndex = line - 1
    editor.caretModel.moveToLogicalPosition(LogicalPosition(lineIndex, 0))
    applyLineRangeSelection(editor, line, endLine)
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
}

private fun applyLineRangeSelection(editor: Editor, line: Int?, endLine: Int?) {
    if (line == null || endLine == null) return
    val document = editor.document
    val startOffset = document.getLineStartOffset(line - 1)
    val endLineIndex = endLine - 1
    val endOffset = if (endLineIndex >= document.lineCount - 1) {
        document.getLineEndOffset(document.lineCount - 1)
    } else {
        document.getLineStartOffset(endLineIndex + 1)
    }
    editor.selectionModel.setSelection(startOffset, endOffset)
}
