package com.forketyfork.walkthrough

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WalkthroughSessionRegistryTest {
    private fun newSession(items: List<WalkthroughItem>) =
        WalkthroughSession(
            id = "test-session",
            initialItems = items,
            acceptsQuestions = true
        )

    @Test
    fun assignsAutoLabelsFromOrdinalPosition() {
        val labeled = assignTopLevelLabels(
            listOf(
                WalkthroughItem(text = "a"),
                WalkthroughItem(text = "b"),
                WalkthroughItem(text = "c")
            )
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
                    WalkthroughItem(text = "three")
                )
            )
        )

        val inserted = session.insertTangents("2", listOf(WalkthroughItem(text = "answer A")))

        assertEquals(listOf("2.1"), inserted.map { it.label })
        assertEquals("2", inserted.single().parentLabel)
        assertEquals(
            listOf("1", "2", "2.1", "3"),
            session.snapshotItems().map { it.label }
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
                    WalkthroughItem(text = "three")
                )
            )
        )
        session.insertTangents("2", listOf(WalkthroughItem(text = "answer A")))
        session.insertTangents("2.1", listOf(WalkthroughItem(text = "deeper")))

        // Now ask under "2" again — should land after the entire "2.*" subtree.
        val second = session.insertTangents("2", listOf(WalkthroughItem(text = "answer B")))

        assertEquals(listOf("2.2"), second.map { it.label })
        assertEquals(
            listOf("1", "2", "2.1", "2.1.1", "2.2", "3"),
            session.snapshotItems().map { it.label }
        )
    }

    @Test
    fun nestedTangentsUseHierarchicalLabels() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "root")))
        )
        session.insertTangents("1", listOf(WalkthroughItem(text = "child")))
        val grandchild = session.insertTangents("1.1", listOf(WalkthroughItem(text = "grandchild")))

        assertEquals("1.1.1", grandchild.single().label)
        assertEquals("1.1", grandchild.single().parentLabel)
        assertEquals(
            listOf("1", "1.1", "1.1.1"),
            session.snapshotItems().map { it.label }
        )
    }

    @Test
    fun insertTangentsRejectsUnknownParent() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only")))
        )
        assertThrows(IllegalArgumentException::class.java) {
            session.insertTangents("9", listOf(WalkthroughItem(text = "x")))
        }
    }

    @Test
    fun insertTangentsClearsLoadingAfterValidationFailure() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only")))
        )
        session.loadingState.value = true

        assertThrows(IllegalArgumentException::class.java) {
            session.insertTangents("9", listOf(WalkthroughItem(text = "x")))
        }

        assertEquals(false, session.loadingState.value)
    }

    @Test
    fun awaitQuestionReturnsNullAfterDismiss() = runBlocking {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only")))
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
                    WalkthroughItem(text = "two")
                )
            )
        )
        session.currentIndexState.intValue = 1
        session.submitQuestion("what about two?")

        val question = session.awaitQuestion()
        assertNotNull(question)
        assertEquals("2", question?.parentLabel)
        assertEquals("what about two?", question?.question)
    }

    @Test
    fun submitQuestionClearsLoadingWhenChannelIsClosed() {
        val session = newSession(
            assignTopLevelLabels(listOf(WalkthroughItem(text = "only")))
        )
        session.dismiss()

        session.submitQuestion("what now?")

        assertEquals(false, session.loadingState.value)
    }

    @Test
    fun removeKeepsDismissedSessionQueryableUntilConsumed() {
        val registry = WalkthroughSessionRegistry()
        val session = registry.create(
            items = assignTopLevelLabels(listOf(WalkthroughItem(text = "only"))),
            acceptsQuestions = true
        )

        registry.remove(session.id)

        assertNull(registry.get(session.id))
        assertEquals(true, registry.consumeDismissed(session.id))
        assertEquals(false, registry.consumeDismissed(session.id))
    }
}
