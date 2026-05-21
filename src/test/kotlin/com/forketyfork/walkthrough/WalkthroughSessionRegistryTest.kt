package com.forketyfork.walkthrough

import com.intellij.openapi.Disposable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughSessionRegistryTest {
    private fun newSession(items: List<WalkthroughItem>) = WalkthroughSession(
        id = "test-session",
        initialItems = items,
        targetKind = WalkthroughTargetKind.File,
        diffDescriptors = emptyList(),
        acceptsQuestions = true,
    )

    @Test
    fun assignsAutoLabelsFromOrdinalPosition() {
        val labeled = assignTopLevelLabels(
            listOf(
                WalkthroughItem(text = "a"),
                WalkthroughItem(text = "b"),
                WalkthroughItem(text = "c"),
            ),
        )
        assertEquals(listOf("1", "2", "3"), labeled.map { it.label })
    }

    @Test
    fun insertsFirstTangentImmediatelyAfterParent() {
        val session = newSession(
            assignTopLevelLabels(
                listOf(
                    WalkthroughItem(text = "one"),
                    WalkthroughItem(text = "two"),
                    WalkthroughItem(text = "three"),
                ),
            ),
        )

        val inserted = session.insertTangents("2", listOf(WalkthroughItem(text = "answer A")))

        assertEquals(listOf("2.1"), inserted.map { it.label })
        assertEquals("2", inserted.single().parentLabel)
        assertEquals(
            listOf("1", "2", "2.1", "3"),
            session.snapshotItems().map { it.label },
        )
        assertEquals(2, session.currentIndexState.intValue)
    }

    @Test
    fun appendsNewTangentAfterExistingSubtree() {
        val session = newSession(
            assignTopLevelLabels(
                listOf(
                    WalkthroughItem(text = "one"),
                    WalkthroughItem(text = "two"),
                    WalkthroughItem(text = "three"),
                ),
            ),
        )
        session.insertTangents("2", listOf(WalkthroughItem(text = "answer A")))
        session.insertTangents("2.1", listOf(WalkthroughItem(text = "deeper")))

        // Now ask under "2" again — should land after the entire "2.*" subtree.
        val second = session.insertTangents("2", listOf(WalkthroughItem(text = "answer B")))

        assertEquals(listOf("2.2"), second.map { it.label })
        assertEquals(
            listOf("1", "2", "2.1", "2.1.1", "2.2", "3"),
            session.snapshotItems().map { it.label },
        )
    }

    @Test
    fun nestedTangentsUseHierarchicalLabels() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "root"))),
        )
        session.insertTangents("1", listOf(WalkthroughItem(text = "child")))
        val grandchild = session.insertTangents("1.1", listOf(WalkthroughItem(text = "grandchild")))

        assertEquals("1.1.1", grandchild.single().label)
        assertEquals("1.1", grandchild.single().parentLabel)
        assertEquals(
            listOf("1", "1.1", "1.1.1"),
            session.snapshotItems().map { it.label },
        )
    }

    @Test
    fun insertTangentsRejectsUnknownParent() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            session.insertTangents("9", listOf(WalkthroughItem(text = "x")))
        }
    }

    @Test
    fun insertTangentsPreservesInFlightQuestionAfterValidationFailure() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        session.submitQuestion("can you explain?")
        val firstResult = session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS)

        assertThrows(IllegalArgumentException::class.java) {
            session.insertTangents("9", listOf(WalkthroughItem(text = "x")))
        }

        assertTrue(firstResult is WalkthroughQuestionAwaitResult.Received)
        assertEquals(WalkthroughQuestionStatus.ProcessingQuestion, session.questionStatusState.value)
        assertEquals(true, session.loadingState.value)

        val retryResult = session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS)

        assertTrue(retryResult is WalkthroughQuestionAwaitResult.Received)
        val retryQuestion = (retryResult as WalkthroughQuestionAwaitResult.Received).question
        assertEquals("can you explain?", retryQuestion.question)
    }

    @Test
    fun awaitQuestionReturnsNullAfterDismiss() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        session.dismiss()
        assertNull(session.awaitQuestion())
    }

    @Test
    fun submitQuestionDeliversParentLabelOfCurrentItem() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(
                listOf(
                    WalkthroughItem(text = "one"),
                    WalkthroughItem(text = "two"),
                ),
            ),
        )
        session.currentIndexState.intValue = 1
        session.submitQuestion("what about two?")

        val question = session.awaitQuestion()
        assertNotNull(question)
        assertEquals("2", question?.parentLabel)
        assertEquals("what about two?", question?.question)
        assertEquals(WalkthroughQuestionStatus.ProcessingQuestion, session.questionStatusState.value)
        assertEquals(true, session.loadingState.value)
    }

    @Test
    fun submitQuestionClearsLoadingWhenChannelIsClosed() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        session.dismiss()

        session.submitQuestion("what now?")

        assertEquals(false, session.loadingState.value)
        assertEquals(WalkthroughQuestionStatus.AgentNotWaiting, session.questionStatusState.value)
    }

    @Test
    fun submitQuestionQueuesWhenAgentIsNotWaiting() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )

        session.submitQuestion("can you explain?")

        assertEquals(WalkthroughQuestionStatus.QuestionQueued, session.questionStatusState.value)
        assertEquals(false, session.loadingState.value)
    }

    @Test
    fun queuedQuestionIsDeliveredToNextAwait() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        session.submitQuestion("can you explain?")

        val result = session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS)

        assertTrue(result is WalkthroughQuestionAwaitResult.Received)
        val question = (result as WalkthroughQuestionAwaitResult.Received).question
        assertEquals("can you explain?", question.question)
        assertEquals("1", question.parentLabel)
        assertEquals(WalkthroughQuestionStatus.ProcessingQuestion, session.questionStatusState.value)
        assertEquals(true, session.loadingState.value)
    }

    @Test
    fun inFlightQuestionCanBeClaimedAgainUntilTangentsAreInserted() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        session.submitQuestion("can you explain?")

        val firstResult = session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS)
        val secondResult = session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS)

        assertTrue(firstResult is WalkthroughQuestionAwaitResult.Received)
        assertTrue(secondResult is WalkthroughQuestionAwaitResult.Received)
        val firstQuestion = (firstResult as WalkthroughQuestionAwaitResult.Received).question
        val secondQuestion = (secondResult as WalkthroughQuestionAwaitResult.Received).question
        assertEquals(firstQuestion, secondQuestion)

        session.insertTangents("1", listOf(WalkthroughItem(text = "answer")))
        val resultAfterAnswer = session.awaitQuestionResult(timeoutMillis = 1L)

        assertSame(WalkthroughQuestionAwaitResult.WaitingExpired, resultAfterAnswer)
    }

    @Test
    fun awaitQuestionResultExpiresAndMarksAgentNotWaiting() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )

        val result = session.awaitQuestionResult(timeoutMillis = 1L)

        assertSame(WalkthroughQuestionAwaitResult.WaitingExpired, result)
        assertEquals(WalkthroughQuestionStatus.AgentNotWaiting, session.questionStatusState.value)
        assertEquals(false, session.loadingState.value)
    }

    @Test
    fun newAwaitReplacesPreviousWaiter() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
        )
        val firstAwait = async { session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS) }
        waitUntilQuestionStatus(session, WalkthroughQuestionStatus.WaitingForQuestion)

        val secondAwait = async { session.awaitQuestionResult(timeoutMillis = TEST_AWAIT_TIMEOUT_MILLIS) }
        assertSame(WalkthroughQuestionAwaitResult.Replaced, firstAwait.await())
        waitUntilQuestionStatus(session, WalkthroughQuestionStatus.WaitingForQuestion)

        session.submitQuestion("fresh question")
        val result = secondAwait.await()

        assertTrue(result is WalkthroughQuestionAwaitResult.Received)
        val question = (result as WalkthroughQuestionAwaitResult.Received).question
        assertEquals("fresh question", question.question)
    }

    @Test
    fun swapActiveReturnsPreviousDisposable() {
        val registry = WalkthroughSessionRegistry()
        val first = Disposable {}
        val second = Disposable {}

        assertNull(registry.swapActive(first))
        assertSame(first, registry.swapActive(second))
        assertSame(second, registry.swapActive(Disposable {}))
    }

    @Test
    fun clearActiveOnlyClearsWhenDisposableStillMatches() {
        val registry = WalkthroughSessionRegistry()
        val first = Disposable {}
        val second = Disposable {}

        registry.swapActive(first)
        registry.swapActive(second)

        // first is stale — clearing it must not evict the current active disposable
        registry.clearActive(first)
        assertSame(second, registry.swapActive(Disposable {}))
    }

    @Test
    fun removeKeepsDismissedSessionQueryableUntilConsumed() {
        val registry = WalkthroughSessionRegistry()
        val session = registry.create(
            items = assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
            acceptsQuestions = true,
        )

        registry.remove(session.id)

        assertNull(registry.get(session.id))
        assertEquals(true, registry.consumeDismissed(session.id))
        assertEquals(false, registry.consumeDismissed(session.id))
    }

    private suspend fun waitUntilQuestionStatus(session: WalkthroughSession, status: WalkthroughQuestionStatus) {
        repeat(TEST_STATUS_WAIT_ATTEMPTS) {
            if (session.questionStatusState.value == status) return
            yield()
        }
        assertEquals(status, session.questionStatusState.value)
    }

    private companion object {
        const val TEST_AWAIT_TIMEOUT_MILLIS = 1_000L
        const val TEST_STATUS_WAIT_ATTEMPTS = 100
    }
}
