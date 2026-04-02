package com.forketyfork.walkthrough

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.projectOrNull
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private data class WalkthroughItemJson(val text: String?, val file: String?, val line: Int?)

class ShowWalkthroughItemsToolset : McpToolset {
    @McpTool(name = "show_walkthrough_items")
    @McpDescription(
        "Shows walkthrough items with navigation support. " +
            "Accepts one or more walkthrough items; the user can cycle through them " +
            "with Previous and Next buttons."
    )
    suspend fun showWalkthroughItems(
        @McpDescription(
            "JSON array of walkthrough items to display, e.g. " +
            "[{\"text\":\"Note 1\",\"file\":\"src/Foo.kt\",\"line\":10},{\"text\":\"Note 2\"}]. " +
            "Each item requires 'text'; 'file' (path relative to project root) and 'line' (1-based) are optional."
        ) items: String
    ): String {
        val project = currentCoroutineContext().projectOrNull
            ?: mcpFail("No active project")

        val itemList = try {
            val type = object : TypeToken<List<WalkthroughItemJson>>() {}.type
            val parsed: List<WalkthroughItemJson> = Gson().fromJson(items, type)
            parsed.map { entry ->
                WalkthroughItem(
                    text = entry.text ?: mcpFail("Each item must have a 'text' field"),
                    file = entry.file,
                    line = entry.line
                )
            }
        } catch (exception: JsonParseException) {
            mcpFail("Invalid entries JSON: ${exception.message}")
        } catch (exception: IllegalStateException) {
            mcpFail("Invalid entries JSON: ${exception.message}")
        }

        if (itemList.isEmpty()) mcpFail("items must not be empty")

        val shown = withContext(Dispatchers.EDT) {
            val firstItem = itemList.first()
            val editor = navigateAndGetEditor(project, firstItem)

            if (editor != null) {
                showWalkthroughItems(project, editor, itemList)
                true
            } else {
                false
            }
        }

        return if (shown) "Walkthrough items shown" else mcpFail("No active editor")
    }
}

private fun navigateAndGetEditor(project: Project, item: WalkthroughItem): Editor? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    return item.file
        ?.let { relativePath -> openReferencedEditor(project, fileEditorManager, item, relativePath) }
        ?: moveCaretIfNeeded(fileEditorManager.selectedTextEditor, item.line)
}

private fun openReferencedEditor(
    project: Project,
    fileEditorManager: FileEditorManager,
    item: WalkthroughItem,
    relativePath: String
): Editor? {
    val virtualFile = findReferencedFile(project, relativePath) ?: return null
    val lineIndex = (item.line ?: 1) - 1
    return fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile, lineIndex, 0), true)
}

private fun findReferencedFile(project: Project, relativePath: String) =
    project.basePath
        ?.let { "$it/$relativePath" }
        ?.let(LocalFileSystem.getInstance()::findFileByPath)

private fun moveCaretIfNeeded(editor: Editor?, line: Int?): Editor? {
    if (editor != null && line != null) {
        editor.caretModel.moveToLogicalPosition(LogicalPosition(line - 1, 0))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }
    return editor
}
