package com.forketyfork.walkthrough

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughPaletteTest {

    @Test
    fun allPresetsAreAvailable() {
        assertEquals(
            listOf("purple", "green", "blue", "red", "orange", "teal", "pink"),
            WalkthroughPalettes.all.map(WalkthroughPalette::id)
        )
    }

    @Test
    fun presetIdsAreDistinct() {
        val ids = WalkthroughPalettes.all.map(WalkthroughPalette::id)

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultPaletteIsPurple() {
        assertSame(WalkthroughPalettes.PURPLE, WalkthroughPalettes.default)
    }

    @Test
    fun lookupReturnsPresetById() {
        assertSame(WalkthroughPalettes.GREEN, WalkthroughPalettes.byId("green"))
    }

    @Test
    fun lookupFallsBackToDefaultForUnknownId() {
        assertSame(WalkthroughPalettes.PURPLE, WalkthroughPalettes.byId("unknown"))
    }

    @Test
    fun lookupFallsBackToDefaultForMissingId() {
        assertSame(WalkthroughPalettes.PURPLE, WalkthroughPalettes.byId(null))
    }

    @Test
    fun purplePalettePreservesCurrentPopupBackground() {
        assertEquals(
            listOf(
                WalkthroughColors.veryDarkPurple,
                WalkthroughColors.darkPurple,
                WalkthroughColors.magenta,
                WalkthroughColors.navyBlue
            ),
            WalkthroughPalettes.PURPLE.backgroundGradientColors
        )
    }

    @Test
    fun purplePalettePreservesCurrentPopupGlow() {
        assertEquals(
            listOf(
                WalkthroughColors.lightPink.copy(alpha = 0.4f),
                WalkthroughColors.glowLavender,
                Color.Transparent
            ),
            WalkthroughPalettes.PURPLE.glowGradientColors
        )
    }

    @Test
    fun purplePalettePreservesCurrentPopupBorder() {
        assertEquals(
            listOf(
                WalkthroughColors.purple,
                WalkthroughColors.pink,
                WalkthroughColors.blue,
                WalkthroughColors.purple
            ),
            WalkthroughPalettes.PURPLE.borderGradientColors
        )
    }

    @Test
    fun everyPresetHasSwatchColors() {
        assertTrue(WalkthroughPalettes.all.all { it.swatchGradientColors.isNotEmpty() })
    }
}
