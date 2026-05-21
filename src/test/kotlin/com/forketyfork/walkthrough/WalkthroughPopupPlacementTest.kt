package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Dimension

class WalkthroughPopupPlacementTest {

    private val minWidth = WalkthroughPopupLayout.MINIMUM_WIDTH_PX
    private val minHeight = WalkthroughPopupLayout.MINIMUM_HEIGHT_PX

    @Test
    fun clampPopupSizePreservesSizeWithinBounds() {
        val clamped = clampPopupSize(Dimension(720, 480), maxWidth = 1200, maxHeight = 900)

        assertEquals(Dimension(720, 480), clamped)
    }

    @Test
    fun clampPopupSizeRaisesBelowMinimumToMinimum() {
        val clamped = clampPopupSize(
            Dimension(minWidth - 50, minHeight - 50),
            maxWidth = 1200,
            maxHeight = 900,
        )

        assertEquals(Dimension(minWidth, minHeight), clamped)
    }

    @Test
    fun clampPopupSizeLowersAboveMaximumToMaximum() {
        val clamped = clampPopupSize(Dimension(2000, 1500), maxWidth = 1200, maxHeight = 900)

        assertEquals(Dimension(1200, 900), clamped)
    }

    @Test
    fun clampPopupSizeUsesMinimumWhenMaxIsBelowMinimum() {
        val clamped = clampPopupSize(
            Dimension(800, 500),
            maxWidth = minWidth - 100,
            maxHeight = minHeight - 100,
        )

        assertEquals(Dimension(minWidth, minHeight), clamped)
    }
}
