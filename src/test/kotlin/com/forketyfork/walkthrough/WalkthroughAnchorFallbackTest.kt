package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughAnchorFallbackTest {
    @Test
    fun nullLineResolvesToCurrentCaret() {
        assertTrue(isResolvableWalkthroughLine(line = null, lineCount = 20))
    }

    @Test
    fun lineInsideDocumentResolves() {
        assertTrue(isResolvableWalkthroughLine(line = 1, lineCount = 20))
        assertTrue(isResolvableWalkthroughLine(line = 20, lineCount = 20))
    }

    @Test
    fun lineOutsideDocumentIsTreatedAsStale() {
        assertFalse(isResolvableWalkthroughLine(line = 0, lineCount = 20))
        assertFalse(isResolvableWalkthroughLine(line = 21, lineCount = 20))
    }

    @Test
    fun missingFileFallsBackToCurrentCaretAnchor() {
        val item = WalkthroughItem(text = "Step", file = "missing/File.kt", line = 10)

        assertEquals(item.copy(line = null), item.withFallbackAnchor())
    }

    @Test
    fun movedFileFallsBackToCurrentCaretAnchor() {
        val item = WalkthroughItem(text = "Step", file = "old/path/File.kt", line = 42)

        assertEquals(item.copy(line = null), item.withFallbackAnchor())
    }
}
