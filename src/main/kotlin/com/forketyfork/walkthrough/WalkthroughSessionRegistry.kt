package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

data class WalkthroughTangentQuestion(
    val question: String,
    val parentLabel: String?
)

internal enum class WalkthroughQuestionStatus {
    AgentNotWaiting,
    WaitingForQuestion,
    QuestionQueued,
    ProcessingQuestion
}

internal sealed interface WalkthroughQuestionAwaitResult {
    data class Received(val question: WalkthroughTangentQuestion) : WalkthroughQuestionAwaitResult
    data object Dismissed : WalkthroughQuestionAwaitResult
    data object WaitingExpired : WalkthroughQuestionAwaitResult
    data object Replaced : WalkthroughQuestionAwaitResult
}

private class WalkthroughQuestionWaiter {
    val result = CompletableDeferred<WalkthroughQuestionAwaitResult>()
}

private data class WalkthroughQuestionWaitRegistration(
    val waiter: WalkthroughQuestionWaiter?,
    val previousWaiter: WalkthroughQuestionWaiter?,
    val immediateResult: WalkthroughQuestionAwaitResult?
)

class WalkthroughSession internal constructor(
    val id: String,
    initialItems: List<WalkthroughItem>,
    val targetKind: WalkthroughTargetKind,
    val diffDescriptors: List<DiffWalkthroughDescriptor>,
    internal val acceptsQuestions: Boolean
) {
    internal val items: SnapshotStateList<WalkthroughItem> =
        mutableStateListOf<WalkthroughItem>().apply { addAll(initialItems) }
    internal val currentIndexState = mutableIntStateOf(0)
    internal val questionStatusState = mutableStateOf(WalkthroughQuestionStatus.AgentNotWaiting)
    internal val loadingState = mutableStateOf(false)

    private val questionLock = Any()
    private var activeQuestionWaiter: WalkthroughQuestionWaiter? = null
    private var pendingQuestion: WalkthroughTangentQuestion? = null
    private var inFlightQuestion: WalkthroughTangentQuestion? = null
    private val disposed = CompletableDeferred<Unit>()

    fun snapshotItems(): List<WalkthroughItem> = items.toList()

    internal fun submitQuestion(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val parentItem = items.getOrNull(currentIndexState.intValue)
        val question = WalkthroughTangentQuestion(trimmed, parentItem?.label)
        val waiter = synchronized(questionLock) {
            when {
                disposed.isCompleted -> null
                pendingQuestion != null -> null
                questionStatusState.value == WalkthroughQuestionStatus.ProcessingQuestion -> null
                activeQuestionWaiter != null -> {
                    activeQuestionWaiter.also {
                        activeQuestionWaiter = null
                        inFlightQuestion = question
                        setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    }
                }
                else -> {
                    pendingQuestion = question
                    setQuestionStatus(WalkthroughQuestionStatus.QuestionQueued)
                    null
                }
            }
        }
        waiter?.result?.complete(WalkthroughQuestionAwaitResult.Received(question))
    }

    suspend fun awaitQuestion(): WalkthroughTangentQuestion? =
        when (val result = awaitQuestionResult(timeoutMillis = null)) {
            is WalkthroughQuestionAwaitResult.Received -> result.question
            else -> null
        }

    internal suspend fun awaitQuestionResult(timeoutMillis: Long?): WalkthroughQuestionAwaitResult = coroutineScope {
        val registration = registerQuestionWaiter()
        registration.previousWaiter?.result?.complete(WalkthroughQuestionAwaitResult.Replaced)
        registration.immediateResult?.let { return@coroutineScope it }

        val waiter = registration.waiter ?: return@coroutineScope WalkthroughQuestionAwaitResult.Dismissed
        val timeoutJob = timeoutMillis?.let { timeout ->
            launch {
                delay(timeout)
                expireQuestionWaiter(waiter)
            }
        }
        try {
            waiter.result.await()
        } finally {
            timeoutJob?.cancel()
            clearQuestionWaiter(waiter)
        }
    }

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
        synchronized(questionLock) {
            inFlightQuestion = null
            setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
        }
        return labeled
    }

    internal fun dismiss() {
        if (disposed.complete(Unit)) {
            val waiter = synchronized(questionLock) {
                activeQuestionWaiter.also {
                    activeQuestionWaiter = null
                    pendingQuestion = null
                    inFlightQuestion = null
                    setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
                }
            }
            waiter?.result?.complete(WalkthroughQuestionAwaitResult.Dismissed)
        }
    }

    private fun registerQuestionWaiter(): WalkthroughQuestionWaitRegistration {
        val waiter = WalkthroughQuestionWaiter()
        return synchronized(questionLock) {
            when {
                disposed.isCompleted -> WalkthroughQuestionWaitRegistration(
                    waiter = null,
                    previousWaiter = null,
                    immediateResult = WalkthroughQuestionAwaitResult.Dismissed
                )
                inFlightQuestion != null -> {
                    setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = null,
                        previousWaiter = null,
                        immediateResult = WalkthroughQuestionAwaitResult.Received(requireNotNull(inFlightQuestion))
                    )
                }
                pendingQuestion != null -> {
                    val question = pendingQuestion
                    pendingQuestion = null
                    inFlightQuestion = question
                    setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = null,
                        previousWaiter = null,
                        immediateResult = WalkthroughQuestionAwaitResult.Received(requireNotNull(question))
                    )
                }
                else -> {
                    val previousWaiter = activeQuestionWaiter
                    activeQuestionWaiter = waiter
                    setQuestionStatus(WalkthroughQuestionStatus.WaitingForQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = waiter,
                        previousWaiter = previousWaiter,
                        immediateResult = null
                    )
                }
            }
        }
    }

    private fun expireQuestionWaiter(waiter: WalkthroughQuestionWaiter) {
        val expired = synchronized(questionLock) {
            if (activeQuestionWaiter === waiter) {
                activeQuestionWaiter = null
                setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
                true
            } else {
                false
            }
        }
        if (expired) {
            waiter.result.complete(WalkthroughQuestionAwaitResult.WaitingExpired)
        }
    }

    private fun clearQuestionWaiter(waiter: WalkthroughQuestionWaiter) {
        synchronized(questionLock) {
            if (activeQuestionWaiter === waiter) {
                activeQuestionWaiter = null
                setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
            }
        }
    }

    private fun setQuestionStatus(status: WalkthroughQuestionStatus) {
        questionStatusState.value = status
        loadingState.value = status == WalkthroughQuestionStatus.ProcessingQuestion
    }
}

class WalkthroughSessionRegistry {
    private val sessions = ConcurrentHashMap<String, WalkthroughSession>()
    private val dismissedSessionIds = ConcurrentHashMap.newKeySet<String>()
    private val dismissedSessionOrder = ConcurrentLinkedQueue<String>()
    private val activeSessionDisposable = AtomicReference<Disposable?>()

    internal fun swapActive(newDisposable: Disposable): Disposable? =
        activeSessionDisposable.getAndSet(newDisposable)

    internal fun clearActive(disposable: Disposable) {
        activeSessionDisposable.compareAndSet(disposable, null)
    }

    internal fun create(
        items: List<WalkthroughItem>,
        acceptsQuestions: Boolean,
        targetKind: WalkthroughTargetKind = WalkthroughTargetKind.File,
        diffDescriptors: List<DiffWalkthroughDescriptor> = emptyList()
    ): WalkthroughSession {
        val session = WalkthroughSession(
            id = UUID.randomUUID().toString(),
            initialItems = items,
            targetKind = targetKind,
            diffDescriptors = diffDescriptors,
            acceptsQuestions = acceptsQuestions
        )
        sessions[session.id] = session
        return session
    }

    fun get(id: String): WalkthroughSession? = sessions[id]

    internal fun consumeDismissed(id: String): Boolean = dismissedSessionIds.remove(id)

    internal fun remove(id: String) {
        val session = sessions.remove(id) ?: return
        session.dismiss()
        rememberDismissed(id)
    }

    private fun rememberDismissed(id: String) {
        if (dismissedSessionIds.add(id)) {
            dismissedSessionOrder.add(id)
        }
        while (dismissedSessionIds.size > MAX_DISMISSED_SESSIONS) {
            dismissedSessionOrder.poll()?.let(dismissedSessionIds::remove) ?: break
        }
    }

    companion object {
        private const val MAX_DISMISSED_SESSIONS = 128

        fun getInstance(project: Project): WalkthroughSessionRegistry =
            project.getService(WalkthroughSessionRegistry::class.java)
    }
}

internal fun assignTopLevelLabels(items: List<WalkthroughItem>): List<WalkthroughItem> =
    items.mapIndexed { index, item ->
        item.copy(label = item.label ?: (index + 1).toString(), parentLabel = null)
    }
