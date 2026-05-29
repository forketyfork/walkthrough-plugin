package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Point

class WalkthroughPopupInteractionTest {

    private val width = 400
    private val height = 300

    @Test
    fun dragHandleContainsPointInTopBand() {
        assertTrue(dragHandleContains(width, Point(20, 20)))
    }

    @Test
    fun dragHandleContainsPointOnLowerBoundary() {
        assertTrue(dragHandleContains(width, Point(0, 64)))
    }

    @Test
    fun dragHandleExcludesPointBelowHandle() {
        assertFalse(dragHandleContains(width, Point(20, 120)))
    }

    @Test
    fun dragHandleExcludesCloseButtonHitBox() {
        assertFalse(dragHandleContains(width, Point(width - 30, 20)))
    }

    @Test
    fun dragHandleExcludesNegativePoint() {
        assertFalse(dragHandleContains(width, Point(-5, 20)))
    }

    @Test
    fun resizeHandleContainsBottomRightCorner() {
        assertTrue(resizeHandleContains(width, height, Point(width - 5, height - 5)))
    }

    @Test
    fun resizeHandleContainsCornerBoundary() {
        assertTrue(resizeHandleContains(width, height, Point(width - 26, height - 26)))
    }

    @Test
    fun resizeHandleExcludesCenter() {
        assertFalse(resizeHandleContains(width, height, Point(width / 2, height / 2)))
    }

    @Test
    fun resizeHandleExcludesPointBeyondWidth() {
        assertFalse(resizeHandleContains(width, height, Point(width + 10, height - 5)))
    }
}
