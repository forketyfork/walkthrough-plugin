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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private data class WalkthroughItemJson(val text: String?, val file: String?, val line: Int?)

class ShowWalkthroughItemsToolset : McpToolset {
    // Discovered and invoked via reflection by the MCP server framework
    @Suppress("unused")
    @McpTool(name = "show_walkthrough_items")
    @McpDescription(
        "Shows and stores walkthrough items with navigation support. " +
            "Accepts one or more walkthrough items; the user can cycle through them " +
            "with Previous and Next buttons. The walkthrough is saved to this project's history."
    )
    suspend fun showWalkthroughItems(
        @McpDescription(
            "Short human-readable description shown in the project walkthrough history. " +
            "Use a concise phrase that helps the user recognize this walkthrough later."
        ) description: String,
        @McpDescription(
            "JSON array of walkthrough items to display, e.g. " +
            "[{\"text\":\"Note 1\",\"file\":\"src/Foo.kt\",\"line\":10},{\"text\":\"Note 2\"}]. " +
            "Each item requires 'text'; 'file' (path relative to project root) and 'line' (1-based) are optional. " +
            "The 'line' value navigates the editor to that exact position, so it must be accurate. " +
            "Verify line numbers by reading the actual file before calling this tool — do not estimate from diffs or memory."
        ) items: String
    ): String {
        val project = currentCoroutineContext().projectOrNull
            ?: mcpFail("No active project")
        val trimmedDescription = description.trim()
        if (trimmedDescription.isBlank()) mcpFail("description must not be blank")

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
            showWalkthroughItems(project, itemList)
        }
        if (!shown) mcpFail("No active editor")

        val record = WalkthroughHistoryService.getInstance(project)
            .save(trimmedDescription, itemList)

        return record
            ?.let { savedRecord -> "Walkthrough items shown and saved as ${savedRecord.id}" }
            ?: "Walkthrough items shown; history was not saved"
    }
}
