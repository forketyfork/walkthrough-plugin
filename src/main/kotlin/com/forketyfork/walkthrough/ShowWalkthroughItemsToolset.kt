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
import com.intellij.openapi.project.Project
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
            "with Previous and Next buttons. The walkthrough is saved to this project's history. " +
            "Top-level items are auto-labeled '1', '2', '3', etc. " +
            "The returned message includes a walkthroughId; pass it to await_walkthrough_question " +
            "to react to follow-up questions the user types into the popup."
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
        val project = requireProject()
        val trimmedDescription = description.trim()
        if (trimmedDescription.isBlank()) mcpFail("description must not be blank")

        val parsedItems = parseItems(items)
        if (parsedItems.isEmpty()) mcpFail("items must not be empty")

        val labeledItems = assignTopLevelLabels(parsedItems)

        val session = withContext(Dispatchers.EDT) {
            showWalkthroughSession(project, labeledItems, acceptsQuestions = true)
        } ?: mcpFail("No active editor")

        val record = WalkthroughHistoryService.getInstance(project)
            .save(trimmedDescription, session.snapshotItems())

        val labels = labeledItems.mapNotNull { it.label }.joinToString(", ")
        val historySuffix = record
            ?.let { "; saved to history as ${it.id}" }
            ?: "; history was not saved"
        return "Walkthrough shown with walkthroughId=${session.id} (steps: $labels)$historySuffix"
    }

    @Suppress("unused")
    @McpTool(name = "await_walkthrough_question")
    @McpDescription(
        "Suspends until the user types a follow-up question into the active walkthrough popup " +
            "and presses Send, then returns the question text along with the label of the step " +
            "the user was viewing. Returns 'dismissed' if the user closes the popup before asking. " +
            "Call this in a loop after show_walkthrough_items to react to questions; call " +
            "insert_walkthrough_tangents to splice the answer into the walkthrough as labeled child steps."
    )
    suspend fun awaitWalkthroughQuestion(
        @McpDescription("The walkthroughId returned by show_walkthrough_items.") walkthroughId: String
    ): String {
        val project = requireProject()
        val session = WalkthroughSessionRegistry.getInstance(project).get(walkthroughId)
            ?: mcpFail("Unknown walkthroughId: $walkthroughId")
        val question = session.awaitQuestion()
            ?: return "dismissed"
        val parent = question.parentLabel ?: "(unknown)"
        return "parentLabel=$parent\nquestion=${question.question}"
    }

    @Suppress("unused")
    @McpTool(name = "insert_walkthrough_tangents")
    @McpDescription(
        "Inserts one or more answer steps as children of an existing walkthrough step. " +
            "New child labels are derived automatically by appending '.N' to the parent label: " +
            "the first tangent under '3' becomes '3.1', the next '3.2', and so on. The popup " +
            "auto-navigates to the first inserted step. Clears the inline loading spinner so " +
            "the user can ask another question."
    )
    suspend fun insertWalkthroughTangents(
        @McpDescription("The walkthroughId returned by show_walkthrough_items.") walkthroughId: String,
        @McpDescription(
            "Label of the parent step the user asked the question under (e.g. '3' or '3.1'). " +
            "Must match the parentLabel reported by await_walkthrough_question."
        ) parentLabel: String,
        @McpDescription(
            "JSON array of walkthrough items to insert as children, same shape as show_walkthrough_items: " +
            "[{\"text\":\"…\",\"file\":\"src/Foo.kt\",\"line\":10}, …]. " +
            "Verify line numbers against the actual file before calling."
        ) items: String
    ): String {
        val project = requireProject()
        val trimmedParent = parentLabel.trim()
        if (trimmedParent.isBlank()) mcpFail("parentLabel must not be blank")
        val session = WalkthroughSessionRegistry.getInstance(project).get(walkthroughId)
            ?: mcpFail("Unknown walkthroughId: $walkthroughId")
        val parsedItems = parseItems(items)
        if (parsedItems.isEmpty()) mcpFail("items must not be empty")

        val inserted = withContext(Dispatchers.EDT) {
            session.insertTangents(trimmedParent, parsedItems)
        }
        val labels = inserted.mapNotNull { it.label }.joinToString(", ")
        return "Inserted ${inserted.size} tangent step(s) under $trimmedParent: $labels"
    }

    private suspend fun requireProject(): Project =
        currentCoroutineContext().projectOrNull ?: mcpFail("No active project")

    private fun parseItems(items: String): List<WalkthroughItem> = try {
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
        mcpFail("Invalid items JSON: ${exception.message}")
    } catch (exception: IllegalStateException) {
        mcpFail("Invalid items JSON: ${exception.message}")
    }
}
