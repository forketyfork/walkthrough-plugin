package com.forketyfork.walkthrough

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

internal class WalkthroughTangentReviewController(
    private val project: Project,
    private val session: WalkthroughSession,
    private val performClose: () -> Unit,
) {
    val reviewModeState: MutableState<Boolean> = mutableStateOf(false)

    fun requestClose() {
        if (!reviewModeState.value && hasPendingTangentReview()) {
            reviewModeState.value = true
            return
        }
        performClose()
    }

    fun confirmReview(discardedGroupIds: Set<String>) {
        persistIfNeeded(session.applyTangentReviewDecision(discardedGroupIds))
        performClose()
    }

    fun persistPendingTangentsOnDispose() {
        persistIfNeeded(session.keepAllPendingTangents())
    }

    private fun persistIfNeeded(shouldPersist: Boolean) {
        if (!shouldPersist) return
        val recordId = session.historyRecordId ?: return
        // Snapshot on the calling thread (typically the EDT), then move the load+write file IO
        // off it so closing or reviewing a walkthrough never blocks the UI thread.
        val itemsSnapshot = session.snapshotItems()
        ApplicationManager.getApplication().executeOnPooledThread {
            WalkthroughHistoryService.getInstance(project).updateItems(recordId, itemsSnapshot)
        }
    }

    private fun hasPendingTangentReview(): Boolean =
        session.acceptsQuestions && session.historyRecordId != null && session.pendingTangentGroups.isNotEmpty()
}

/**
 * Creates the [WalkthroughTangentReviewController] for a walkthrough session and wires its
 * disposal-time fallback (keep all pending tangents, then remove the session from [registry]).
 * Shared by [showWalkthroughSession] and [showDiffWalkthroughSession], which otherwise duplicate
 * this wiring identically.
 */
internal fun attachTangentReviewController(
    project: Project,
    session: WalkthroughSession,
    sessionDisposable: Disposable,
    registry: WalkthroughSessionRegistry,
    performClose: () -> Unit,
): WalkthroughTangentReviewController {
    val controller = WalkthroughTangentReviewController(project, session, performClose)
    Disposer.register(sessionDisposable) {
        controller.persistPendingTangentsOnDispose()
        registry.remove(session.id)
        registry.clearActive(sessionDisposable)
    }
    return controller
}
