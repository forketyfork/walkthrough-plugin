@file:OptIn(ExperimentalJewelApi::class)
@file:Suppress("MatchingDeclarationName")

package com.forketyfork.walkthrough

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal data class WalkthroughPopupColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
    val accentColor: Color,
    val fieldBackgroundColor: Color,
    val fieldBorderColor: Color,
    val scrollbarUnhoverColor: Color,
    val scrollbarHoverColor: Color,
    val metaTextColor: Color,
    val markdownCodeTextColor: Color,
    val markdownInlineCodeBackground: Color,
    val markdownLinkColor: Color,
    val markdownBlockBackground: Color,
    val markdownDividerColor: Color,
)

@Composable
internal fun WalkthroughPalette.popupColors(): WalkthroughPopupColors {
    if (!isThemeBased) {
        return WalkthroughPopupColors(
            backgroundColor = paletteBackgroundColor(),
            borderColor = borderGradientColors.first(),
            contentColor = WalkthroughColors.textPrimary,
            disabledContentColor = Color.White.copy(alpha = 0.45f),
            accentColor = navPrimaryBorderColor,
            fieldBackgroundColor = Color.White.copy(alpha = 0.12f),
            fieldBorderColor = Color.White.copy(alpha = 0.2f),
            scrollbarUnhoverColor = scrollbarUnhoverColor,
            scrollbarHoverColor = scrollbarHoverColor,
            metaTextColor = metaTextColor,
            markdownCodeTextColor = WalkthroughColors.textCode,
            markdownInlineCodeBackground = WalkthroughColors.pink.copy(alpha = 0.2f),
            markdownLinkColor = WalkthroughColors.blue,
            markdownBlockBackground = WalkthroughColors.blockBackground,
            markdownDividerColor = WalkthroughColors.divider,
        )
    }

    val globalColors = JewelTheme.globalColors
    val contentColor = JewelTheme.contentColor
    val borderColor = globalColors.borders.normal
    val accentColor = globalColors.borders.focused
    val panelBackground = globalColors.panelBackground
    val fieldBackground = globalColors.toolwindowBackground
    val disabledContentColor = globalColors.text.disabled
    val infoColor = globalColors.text.info

    return WalkthroughPopupColors(
        backgroundColor = panelBackground,
        borderColor = borderColor,
        contentColor = contentColor,
        disabledContentColor = disabledContentColor,
        accentColor = accentColor,
        fieldBackgroundColor = fieldBackground,
        fieldBorderColor = borderColor,
        scrollbarUnhoverColor = borderColor,
        scrollbarHoverColor = accentColor,
        metaTextColor = globalColors.text.normal,
        markdownCodeTextColor = contentColor,
        markdownInlineCodeBackground = infoColor.copy(alpha = 0.16f),
        markdownLinkColor = infoColor,
        markdownBlockBackground = fieldBackground,
        markdownDividerColor = borderColor,
    )
}

internal fun WalkthroughPalette.popupCornerRadius() = if (isThemeBased) 8.dp else 24.dp

internal fun WalkthroughPalette.headerCornerRadius() = if (isThemeBased) 6.dp else 999.dp

internal fun WalkthroughPalette.bodyCornerRadius() = if (isThemeBased) 6.dp else 18.dp

internal fun WalkthroughPalette.questionFieldCornerRadius() = if (isThemeBased) 6.dp else 18.dp

private fun WalkthroughPalette.paletteBackgroundColor(): Color = backgroundGradientColors.first()
