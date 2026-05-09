package com.forketyfork.walkthrough

import androidx.compose.ui.graphics.Color
import java.awt.Color as AwtColor

internal data class WalkthroughPalette(
    val id: String,
    val displayName: String,
    val backgroundGradientColors: List<Color>,
    val glowGradientColors: List<Color>,
    val overlayColor: Color,
    val borderGradientColors: List<Color>,
    val scrollbarUnhoverColor: Color,
    val scrollbarHoverColor: Color,
    val metaTextColor: Color,
    val badgeGradientColors: List<Color>,
    val navPrimaryGradientColors: List<Color>,
    val navPrimaryBorderColor: Color,
    val connectorStrokeColor: AwtColor,
    val connectorArrowFillColor: AwtColor
) {
    val swatchGradientColors: List<Color> = borderGradientColors
}

internal object WalkthroughPalettes {
    val PURPLE = WalkthroughPalette(
        id = "purple",
        displayName = "Purple",
        backgroundGradientColors = listOf(
            WalkthroughColors.veryDarkPurple,
            WalkthroughColors.darkPurple,
            WalkthroughColors.magenta,
            WalkthroughColors.navyBlue
        ),
        glowGradientColors = listOf(
            WalkthroughColors.lightPink.copy(alpha = 0.4f),
            WalkthroughColors.glowLavender,
            Color.Transparent
        ),
        overlayColor = WalkthroughColors.overlay,
        borderGradientColors = listOf(
            WalkthroughColors.purple,
            WalkthroughColors.pink,
            WalkthroughColors.blue,
            WalkthroughColors.purple
        ),
        scrollbarUnhoverColor = WalkthroughColors.scrollbarIndigo,
        scrollbarHoverColor = WalkthroughColors.pink,
        metaTextColor = WalkthroughColors.textMeta,
        badgeGradientColors = listOf(
            WalkthroughColors.purple,
            WalkthroughColors.pink,
            WalkthroughColors.blue
        ),
        navPrimaryGradientColors = listOf(
            WalkthroughColors.purple,
            WalkthroughColors.pink,
            WalkthroughColors.deepPurple
        ),
        navPrimaryBorderColor = WalkthroughColors.lightPink,
        connectorStrokeColor = AwtColor(255, 136, 136, 235),
        connectorArrowFillColor = AwtColor(255, 102, 102, 215)
    )

    val GREEN = palette(
        id = "green",
        displayName = "Green",
        darkest = Color(0xFF031F16),
        dark = Color(0xFF064E3B),
        mid = Color(0xFF10B981),
        accent = Color(0xFF86EFAC),
        alternate = Color(0xFF14B8A6),
        glow = Color(0xFF6EE7B7),
        meta = Color(0xFFD1FAE5),
        connectorStroke = AwtColor(88, 207, 160, 235),
        connectorArrow = AwtColor(45, 184, 128, 215)
    )

    val BLUE = palette(
        id = "blue",
        displayName = "Blue",
        darkest = Color(0xFF071B3A),
        dark = Color(0xFF1E3A8A),
        mid = Color(0xFF3B82F6),
        accent = Color(0xFF93C5FD),
        alternate = Color(0xFF06B6D4),
        glow = Color(0xFF7DD3FC),
        meta = Color(0xFFDBEAFE),
        connectorStroke = AwtColor(96, 165, 250, 235),
        connectorArrow = AwtColor(59, 130, 246, 215)
    )

    val RED = palette(
        id = "red",
        displayName = "Red",
        darkest = Color(0xFF33070A),
        dark = Color(0xFF7F1D1D),
        mid = Color(0xFFEF4444),
        accent = Color(0xFFFCA5A5),
        alternate = Color(0xFFF97316),
        glow = Color(0xFFFCA5A5),
        meta = Color(0xFFFEE2E2),
        connectorStroke = AwtColor(248, 113, 113, 235),
        connectorArrow = AwtColor(239, 68, 68, 215)
    )

    val ORANGE = palette(
        id = "orange",
        displayName = "Orange",
        darkest = Color(0xFF2B1203),
        dark = Color(0xFF7C2D12),
        mid = Color(0xFFF97316),
        accent = Color(0xFFFDBA74),
        alternate = Color(0xFFEAB308),
        glow = Color(0xFFFCD34D),
        meta = Color(0xFFFFEDD5),
        connectorStroke = AwtColor(251, 146, 60, 235),
        connectorArrow = AwtColor(249, 115, 22, 215)
    )

    val TEAL = palette(
        id = "teal",
        displayName = "Teal",
        darkest = Color(0xFF032F35),
        dark = Color(0xFF155E75),
        mid = Color(0xFF06B6D4),
        accent = Color(0xFF67E8F9),
        alternate = Color(0xFF22D3EE),
        glow = Color(0xFF67E8F9),
        meta = Color(0xFFCFFAFE),
        connectorStroke = AwtColor(34, 211, 238, 235),
        connectorArrow = AwtColor(6, 182, 212, 215)
    )

    val PINK = palette(
        id = "pink",
        displayName = "Pink",
        darkest = Color(0xFF351024),
        dark = Color(0xFF831843),
        mid = Color(0xFFEC4899),
        accent = Color(0xFFF9A8D4),
        alternate = Color(0xFFA855F7),
        glow = Color(0xFFF0ABFC),
        meta = Color(0xFFFCE7F3),
        connectorStroke = AwtColor(244, 114, 182, 235),
        connectorArrow = AwtColor(236, 72, 153, 215)
    )

    val all: List<WalkthroughPalette> = listOf(PURPLE, GREEN, BLUE, RED, ORANGE, TEAL, PINK)
    val default: WalkthroughPalette = PURPLE

    private val byId = all.associateBy(WalkthroughPalette::id)

    fun byId(id: String?): WalkthroughPalette = byId[id] ?: default

    private fun palette(
        id: String,
        displayName: String,
        darkest: Color,
        dark: Color,
        mid: Color,
        accent: Color,
        alternate: Color,
        glow: Color,
        meta: Color,
        connectorStroke: AwtColor,
        connectorArrow: AwtColor
    ): WalkthroughPalette =
        WalkthroughPalette(
            id = id,
            displayName = displayName,
            backgroundGradientColors = listOf(darkest, dark, mid, alternate),
            glowGradientColors = listOf(glow.copy(alpha = 0.4f), glow.copy(alpha = 0.33f), Color.Transparent),
            overlayColor = WalkthroughColors.overlay,
            borderGradientColors = listOf(mid, accent, alternate, mid),
            scrollbarUnhoverColor = mid.copy(alpha = 0.4f),
            scrollbarHoverColor = accent,
            metaTextColor = meta,
            badgeGradientColors = listOf(mid, accent, alternate),
            navPrimaryGradientColors = listOf(mid, accent, dark),
            navPrimaryBorderColor = accent,
            connectorStrokeColor = connectorStroke,
            connectorArrowFillColor = connectorArrow
        )
}
