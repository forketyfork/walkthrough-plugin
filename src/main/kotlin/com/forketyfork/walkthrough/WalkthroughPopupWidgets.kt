package com.forketyfork.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private object WalkthroughWidgetStyle {
    val navigationSpacing = 10.dp
    val badgeGradientColors = listOf(
        WalkthroughColors.purple,
        WalkthroughColors.pink,
        WalkthroughColors.blue
    )
    val badgePaddingHorizontal = 12.dp
    val badgePaddingVertical = 6.dp
    val badgeTextSize = 11.sp
    val closeButtonSize = 30.dp
    const val CLOSE_BUTTON_BACKGROUND_ALPHA = 0.12f
    val closeButtonBorderWidth = 1.dp
    const val CLOSE_BUTTON_BORDER_ALPHA = 0.18f
    val closeButtonTextSize = 13.sp
    val navPrimaryGradientColors = listOf(
        WalkthroughColors.purple,
        WalkthroughColors.pink,
        WalkthroughColors.deepPurple
    )
    val navSecondaryGradientColors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.08f)
    )
    val navPrimaryBorderColor = WalkthroughColors.lightPink
    const val NAV_TEXT_DISABLED_ALPHA = 0.45f
    val navHorizontalPadding = 14.dp
    val navVerticalPadding = 8.dp
    val navTextSize = 13.sp
}

@Composable
internal fun WalkthroughPopupNavigation(
    currentIndex: Int,
    lastIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughWidgetStyle.navigationSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentWidth()
    ) {
        AiNavButton(
            label = "Previous",
            enabled = currentIndex > 0,
            emphasized = false,
            onClick = onPrevious
        )
        AiNavButton(
            label = "Next",
            enabled = currentIndex < lastIndex,
            emphasized = true,
            onClick = onNext
        )
    }
}

@Composable
internal fun AiBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(WalkthroughWidgetStyle.badgeGradientColors))
            .padding(
                horizontal = WalkthroughWidgetStyle.badgePaddingHorizontal,
                vertical = WalkthroughWidgetStyle.badgePaddingVertical
            )
    ) {
        Text(
            text = "Walkthrough",
            color = Color.White,
            fontSize = WalkthroughWidgetStyle.badgeTextSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun AiCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(WalkthroughWidgetStyle.closeButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BACKGROUND_ALPHA))
            .border(
                WalkthroughWidgetStyle.closeButtonBorderWidth,
                Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BORDER_ALPHA),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u2715",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = WalkthroughWidgetStyle.closeButtonTextSize
        )
    }
}

@Composable
internal fun GoToSourceButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(WalkthroughWidgetStyle.closeButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BACKGROUND_ALPHA))
            .border(
                WalkthroughWidgetStyle.closeButtonBorderWidth,
                Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BORDER_ALPHA),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            key = AllIconsKeys.General.Locate,
            contentDescription = "Go to source",
            tint = Color.White
        )
    }
}

@Composable
internal fun AiNavButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val backgroundBrush = if (emphasized) {
        Brush.linearGradient(WalkthroughWidgetStyle.navPrimaryGradientColors)
    } else {
        Brush.linearGradient(WalkthroughWidgetStyle.navSecondaryGradientColors)
    }
    val borderColor = if (emphasized) {
        WalkthroughWidgetStyle.navPrimaryBorderColor
    } else {
        Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BORDER_ALPHA)
    }
    val textStyle = if (emphasized) {
        TextStyle(fontWeight = FontWeight.Bold)
    } else {
        TextStyle(fontWeight = FontWeight.Medium)
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundBrush, CircleShape)
            .border(WalkthroughWidgetStyle.closeButtonBorderWidth, borderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = WalkthroughWidgetStyle.navHorizontalPadding,
                vertical = WalkthroughWidgetStyle.navVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(
                alpha = if (enabled) 1f else WalkthroughWidgetStyle.NAV_TEXT_DISABLED_ALPHA
            ),
            fontSize = WalkthroughWidgetStyle.navTextSize,
            style = textStyle
        )
    }
}
