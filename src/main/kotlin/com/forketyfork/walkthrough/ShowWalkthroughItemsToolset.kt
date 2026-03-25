package com.forketyfork.walkthrough

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.projectOrNull
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private data class WalkthroughItemJson(val text: String?, val file: String?, val line: Int?)

class ShowWalkthroughItemsToolset : McpToolset {
    @McpTool
    @McpDescription("Shows walkthrough items with navigation support. Accepts one or more walkthrough items; the user can cycle through them with Previous/Next buttons.")
    suspend fun show_walkthrough_items(
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
            parsed.map { e ->
                WalkthroughItem(
                    text = e.text ?: mcpFail("Each item must have a 'text' field"),
                    file = e.file,
                    line = e.line
                )
            }
        } catch (e: Exception) {
            mcpFail("Invalid entries JSON: ${e.message}")
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

private fun navigateAndGetEditor(
    project: com.intellij.openapi.project.Project,
    item: WalkthroughItem
): com.intellij.openapi.editor.Editor? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    return if (item.file != null) {
        val basePath = project.basePath ?: return null
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath("$basePath/${item.file}") ?: return null
        val lineIndex = (item.line ?: 1) - 1
        fileEditorManager.openTextEditor(
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, virtualFile, lineIndex, 0), true
        )
    } else {
        val editor = fileEditorManager.selectedTextEditor
        if (editor != null && item.line != null) {
            editor.caretModel.moveToLogicalPosition(
                com.intellij.openapi.editor.LogicalPosition(item.line - 1, 0)
            )
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
        }
        editor
    }
}
