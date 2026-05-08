package com.forketyfork.walkthrough

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import org.jetbrains.jewel.ui.component.Text

private object WalkthroughPopupContentStyle {
    val cornerRadius = 24.dp
    val textMinHeight = 180.dp
    val scrollbarMinHeight = 28.dp
    val scrollbarThickness = 6.dp
    const val SCROLLBAR_HOVER_DURATION_MS = 300
    val scrollbarUnhoverColor = WalkthroughColors.scrollbarIndigo
    val scrollbarHoverColor = WalkthroughColors.pink
    const val ANIMATION_START = 0f
    const val ANIMATION_END = 1f
    const val GRADIENT_ANIMATION_DURATION_MS = 5400
    const val GLOW_ANIMATION_DURATION_MS = 3200

    // Drives shift updates via a manual coroutine instead of Compose's frame clock, so the
    // SkiaLayer (and the editor underneath the translucent popup corners) only repaints at
    // this cadence. Lower values save more CPU at the cost of visible stepping.
    const val ANIMATION_FRAME_INTERVAL_MS = 67L
    val backgroundGradientColors = listOf(
        WalkthroughColors.veryDarkPurple,
        WalkthroughColors.darkPurple,
        WalkthroughColors.magenta,
        WalkthroughColors.navyBlue
    )
    const val BACKGROUND_START_X_SHIFT = 0.7f
    const val BACKGROUND_START_Y_SHIFT = 0.2f
    const val BACKGROUND_END_X_SHIFT = 0.5f
    const val BACKGROUND_END_Y_SHIFT = 1.1f
    val glowGradientColors = listOf(
        WalkthroughColors.lightPink.copy(alpha = 0.4f),
        WalkthroughColors.glowLavender,
        Color.Transparent
    )
    const val GLOW_CENTER_BASE_X = 0.18f
    const val GLOW_CENTER_SHIFT_X = 0.62f
    const val GLOW_CENTER_Y = 0.2f
    const val GLOW_RADIUS_FACTOR = 0.95f
    val overlayColor = WalkthroughColors.overlay
    val borderGradientColors = listOf(
        WalkthroughColors.purple,
        WalkthroughColors.pink,
        WalkthroughColors.blue,
        WalkthroughColors.purple
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
    val metaTextColor = WalkthroughColors.textMeta
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
    onNavigateToSource: (WalkthroughItem) -> Unit,
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
            onNavigateToSource = { onNavigateToSource(item) },
            onClose = onClose
        )
    }
}

@Composable
private fun rememberPopupAnimationState(): WalkthroughPopupAnimationState {
    if (WalkthroughDebugOptions.disablePopupContentAnimation) {
        return remember {
            WalkthroughPopupAnimationState(
                gradientShift = WalkthroughPopupContentStyle.ANIMATION_START,
                glowShift = WalkthroughPopupContentStyle.ANIMATION_START
            )
        }
    }
    var gradientShift by remember { mutableStateOf(WalkthroughPopupContentStyle.ANIMATION_START) }
    var glowShift by remember { mutableStateOf(WalkthroughPopupContentStyle.ANIMATION_START) }
    LaunchedEffect(Unit) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            val elapsed = mark.elapsedNow().inWholeMilliseconds
            gradientShift = reverseLinearShift(elapsed, WalkthroughPopupContentStyle.GRADIENT_ANIMATION_DURATION_MS)
            glowShift = reverseLinearShift(elapsed, WalkthroughPopupContentStyle.GLOW_ANIMATION_DURATION_MS)
            delay(WalkthroughPopupContentStyle.ANIMATION_FRAME_INTERVAL_MS)
        }
    }
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
    onNavigateToSource: () -> Unit,
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

        if (item.file != null || item.line != null) {
            GoToSourceButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(WalkthroughPopupContentStyle.closeButtonPadding),
                onClick = onNavigateToSource
            )
        }

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
