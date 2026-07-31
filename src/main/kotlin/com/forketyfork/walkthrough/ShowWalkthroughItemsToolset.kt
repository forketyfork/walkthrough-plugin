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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private data class WalkthroughItemJson(
    val text: String?,
    val file: String?,
    val line: Int?,
    val diffId: String?,
    val diffFile: String?,
    val diffSide: String?,
)

private data class DiffWalkthroughPayloadJson(
    val diffs: List<ToolDiffWalkthroughDescriptorJson>?,
    val items: List<WalkthroughItemJson>?,
)

private data class ToolDiffWalkthroughDescriptorJson(
    val id: String?,
    val file: String?,
    val leftFile: String?,
    val rightFile: String?,
    val leftCommit: String?,
    val rightCommit: String?,
)

private const val QUESTION_TOOL_REFRESH_TIMEOUT_MILLIS = 110_000L

private fun resolveAwaitQuestionResult(
    result: WalkthroughQuestionAwaitResult,
    wasDismissed: Boolean,
): WalkthroughQuestionAwaitResult = if (wasDismissed && result == WalkthroughQuestionAwaitResult.WaitingExpired) {
    WalkthroughQuestionAwaitResult.Dismissed
} else {
    result
}

class ShowWalkthroughItemsToolset : McpToolset {
    // Discovered and invoked via reflection by the MCP server framework
    @Suppress("unused")
    @McpTool(name = "show_walkthrough_items")
    @McpDescription(
        "Shows and stores a file walkthrough anchored to normal project files. " +
            "Use this when the user asks how code works, wants an architecture tour, asks for onboarding, " +
            "or needs an explanation of existing behavior. Do not use this for PR review, branch review, " +
            "commit review, or 'what changed' requests; use show_diff_walkthrough_items instead. " +
            "Accepts one or more walkthrough items; the user can cycle through them " +
            "with Previous and Next buttons. The walkthrough is saved to this project's history. " +
            "Top-level items are auto-labeled '1', '2', '3', etc. " +
            "The returned message includes a walkthroughId; pass it to await_walkthrough_question " +
            "to react to follow-up questions the user types into the popup. " +
            "After this tool returns, immediately call await_walkthrough_question with that walkthroughId. " +
            "Do not stop after showing the walkthrough; the waiting tool call is required for the plugin " +
            "to deliver user questions back to you.",
    )
    suspend fun showWalkthroughItems(
        @McpDescription(
            "Short human-readable description shown in the project walkthrough history. " +
                "Use a concise phrase that helps the user recognize this walkthrough later.",
        ) description: String,
        @McpDescription(
            "JSON array of walkthrough items to display, e.g. " +
                "[{\"text\":\"Note 1\",\"file\":\"src/Foo.kt\",\"line\":10},{\"text\":\"Note 2\"}]. " +
                "Each item requires 'text'; 'file' (path relative to project root) and " +
                "'line' (1-based) are optional. " +
                "The 'line' value is a line in the current full file, not a diff hunk line, " +
                "so it must be accurate. " +
                "Verify line numbers by reading the actual file before calling this tool — " +
                "do not estimate from diffs or memory.",
        ) items: String,
        @McpDescription(
            "Optional history id returned by a previous show_walkthrough_items call. When set, " +
                "the existing history record with this id is overwritten with the new description " +
                "and items instead of creating a new history entry — useful when iterating on a " +
                "walkthrough's content. Leave empty to create a new history record as usual.",
        ) historyId: String = "",
    ): String {
        val project = requireProject()
        val trimmedDescription = description.trim()
        if (trimmedDescription.isBlank()) mcpFail("description must not be blank")

        val parsedItems = parseFileItems(items)
        if (parsedItems.isEmpty()) mcpFail("items must not be empty")

        val historyService = WalkthroughHistoryService.getInstance(project)
        val trimmedHistoryId = historyId.trim()
        if (trimmedHistoryId.isNotBlank()) {
            historyService.requireOverwritableRecord(trimmedHistoryId, WalkthroughTargetKind.File)
        }

        val labeledItems = assignTopLevelLabels(parsedItems)

        val session = withContext(Dispatchers.EDT) {
            showWalkthroughSession(project, labeledItems, acceptsQuestions = true)
        } ?: mcpFail("No active editor")

        val record = historyService.saveOrOverwrite(
            historyId = trimmedHistoryId,
            description = trimmedDescription,
            targetKind = WalkthroughTargetKind.File,
            diffDescriptors = emptyList(),
            items = session.snapshotItems(),
        )
        session.historyRecordId = record?.id

        val labels = labeledItems.mapNotNull { it.label }.joinToString(", ")
        val historySuffix = record
            ?.let { "; saved to history as ${it.id}" }
            ?: "; history was not saved"
        return "Walkthrough shown with walkthroughId=${session.id} (steps: $labels)$historySuffix. " +
            "Next: immediately call await_walkthrough_question with walkthroughId=${session.id} " +
            "and keep waiting for questions until it returns dismissed."
    }

    @Suppress("unused")
    @McpTool(name = "show_diff_walkthrough_items")
    @McpDescription(
        "Shows and stores a diff walkthrough anchored to IntelliJ IDEA diff viewers. " +
            "Use this when the user asks about changes, a PR, a review, a commit, a branch comparison, " +
            "a patch, or 'what changed'. Do not use this for general code explanation unless the user " +
            "specifically wants the explanation in terms of a change. All items in one call must target " +
            "Git commit-backed file diffs; do not mix file walkthrough items and diff walkthrough items. " +
            "Do not submit file contents. Submit commit hashes for the two file revisions to compare. " +
            "After this tool returns, immediately call await_walkthrough_question with the returned walkthroughId.",
    )
    suspend fun showDiffWalkthroughItems(
        @McpDescription(
            "Short human-readable description shown in the project walkthrough history. " +
                "Use a concise phrase that helps the user recognize this walkthrough later.",
        ) description: String,
        @McpDescription(
            "JSON object with 'diffs' and 'items'. 'diffs' supplies Git revisions to compare: " +
                "'id', 'file', 'leftCommit', and 'rightCommit'; for renames, use 'leftFile' and 'rightFile' " +
                "instead of 'file'. 'items' is an array with 'text', 'diffId', 'diffFile', " +
                "'diffSide', and 'line'. " +
                "'diffSide' is 'left' or 'right'. 'line' is 1-based in that side's full file text " +
                "at that commit, not the patch hunk line. " +
                "Use 'right' for added or modified new code and 'left' for removed old code. " +
                "For PRs, pass the merge-base commit as 'leftCommit' and the PR head commit as 'rightCommit'. " +
                "Verify every line by inspecting that exact file at that exact commit before calling.",
        ) payload: String,
        @McpDescription(
            "Optional history id returned by a previous show_diff_walkthrough_items call. When set, " +
                "the existing history record with this id is overwritten with the new description, " +
                "diffs, and items instead of creating a new history entry — useful when iterating on " +
                "a walkthrough's content. Leave empty to create a new history record as usual.",
        ) historyId: String = "",
    ): String {
        val project = requireProject()
        val trimmedDescription = description.trim()
        if (trimmedDescription.isBlank()) mcpFail("description must not be blank")

        val parsedPayload = parseDiffPayload(payload)

        val historyService = WalkthroughHistoryService.getInstance(project)
        val trimmedHistoryId = historyId.trim()
        if (trimmedHistoryId.isNotBlank()) {
            historyService.requireOverwritableRecord(trimmedHistoryId, WalkthroughTargetKind.Diff)
        }

        val labeledItems = assignTopLevelLabels(parsedPayload.items)

        val session = withContext(Dispatchers.EDT) {
            showDiffWalkthroughSession(
                project = project,
                descriptors = parsedPayload.descriptors,
                items = labeledItems,
                acceptsQuestions = true,
            )
        } ?: mcpFail("No diff walkthrough items to show")

        val record = historyService.saveOrOverwrite(
            historyId = trimmedHistoryId,
            description = trimmedDescription,
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = parsedPayload.descriptors,
            items = session.snapshotItems(),
        )
        session.historyRecordId = record?.id

        val labels = labeledItems.mapNotNull { it.label }.joinToString(", ")
        val historySuffix = record
            ?.let { "; saved to history as ${it.id}" }
            ?: "; history was not saved"
        return "Diff walkthrough shown with walkthroughId=${session.id} (steps: $labels)$historySuffix. " +
            "Next: immediately call await_walkthrough_question with walkthroughId=${session.id} " +
            "and keep waiting for questions until it returns dismissed."
    }

    @Suppress("unused")
    @McpTool(name = "await_walkthrough_question")
    @McpDescription(
        "Suspends until the user types a follow-up question into the active walkthrough popup " +
            "and presses Send, then returns the question text along with the label of the step " +
            "the user was viewing. Returns 'dismissed' if the user closes the popup before asking. " +
            "Returns 'waiting-expired' before Codex's tool timeout if no question arrives; " +
            "when that happens, immediately call this tool again with the same walkthroughId. " +
            "Call this immediately after show_walkthrough_items or show_diff_walkthrough_items returns, " +
            "and call it again after each insert_walkthrough_tangents response. " +
            "Keep waiting in this loop until this tool returns dismissed. " +
            "Call insert_walkthrough_tangents to splice each answer into the walkthrough as labeled child steps.",
    )
    suspend fun awaitWalkthroughQuestion(
        @McpDescription("The walkthroughId returned by show_walkthrough_items.") walkthroughId: String,
    ): String {
        val project = requireProject()
        val registry = WalkthroughSessionRegistry.getInstance(project)
        val session = registry.get(walkthroughId)
        val result = try {
            when {
                session != null -> resolveAwaitQuestionResult(
                    result = session.awaitQuestionResult(QUESTION_TOOL_REFRESH_TIMEOUT_MILLIS),
                    wasDismissed = registry.consumeDismissed(walkthroughId),
                )

                registry.consumeDismissed(walkthroughId) -> WalkthroughQuestionAwaitResult.Dismissed

                else -> mcpFail("Unknown walkthroughId: $walkthroughId")
            }
        } catch (@Suppress("SwallowedException") cancellation: CancellationException) {
            // The MCP client cancelled this tool call (e.g. its own request timeout or disconnect).
            // The waiter has already been cleaned up by `awaitQuestionResult`'s `finally` block.
            // Translate the cancellation into a clean, non-error response so it does not surface
            // as a JobCancellationException in the IDE log. Treat it like a refresh timeout so the
            // agent immediately re-issues the call on its next turn.
            WalkthroughQuestionAwaitResult.WaitingExpired
        }
        return when (result) {
            is WalkthroughQuestionAwaitResult.Received -> {
                val parent = result.question.parentLabel ?: "(unknown)"
                "parentLabel=$parent\nquestion=${result.question.question}"
            }

            WalkthroughQuestionAwaitResult.Dismissed -> "dismissed"

            WalkthroughQuestionAwaitResult.WaitingExpired ->
                "waiting-expired\nNo question arrived before the refresh timeout. " +
                    "Call await_walkthrough_question again immediately with walkthroughId=$walkthroughId."

            WalkthroughQuestionAwaitResult.Replaced ->
                "waiting-replaced\nA newer await_walkthrough_question call is already listening."
        }
    }

    @Suppress("unused")
    @McpTool(name = "insert_walkthrough_tangents")
    @McpDescription(
        "Inserts one or more answer steps as children of an existing walkthrough step. " +
            "New child labels are derived automatically by appending '.N' to the parent label: " +
            "the first tangent under '3' becomes '3.1', the next '3.2', and so on. The popup " +
            "auto-navigates to the first inserted step. Clears the inline loading spinner so " +
            "the user can ask another question. After this tool returns, immediately call " +
            "await_walkthrough_question again with the same walkthroughId.",
    )
    suspend fun insertWalkthroughTangents(
        @McpDescription("The walkthroughId returned by show_walkthrough_items.") walkthroughId: String,
        @McpDescription(
            "Label of the parent step the user asked the question under (e.g. '3' or '3.1'). " +
                "Must match the parentLabel reported by await_walkthrough_question.",
        ) parentLabel: String,
        @McpDescription(
            "JSON array of walkthrough items to insert as children. For file walkthroughs, use the same item " +
                "shape as show_walkthrough_items. For diff walkthroughs, use diff item fields from " +
                "show_diff_walkthrough_items: 'text', 'diffId', 'diffFile', 'diffSide', and 'line'. " +
                "Verify line numbers against the actual file or exact diff side before calling.",
        ) items: String,
    ): String {
        val project = requireProject()
        val trimmedParent = parentLabel.trim()
        if (trimmedParent.isBlank()) mcpFail("parentLabel must not be blank")
        val session = WalkthroughSessionRegistry.getInstance(project).get(walkthroughId)
            ?: mcpFail("Unknown walkthroughId: $walkthroughId")
        val parsedItems = when (session.targetKind) {
            WalkthroughTargetKind.File -> parseFileItems(items)
            WalkthroughTargetKind.Diff -> parseDiffItems(items, session.diffDescriptors)
        }
        if (parsedItems.isEmpty()) mcpFail("items must not be empty")

        val inserted = withContext(Dispatchers.EDT) {
            session.insertTangents(trimmedParent, parsedItems)
        }
        session.historyRecordId?.let { recordId ->
            WalkthroughHistoryService.getInstance(project).updateItems(recordId, session.snapshotItems())
        }
        val labels = inserted.mapNotNull { it.label }.joinToString(", ")
        return "Inserted ${inserted.size} tangent step(s) under $trimmedParent: $labels"
    }

    private suspend fun requireProject(): Project =
        currentCoroutineContext().projectOrNull ?: mcpFail("No active project")

    private fun parseFileItems(items: String): List<WalkthroughItem> = try {
        val type = object : TypeToken<List<WalkthroughItemJson>>() {}.type
        // Gson returns null for the JSON literal `null` or blank input; treat it as an empty
        // list so the caller's emptiness check reports it cleanly instead of throwing an NPE.
        val parsed: List<WalkthroughItemJson> = Gson().fromJson<List<WalkthroughItemJson>?>(items, type).orEmpty()
        parsed.map { entry ->
            WalkthroughItem(
                text = entry.text ?: mcpFail("Each item must have a 'text' field"),
                file = entry.file,
                line = entry.line,
            )
        }
    } catch (exception: JsonParseException) {
        mcpFail("Invalid items JSON: ${exception.message}")
    } catch (exception: IllegalStateException) {
        mcpFail("Invalid items JSON: ${exception.message}")
    }

    private data class ParsedDiffPayload(
        val descriptors: List<DiffWalkthroughDescriptor>,
        val items: List<WalkthroughItem>,
    )

    private fun parseDiffPayload(payload: String): ParsedDiffPayload = try {
        val parsed = Gson().fromJson(payload, DiffWalkthroughPayloadJson::class.java)
            ?: mcpFail("payload must be a JSON object")
        val descriptors = parseDiffDescriptors(parsed.diffs.orEmpty())
        if (descriptors.isEmpty()) mcpFail("diffs must not be empty")
        val items = parseDiffItems(parsed.items.orEmpty(), descriptors)
        if (items.isEmpty()) mcpFail("items must not be empty")
        ParsedDiffPayload(descriptors = descriptors, items = items)
    } catch (exception: JsonParseException) {
        mcpFail("Invalid payload JSON: ${exception.message}")
    } catch (exception: IllegalStateException) {
        mcpFail("Invalid payload JSON: ${exception.message}")
    }

    private fun parseDiffDescriptors(
        entries: List<ToolDiffWalkthroughDescriptorJson>,
    ): List<DiffWalkthroughDescriptor> {
        val descriptors = entries.map { entry ->
            val file = entry.file?.trim()?.takeIf { it.isNotBlank() }
            val leftFile = entry.leftFile?.trim()?.takeIf { it.isNotBlank() }
            val rightFile = entry.rightFile?.trim()?.takeIf { it.isNotBlank() }
            if (file == null && (leftFile == null || rightFile == null)) {
                mcpFail("Each diff requires either 'file' or both 'leftFile' and 'rightFile'")
            }
            DiffWalkthroughDescriptor(
                id = entry.id?.trim()?.takeIf { it.isNotBlank() }
                    ?: mcpFail("Each diff must have an 'id' field"),
                file = file,
                leftFile = leftFile,
                rightFile = rightFile,
                leftCommit = entry.leftCommit?.trim()?.takeIf { it.isNotBlank() }
                    ?: mcpFail("Each diff must have a 'leftCommit' field"),
                rightCommit = entry.rightCommit?.trim()?.takeIf { it.isNotBlank() }
                    ?: mcpFail("Each diff must have a 'rightCommit' field"),
            )
        }
        val duplicateId = descriptors.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }?.key
        if (duplicateId != null) mcpFail("Duplicate diff id: $duplicateId")
        return descriptors
    }

    private fun parseDiffItems(items: String, descriptors: List<DiffWalkthroughDescriptor>): List<WalkthroughItem> =
        try {
            val type = object : TypeToken<List<WalkthroughItemJson>>() {}.type
            // Gson returns null for the JSON literal `null` or blank input; treat it as an empty
            // list so the caller's emptiness check reports it cleanly instead of throwing an NPE.
            val parsed: List<WalkthroughItemJson> = Gson().fromJson<List<WalkthroughItemJson>?>(items, type).orEmpty()
            parseDiffItems(parsed, descriptors)
        } catch (exception: JsonParseException) {
            mcpFail("Invalid items JSON: ${exception.message}")
        } catch (exception: IllegalStateException) {
            mcpFail("Invalid items JSON: ${exception.message}")
        }

    private fun parseDiffItems(
        entries: List<WalkthroughItemJson>,
        descriptors: List<DiffWalkthroughDescriptor>,
    ): List<WalkthroughItem> = entries.map { entry ->
        val diffId = entry.diffId?.trim()?.takeIf { it.isNotBlank() }
            ?: mcpFail("Each diff item must have a 'diffId' field")
        val descriptor = descriptors.firstOrNull { it.id == diffId }
            ?: mcpFail("Unknown diffId: $diffId")
        WalkthroughItem(
            text = entry.text ?: mcpFail("Each item must have a 'text' field"),
            line = entry.line ?: mcpFail("Each diff item must have a 'line' field"),
            diffId = diffId,
            diffFile = entry.diffFile?.trim()?.takeIf { it.isNotBlank() }
                ?: descriptor.file
                ?: descriptor.rightFile
                ?: descriptor.leftFile,
            diffSide = parseDiffSide(entry.diffSide),
        )
    }

    private fun parseDiffSide(value: String?): DiffSide = when (value?.trim()?.lowercase()) {
        "left" -> DiffSide.Left
        "right" -> DiffSide.Right
        else -> mcpFail("Each diff item must have 'diffSide' set to 'left' or 'right'")
    }
}

private fun historyNotFoundMessage(historyId: String) = "No walkthrough history record found for historyId=$historyId"

private fun historyTargetKindMismatchMessage(historyId: String, existingKind: WalkthroughTargetKind) =
    "historyId=$historyId belongs to a $existingKind walkthrough; use the matching tool to update it"

private fun historyOverwriteFailureMessage(historyId: String) =
    "Failed to update walkthrough history record historyId=$historyId; check the IDE log"

private fun WalkthroughHistoryService.requireOverwritableRecord(
    historyId: String,
    expectedKind: WalkthroughTargetKind,
) {
    val record = load(historyId) ?: mcpFail(historyNotFoundMessage(historyId))
    if (record.targetKind != expectedKind) {
        mcpFail(historyTargetKindMismatchMessage(historyId, record.targetKind))
    }
}

private fun WalkthroughHistoryService.saveOrOverwrite(
    historyId: String,
    description: String,
    targetKind: WalkthroughTargetKind,
    diffDescriptors: List<DiffWalkthroughDescriptor>,
    items: List<WalkthroughItem>,
): WalkthroughRecord? {
    if (historyId.isBlank()) {
        return save(
            description = description,
            targetKind = targetKind,
            diffDescriptors = diffDescriptors,
            items = items,
        )
    }
    return when (
        val result = overwrite(
            historyId = historyId,
            description = description,
            targetKind = targetKind,
            diffDescriptors = diffDescriptors,
            items = items,
        )
    ) {
        is WalkthroughOverwriteResult.Success -> result.record

        WalkthroughOverwriteResult.NotFound -> mcpFail(historyNotFoundMessage(historyId))

        is WalkthroughOverwriteResult.TargetKindMismatch ->
            mcpFail(historyTargetKindMismatchMessage(historyId, result.existingKind))

        WalkthroughOverwriteResult.Failure -> mcpFail(historyOverwriteFailureMessage(historyId))
    }
}
