package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class WalkthroughTangentQuestion(
    val question: String,
    val parentLabel: String?
)

class WalkthroughSession internal constructor(
    val id: String,
    initialItems: List<WalkthroughItem>,
    internal val acceptsQuestions: Boolean
) {
    internal val items: SnapshotStateList<WalkthroughItem> =
        mutableStateListOf<WalkthroughItem>().apply { addAll(initialItems) }
    internal val currentIndexState = mutableIntStateOf(0)
    internal val loadingState = mutableStateOf(false)

    private val questionChannel = Channel<WalkthroughTangentQuestion>(capacity = Channel.UNLIMITED)
    private val disposed = CompletableDeferred<Unit>()

    fun snapshotItems(): List<WalkthroughItem> = items.toList()

    internal fun submitQuestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val parentItem = items.getOrNull(currentIndexState.intValue)
        loadingState.value = true
        questionChannel.trySend(WalkthroughTangentQuestion(trimmed, parentItem?.label))
    }

    suspend fun awaitQuestion(): WalkthroughTangentQuestion? =
        runCatching { questionChannel.receive() }.getOrNull()

    fun insertTangents(parentLabel: String, newItems: List<WalkthroughItem>): List<WalkthroughItem> {
        require(newItems.isNotEmpty()) { "newItems must not be empty" }
        val parentIndex = items.indexOfFirst { it.label == parentLabel }
        require(parentIndex >= 0) { "No item with label '$parentLabel'" }

        val directChildPattern = Regex("^${Regex.escape(parentLabel)}\\.\\d+$")
        val existingDirectChildren = items.count { it.label?.matches(directChildPattern) == true }

        val parentPrefix = "$parentLabel."
        var lastSubtreeIndex = parentIndex
        items.forEachIndexed { index, item ->
            if (item.label?.startsWith(parentPrefix) == true) {
                lastSubtreeIndex = index
            }
        }

        val labeled = newItems.mapIndexed { offset, item ->
            item.copy(
                label = "$parentLabel.${existingDirectChildren + offset + 1}",
                parentLabel = parentLabel
            )
        }
        items.addAll(lastSubtreeIndex + 1, labeled)
        currentIndexState.intValue = lastSubtreeIndex + 1
        loadingState.value = false
        return labeled
    }

    internal fun dismiss() {
        if (disposed.complete(Unit)) {
            loadingState.value = false
            questionChannel.close()
        }
    }
}

class WalkthroughSessionRegistry {
    private val sessions = ConcurrentHashMap<String, WalkthroughSession>()

    internal fun create(items: List<WalkthroughItem>, acceptsQuestions: Boolean): WalkthroughSession {
        val session = WalkthroughSession(
            id = UUID.randomUUID().toString(),
            initialItems = items,
            acceptsQuestions = acceptsQuestions
        )
        sessions[session.id] = session
        return session
    }

    fun get(id: String): WalkthroughSession? = sessions[id]

    internal fun remove(id: String) {
        sessions.remove(id)?.dismiss()
    }

    companion object {
        fun getInstance(project: Project): WalkthroughSessionRegistry =
            project.getService(WalkthroughSessionRegistry::class.java)
    }
}

internal fun assignTopLevelLabels(items: List<WalkthroughItem>): List<WalkthroughItem> =
    items.mapIndexed { index, item ->
        item.copy(label = item.label ?: (index + 1).toString(), parentLabel = null)
    }
