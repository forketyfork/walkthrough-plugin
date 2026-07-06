package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.project.Project

internal class WalkthroughTangentReviewController(
    private val project: Project,
    private val session: WalkthroughSession,
    private val performClose: () -> Unit,
) {
    val reviewModeState = mutableStateOf(false)

    fun requestClose() {
        if (!reviewModeState.value && hasPendingTangentReview()) {
            reviewModeState.value = true
            return
        }
        performClose()
    }

    fun confirmReview(keptGroupIds: Set<String>) {
        persistIfNeeded(session.applyTangentReviewDecision(keptGroupIds))
        performClose()
    }

    fun persistPendingTangentsOnDispose() {
        persistIfNeeded(session.keepAllPendingTangents())
    }

    private fun persistIfNeeded(shouldPersist: Boolean) {
        if (!shouldPersist) return
        session.historyRecordId?.let { recordId ->
            WalkthroughHistoryService.getInstance(project).updateItems(recordId, session.snapshotItems())
        }
    }

    private fun hasPendingTangentReview(): Boolean =
        session.acceptsQuestions && session.historyRecordId != null && session.pendingTangentGroups.isNotEmpty()
}
