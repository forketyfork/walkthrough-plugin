package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

data class WalkthroughTangentQuestion(val question: String, val parentLabel: String?)

data class WalkthroughPendingTangentGroup(
    val id: String,
    val questionText: String,
    val parentLabel: String,
    val childLabels: List<String>,
)

internal enum class WalkthroughQuestionStatus {
    AgentNotWaiting,
    WaitingForQuestion,
    QuestionQueued,
    ProcessingQuestion,
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
    val immediateResult: WalkthroughQuestionAwaitResult?,
)

@Suppress("TooManyFunctions")
class WalkthroughSession internal constructor(
    val id: String,
    initialItems: List<WalkthroughItem>,
    val targetKind: WalkthroughTargetKind,
    val diffDescriptors: List<DiffWalkthroughDescriptor>,
    internal val acceptsQuestions: Boolean,
    internal val notListeningGracePeriodMillis: Long = DEFAULT_NOT_LISTENING_GRACE_PERIOD_MILLIS,
) {
    internal val items: SnapshotStateList<WalkthroughItem> =
        mutableStateListOf<WalkthroughItem>().apply { addAll(initialItems) }
    internal val pendingTangentGroups: SnapshotStateList<WalkthroughPendingTangentGroup> = mutableStateListOf()
    internal val currentIndexState = mutableIntStateOf(0)
    internal val questionStatusState = mutableStateOf(WalkthroughQuestionStatus.AgentNotWaiting)
    internal val loadingState = mutableStateOf(false)

    @Volatile
    internal var historyRecordId: String? = null

    private val questionLock = Any()
    private var activeQuestionWaiter: WalkthroughQuestionWaiter? = null
    private var pendingQuestion: WalkthroughTangentQuestion? = null
    private var inFlightQuestion: WalkthroughTangentQuestion? = null
    private val disposed = CompletableDeferred<Unit>()
    private val sessionScope = CoroutineScope(SupervisorJob())
    private var pendingNotListeningJob: Job? = null

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
                        cancelPendingAgentNotWaitingLocked()
                        setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    }
                }

                else -> {
                    pendingQuestion = question
                    cancelPendingAgentNotWaitingLocked()
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
                parentLabel = parentLabel,
            )
        }
        items.addAll(lastSubtreeIndex + 1, labeled)
        currentIndexState.intValue = lastSubtreeIndex + 1
        val questionText = synchronized(questionLock) {
            val text = inFlightQuestion?.question
            inFlightQuestion = null
            scheduleAgentNotWaitingLocked()
            text
        }
        pendingTangentGroups.add(
            WalkthroughPendingTangentGroup(
                id = UUID.randomUUID().toString(),
                questionText = questionText ?: parentLabel,
                parentLabel = parentLabel,
                childLabels = labeled.mapNotNull { it.label },
            ),
        )
        return labeled
    }

    /**
     * Applies a review decision for the currently pending tangent groups: groups in
     * [discardedGroupIds] have their child steps removed from [items], the rest stay. A pending
     * group whose [parentLabel][WalkthroughPendingTangentGroup.parentLabel] falls under a
     * discarded group's subtree is discarded too, even if its own id is absent from
     * [discardedGroupIds] — this cascades the removal to nested tangents (asked on a
     * still-pending tangent step) whose parent no longer exists. Any pending group unknown to the
     * caller — e.g. one inserted while the review screen was already open — defaults to kept.
     * Clears the pending groups either way.
     * Returns whether any group was kept, i.e. whether the caller must persist [items] to history.
     */
    internal fun applyTangentReviewDecision(discardedGroupIds: Set<String>): Boolean {
        val groups = pendingTangentGroups.toList()
        if (groups.isEmpty()) return false

        val resolvedDiscardedIds = mutableSetOf<String>()
        val discardedLabels = mutableSetOf<String>()

        fun isUnderDiscardedSubtree(label: String) = discardedLabels.any { label == it || label.startsWith("$it.") }

        groups.filter { it.id in discardedGroupIds }.forEach { group ->
            resolvedDiscardedIds += group.id
            discardedLabels += group.childLabels
        }

        var cascaded = true
        while (cascaded) {
            cascaded = false
            groups.forEach { group ->
                if (group.id !in resolvedDiscardedIds && isUnderDiscardedSubtree(group.parentLabel)) {
                    resolvedDiscardedIds += group.id
                    discardedLabels += group.childLabels
                    cascaded = true
                }
            }
        }

        if (discardedLabels.isNotEmpty()) {
            items.removeAll { item -> item.label != null && isUnderDiscardedSubtree(item.label) }
        }
        pendingTangentGroups.clear()
        return groups.any { it.id !in resolvedDiscardedIds }
    }

    /**
     * Non-destructive fallback for when a session ends without an explicit review decision: keeps
     * every pending tangent group. A no-op if a decision was already applied.
     */
    internal fun keepAllPendingTangents(): Boolean = applyTangentReviewDecision(discardedGroupIds = emptySet())

    internal fun dismiss() {
        if (disposed.complete(Unit)) {
            val waiter = synchronized(questionLock) {
                activeQuestionWaiter.also {
                    activeQuestionWaiter = null
                    pendingQuestion = null
                    inFlightQuestion = null
                    cancelPendingAgentNotWaitingLocked()
                    setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
                }
            }
            waiter?.result?.complete(WalkthroughQuestionAwaitResult.Dismissed)
            sessionScope.coroutineContext[Job]?.cancel()
        }
    }

    private fun registerQuestionWaiter(): WalkthroughQuestionWaitRegistration {
        val waiter = WalkthroughQuestionWaiter()
        return synchronized(questionLock) {
            when {
                disposed.isCompleted -> WalkthroughQuestionWaitRegistration(
                    waiter = null,
                    previousWaiter = null,
                    immediateResult = WalkthroughQuestionAwaitResult.Dismissed,
                )

                inFlightQuestion != null -> {
                    cancelPendingAgentNotWaitingLocked()
                    setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = null,
                        previousWaiter = null,
                        immediateResult = WalkthroughQuestionAwaitResult.Received(requireNotNull(inFlightQuestion)),
                    )
                }

                pendingQuestion != null -> {
                    val question = pendingQuestion
                    pendingQuestion = null
                    inFlightQuestion = question
                    cancelPendingAgentNotWaitingLocked()
                    setQuestionStatus(WalkthroughQuestionStatus.ProcessingQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = null,
                        previousWaiter = null,
                        immediateResult = WalkthroughQuestionAwaitResult.Received(requireNotNull(question)),
                    )
                }

                else -> {
                    val previousWaiter = activeQuestionWaiter
                    activeQuestionWaiter = waiter
                    cancelPendingAgentNotWaitingLocked()
                    setQuestionStatus(WalkthroughQuestionStatus.WaitingForQuestion)
                    WalkthroughQuestionWaitRegistration(
                        waiter = waiter,
                        previousWaiter = previousWaiter,
                        immediateResult = null,
                    )
                }
            }
        }
    }

    private fun expireQuestionWaiter(waiter: WalkthroughQuestionWaiter) {
        val expired = synchronized(questionLock) {
            if (activeQuestionWaiter === waiter) {
                activeQuestionWaiter = null
                scheduleAgentNotWaitingLocked()
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
                scheduleAgentNotWaitingLocked()
            }
        }
    }

    private fun setQuestionStatus(status: WalkthroughQuestionStatus) {
        questionStatusState.value = status
        loadingState.value = status == WalkthroughQuestionStatus.ProcessingQuestion
    }

    /**
     * Defers the transition to [WalkthroughQuestionStatus.AgentNotWaiting] by [notListeningGracePeriodMillis]
     * to avoid briefly showing the "agent is not listening" warning when the MCP client cancels and
     * immediately re-issues the await call. The spinner ([loadingState]) is cleared immediately so it
     * does not keep spinning during the grace window after the question has actually been answered.
     * Must be invoked while holding [questionLock].
     */
    private fun scheduleAgentNotWaitingLocked() {
        cancelPendingAgentNotWaitingLocked()
        if (notListeningGracePeriodMillis <= 0L) {
            setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
            return
        }
        // Stop the spinner now even though the visible status text is deferred: the question is
        // done, only the AgentNotWaiting warning flash needs the grace window.
        loadingState.value = false
        lateinit var scheduledJob: Job
        scheduledJob = sessionScope.launch {
            delay(notListeningGracePeriodMillis)
            synchronized(questionLock) { applyPendingAgentNotWaitingLocked(scheduledJob) }
        }
        pendingNotListeningJob = scheduledJob
    }

    /** Must be invoked while holding [questionLock]. */
    private fun applyPendingAgentNotWaitingLocked(scheduledJob: Job) {
        if (pendingNotListeningJob !== scheduledJob) return
        val hasListener = activeQuestionWaiter != null || inFlightQuestion != null || pendingQuestion != null
        if (!hasListener) {
            setQuestionStatus(WalkthroughQuestionStatus.AgentNotWaiting)
        }
        pendingNotListeningJob = null
    }

    /** Must be invoked while holding [questionLock]. */
    private fun cancelPendingAgentNotWaitingLocked() {
        pendingNotListeningJob?.cancel()
        pendingNotListeningJob = null
    }

    internal companion object {
        /**
         * How long to wait after the agent stops listening before flipping the popup to
         * [WalkthroughQuestionStatus.AgentNotWaiting]. MCP clients typically reconnect within
         * milliseconds; the grace period avoids a visible warning flash during normal retries.
         */
        const val DEFAULT_NOT_LISTENING_GRACE_PERIOD_MILLIS: Long = 5_000L
    }
}

class WalkthroughSessionRegistry {
    private val sessions = ConcurrentHashMap<String, WalkthroughSession>()
    private val dismissedSessionIds = ConcurrentHashMap.newKeySet<String>()
    private val dismissedSessionOrder = ConcurrentLinkedQueue<String>()
    private val activeSessionDisposable = AtomicReference<Disposable?>()

    internal fun swapActive(newDisposable: Disposable): Disposable? = activeSessionDisposable.getAndSet(newDisposable)

    internal fun clearActive(disposable: Disposable) {
        activeSessionDisposable.compareAndSet(disposable, null)
    }

    internal fun create(
        items: List<WalkthroughItem>,
        acceptsQuestions: Boolean,
        targetKind: WalkthroughTargetKind = WalkthroughTargetKind.File,
        diffDescriptors: List<DiffWalkthroughDescriptor> = emptyList(),
    ): WalkthroughSession {
        val session = WalkthroughSession(
            id = UUID.randomUUID().toString(),
            initialItems = items,
            targetKind = targetKind,
            diffDescriptors = diffDescriptors,
            acceptsQuestions = acceptsQuestions,
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
