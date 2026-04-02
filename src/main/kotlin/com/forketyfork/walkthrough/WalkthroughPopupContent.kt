package com.forketyfork.walkthrough

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.ui.component.Text

private object WalkthroughPopupContentStyle {
    val cornerRadius = 24.dp
    val textMinHeight = 180.dp
    val scrollbarMinHeight = 28.dp
    val scrollbarThickness = 6.dp
    const val SCROLLBAR_HOVER_DURATION_MS = 300
    val scrollbarUnhoverColor = Color(0x66818CF8)
    val scrollbarHoverColor = Color(0xFFE879F9)
    const val ANIMATION_START = 0f
    const val ANIMATION_END = 1f
    const val GRADIENT_ANIMATION_DURATION_MS = 5400
    const val GLOW_ANIMATION_DURATION_MS = 3200
    val backgroundGradientColors = listOf(
        Color(0xFF160326),
        Color(0xFF3D0A68),
        Color(0xFF7C1B9A),
        Color(0xFF1D4ED8)
    )
    const val BACKGROUND_START_X_SHIFT = 0.7f
    const val BACKGROUND_START_Y_SHIFT = 0.2f
    const val BACKGROUND_END_X_SHIFT = 0.5f
    const val BACKGROUND_END_Y_SHIFT = 1.1f
    val glowGradientColors = listOf(
        Color(0x66F0ABFC),
        Color(0x55C084FC),
        Color.Transparent
    )
    const val GLOW_CENTER_BASE_X = 0.18f
    const val GLOW_CENTER_SHIFT_X = 0.62f
    const val GLOW_CENTER_Y = 0.2f
    const val GLOW_RADIUS_FACTOR = 0.95f
    val overlayColor = Color(0x9A0A0114)
    val borderGradientColors = listOf(
        Color(0xFFA855F7),
        Color(0xFFE879F9),
        Color(0xFF60A5FA),
        Color(0xFFA855F7)
    )
    const val BORDER_GRADIENT_START_SHIFT = 1f
    const val BORDER_ALPHA = 0.9f
    val borderStrokeWidth = 1.5.dp
    val closeButtonPadding = 14.dp
    val contentPaddingStart = 18.dp
    val contentPaddingTop = 16.dp
    val contentPaddingEnd = 58.dp
    val contentPaddingBottom = 16.dp
    val contentSectionSpacing = 14.dp
    val headerSpacing = 10.dp
    val headerPillRadius = 999.dp
    const val HEADER_BACKGROUND_ALPHA = 0.06f
    val headerBorderWidth = 1.dp
    const val HEADER_BORDER_ALPHA = 0.08f
    val headerPaddingHorizontal = 8.dp
    val headerPaddingVertical = 6.dp
    val metaTextColor = Color(0xFFE9D5FF)
    val metaTextSize = 12.sp
    val bodyCornerRadius = 18.dp
    const val BODY_BACKGROUND_ALPHA = 0.08f
    val bodyBorderWidth = 1.dp
    const val BODY_BORDER_ALPHA = 0.12f
    val markdownPaddingStart = 16.dp
    val markdownPaddingTop = 14.dp
    val markdownPaddingEnd = 28.dp
    val markdownPaddingBottom = 14.dp
    val scrollbarPaddingEnd = 6.dp
    val scrollbarPaddingTop = 10.dp
    val scrollbarPaddingBottom = 10.dp
    val navigationSpacing = 10.dp
    val badgeGradientColors = listOf(
        Color(0xFFA855F7),
        Color(0xFFE879F9),
        Color(0xFF60A5FA)
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
        Color(0xFFA855F7),
        Color(0xFFE879F9),
        Color(0xFF7C3AED)
    )
    val navSecondaryGradientColors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.08f)
    )
    val navPrimaryBorderColor = Color(0xFFF0ABFC)
    const val NAV_TEXT_DISABLED_ALPHA = 0.45f
    val navHorizontalPadding = 14.dp
    val navVerticalPadding = 8.dp
    val navTextSize = 13.sp
}

private data class WalkthroughPopupAnimationState(
    val gradientShift: Float,
    val glowShift: Float
)

@Composable
internal fun WalkthroughItemContent(
    project: Project,
    items: List<WalkthroughItem>,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val item = items[currentIndex]
    val scrollState = rememberScrollState()
    val animationState = rememberPopupAnimationState()
    val scrollbarStyle = rememberPopupScrollbarStyle()
    val showScrollbar = scrollState.maxValue > 0

    LaunchedEffect(item) {
        scrollState.scrollTo(0)
        onItemDisplayed(item)
    }

    CompositionLocalProvider(LocalScrollbarStyle provides scrollbarStyle) {
        WalkthroughPopupFrame(
            project = project,
            item = item,
            items = items,
            currentIndex = currentIndex,
            scrollState = scrollState,
            showScrollbar = showScrollbar,
            animationState = animationState,
            onPrevious = { currentIndex-- },
            onNext = { currentIndex++ },
            onClose = onClose
        )
    }
}

@Composable
private fun rememberPopupAnimationState(): WalkthroughPopupAnimationState {
    val transition = rememberInfiniteTransition()
    val gradientShift by transition.animateFloat(
        initialValue = WalkthroughPopupContentStyle.ANIMATION_START,
        targetValue = WalkthroughPopupContentStyle.ANIMATION_END,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WalkthroughPopupContentStyle.GRADIENT_ANIMATION_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    val glowShift by transition.animateFloat(
        initialValue = WalkthroughPopupContentStyle.ANIMATION_START,
        targetValue = WalkthroughPopupContentStyle.ANIMATION_END,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WalkthroughPopupContentStyle.GLOW_ANIMATION_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    return WalkthroughPopupAnimationState(gradientShift = gradientShift, glowShift = glowShift)
}

@Composable
private fun rememberPopupScrollbarStyle(): ScrollbarStyle =
    remember {
        ScrollbarStyle(
            minimalHeight = WalkthroughPopupContentStyle.scrollbarMinHeight,
            thickness = WalkthroughPopupContentStyle.scrollbarThickness,
            shape = CircleShape,
            hoverDurationMillis = WalkthroughPopupContentStyle.SCROLLBAR_HOVER_DURATION_MS,
            unhoverColor = WalkthroughPopupContentStyle.scrollbarUnhoverColor,
            hoverColor = WalkthroughPopupContentStyle.scrollbarHoverColor
        )
    }

@Composable
private fun WalkthroughPopupFrame(
    project: Project,
    item: WalkthroughItem,
    items: List<WalkthroughItem>,
    currentIndex: Int,
    scrollState: ScrollState,
    showScrollbar: Boolean,
    animationState: WalkthroughPopupAnimationState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(WalkthroughPopupContentStyle.cornerRadius)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .walkthroughPopupBackground(animationState)
    ) {
        AiCloseButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WalkthroughPopupContentStyle.closeButtonPadding),
            onClick = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = WalkthroughPopupContentStyle.contentPaddingStart,
                    top = WalkthroughPopupContentStyle.contentPaddingTop,
                    end = WalkthroughPopupContentStyle.contentPaddingEnd,
                    bottom = WalkthroughPopupContentStyle.contentPaddingBottom
                ),
            verticalArrangement = Arrangement.spacedBy(WalkthroughPopupContentStyle.contentSectionSpacing)
        ) {
            WalkthroughPopupHeader(item = item, items = items, currentIndex = currentIndex)
            WalkthroughPopupBody(
                project = project,
                item = item,
                scrollState = scrollState,
                showScrollbar = showScrollbar
            )
            if (items.size > 1) {
                WalkthroughPopupNavigation(
                    currentIndex = currentIndex,
                    lastIndex = items.lastIndex,
                    onPrevious = onPrevious,
                    onNext = onNext
                )
            }
        }
    }
}

private fun Modifier.walkthroughPopupBackground(
    animationState: WalkthroughPopupAnimationState
): Modifier = drawWithCache {
    val cornerRadius = CornerRadius(
        WalkthroughPopupContentStyle.cornerRadius.toPx(),
        WalkthroughPopupContentStyle.cornerRadius.toPx()
    )
    val backgroundBrush = Brush.linearGradient(
        colors = WalkthroughPopupContentStyle.backgroundGradientColors,
        start = Offset(
            size.width * (animationState.gradientShift - WalkthroughPopupContentStyle.BACKGROUND_START_X_SHIFT),
            -size.height * WalkthroughPopupContentStyle.BACKGROUND_START_Y_SHIFT
        ),
        end = Offset(
            size.width * (animationState.gradientShift + WalkthroughPopupContentStyle.BACKGROUND_END_X_SHIFT),
            size.height * WalkthroughPopupContentStyle.BACKGROUND_END_Y_SHIFT
        )
    )
    val glowBrush = Brush.radialGradient(
        colors = WalkthroughPopupContentStyle.glowGradientColors,
        center = Offset(
            size.width * (
                WalkthroughPopupContentStyle.GLOW_CENTER_BASE_X +
                    animationState.glowShift * WalkthroughPopupContentStyle.GLOW_CENTER_SHIFT_X
                ),
            size.height * WalkthroughPopupContentStyle.GLOW_CENTER_Y
        ),
        radius = size.minDimension * WalkthroughPopupContentStyle.GLOW_RADIUS_FACTOR
    )
    val borderBrush = Brush.linearGradient(
        colors = WalkthroughPopupContentStyle.borderGradientColors,
        start = Offset(
            size.width * (animationState.gradientShift - WalkthroughPopupContentStyle.BORDER_GRADIENT_START_SHIFT),
            0f
        ),
        end = Offset(size.width * animationState.gradientShift, size.height)
    )

    onDrawBehind {
        drawRoundRect(brush = backgroundBrush, cornerRadius = cornerRadius)
        drawRoundRect(brush = glowBrush, cornerRadius = cornerRadius)
        drawRoundRect(color = WalkthroughPopupContentStyle.overlayColor, cornerRadius = cornerRadius)
        drawRoundRect(
            brush = borderBrush,
            cornerRadius = cornerRadius,
            alpha = WalkthroughPopupContentStyle.BORDER_ALPHA,
            style = Stroke(width = WalkthroughPopupContentStyle.borderStrokeWidth.toPx())
        )
    }
}

@Composable
private fun WalkthroughPopupHeader(
    item: WalkthroughItem,
    items: List<WalkthroughItem>,
    currentIndex: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughPopupContentStyle.headerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(WalkthroughPopupContentStyle.headerPillRadius))
            .background(Color.White.copy(alpha = WalkthroughPopupContentStyle.HEADER_BACKGROUND_ALPHA))
            .border(
                width = WalkthroughPopupContentStyle.headerBorderWidth,
                color = Color.White.copy(alpha = WalkthroughPopupContentStyle.HEADER_BORDER_ALPHA),
                shape = RoundedCornerShape(WalkthroughPopupContentStyle.headerPillRadius)
            )
            .padding(
                horizontal = WalkthroughPopupContentStyle.headerPaddingHorizontal,
                vertical = WalkthroughPopupContentStyle.headerPaddingVertical
            )
    ) {
        AiBadge()
        if (items.size > 1) {
            Text(
                text = "${currentIndex + 1} / ${items.size}",
                color = WalkthroughPopupContentStyle.metaTextColor,
                fontSize = WalkthroughPopupContentStyle.metaTextSize,
                fontWeight = FontWeight.Medium
            )
        } else if (item.line != null) {
            Text(
                text = "Line ${item.line}",
                color = WalkthroughPopupContentStyle.metaTextColor,
                fontSize = WalkthroughPopupContentStyle.metaTextSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ColumnScope.WalkthroughPopupBody(
    project: Project,
    item: WalkthroughItem,
    scrollState: ScrollState,
    showScrollbar: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .heightIn(min = WalkthroughPopupContentStyle.textMinHeight)
            .clip(RoundedCornerShape(WalkthroughPopupContentStyle.bodyCornerRadius))
            .background(Color.White.copy(alpha = WalkthroughPopupContentStyle.BODY_BACKGROUND_ALPHA))
            .border(
                width = WalkthroughPopupContentStyle.bodyBorderWidth,
                color = Color.White.copy(alpha = WalkthroughPopupContentStyle.BODY_BORDER_ALPHA),
                shape = RoundedCornerShape(WalkthroughPopupContentStyle.bodyCornerRadius)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = WalkthroughPopupContentStyle.markdownPaddingStart,
                    top = WalkthroughPopupContentStyle.markdownPaddingTop,
                    end = WalkthroughPopupContentStyle.markdownPaddingEnd,
                    bottom = WalkthroughPopupContentStyle.markdownPaddingBottom
                )
                .verticalScroll(scrollState)
        ) {
            MarkdownContent(project, item.text)
        }

        if (showScrollbar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        end = WalkthroughPopupContentStyle.scrollbarPaddingEnd,
                        top = WalkthroughPopupContentStyle.scrollbarPaddingTop,
                        bottom = WalkthroughPopupContentStyle.scrollbarPaddingBottom
                    )
            ) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun WalkthroughPopupNavigation(
    currentIndex: Int,
    lastIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughPopupContentStyle.navigationSpacing),
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
private fun AiBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(WalkthroughPopupContentStyle.badgeGradientColors))
            .padding(
                horizontal = WalkthroughPopupContentStyle.badgePaddingHorizontal,
                vertical = WalkthroughPopupContentStyle.badgePaddingVertical
            )
    ) {
        Text(
            text = "Walkthrough",
            color = Color.White,
            fontSize = WalkthroughPopupContentStyle.badgeTextSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AiCloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(WalkthroughPopupContentStyle.closeButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = WalkthroughPopupContentStyle.CLOSE_BUTTON_BACKGROUND_ALPHA))
            .border(
                WalkthroughPopupContentStyle.closeButtonBorderWidth,
                Color.White.copy(alpha = WalkthroughPopupContentStyle.CLOSE_BUTTON_BORDER_ALPHA),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✕",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = WalkthroughPopupContentStyle.closeButtonTextSize
        )
    }
}

@Composable
private fun AiNavButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    val backgroundBrush = if (emphasized) {
        Brush.linearGradient(WalkthroughPopupContentStyle.navPrimaryGradientColors)
    } else {
        Brush.linearGradient(WalkthroughPopupContentStyle.navSecondaryGradientColors)
    }
    val borderColor = if (emphasized) {
        WalkthroughPopupContentStyle.navPrimaryBorderColor
    } else {
        Color.White.copy(alpha = WalkthroughPopupContentStyle.CLOSE_BUTTON_BORDER_ALPHA)
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
            .border(WalkthroughPopupContentStyle.closeButtonBorderWidth, borderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = WalkthroughPopupContentStyle.navHorizontalPadding,
                vertical = WalkthroughPopupContentStyle.navVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(
                alpha = if (enabled) 1f else WalkthroughPopupContentStyle.NAV_TEXT_DISABLED_ALPHA
            ),
            fontSize = WalkthroughPopupContentStyle.navTextSize,
            style = textStyle
        )
    }
}
