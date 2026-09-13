package com.forketyfork.walkthrough

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
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
): ResolvedWalkthroughTarget? = run {
    val editor = fileEditorManager.selectedTextEditor ?: fallbackEditor ?: return@run null
    if (!isResolvableWalkthroughLine(item.line, editor.document.lineCount)) {
        return@run ResolvedWalkthroughTarget(editor, item.withFallbackAnchor())
    }
    val resolvedItem = item.withResolvedEndLine(editor.document.lineCount)
    moveEditorCaretToLine(editor, resolvedItem.line)
    ResolvedWalkthroughTarget(editor, resolvedItem)
}

private fun resolveFileTarget(
    project: Project,
    fileEditorManager: FileEditorManager,
    item: WalkthroughItem,
    relativePath: String,
): ResolvedWalkthroughTarget? = run {
    val virtualFile = findWalkthroughFile(project, relativePath) ?: return@run null
    val lineCount = virtualFile.lineCount() ?: return@run null
    if (!isResolvableWalkthroughLine(item.line, lineCount)) return@run null
    val resolvedItem = item.withResolvedEndLine(lineCount)
    val editor = openEditor(project, fileEditorManager, virtualFile, resolvedItem)
        ?: return@run null
    ResolvedWalkthroughTarget(editor, resolvedItem)
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
    }.getOrNull()?.also { editor -> moveEditorCaretToLine(editor, item.line) }
}
