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
    const val ANIMATION_START = 0f
    const val ANIMATION_END = 1f
    const val GRADIENT_ANIMATION_DURATION_MS = 5400
    const val GLOW_ANIMATION_DURATION_MS = 3200

    // Drives shift updates via a manual coroutine instead of Compose's frame clock, so the
    // SkiaLayer (and the editor underneath the translucent popup corners) only repaints at
    // this cadence. Lower values save more CPU at the cost of visible stepping.
    const val ANIMATION_FRAME_INTERVAL_MS = 67L
    const val BACKGROUND_START_X_SHIFT = 0.7f
    const val BACKGROUND_START_Y_SHIFT = 0.2f
    const val BACKGROUND_END_X_SHIFT = 0.5f
    const val BACKGROUND_END_Y_SHIFT = 1.1f
    const val GLOW_CENTER_BASE_X = 0.18f
    const val GLOW_CENTER_SHIFT_X = 0.62f
    const val GLOW_CENTER_Y = 0.2f
    const val GLOW_RADIUS_FACTOR = 0.95f
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
    session: WalkthroughSession,
    palette: WalkthroughPalette,
    onItemDisplayed: (WalkthroughItem) -> Unit,
    onNavigateToSource: (WalkthroughItem) -> Unit,
    onClose: () -> Unit
) {
    val items = session.items
    var currentIndex by session.currentIndexState
    val safeIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val item = items.getOrNull(safeIndex) ?: return
    val scrollState = rememberScrollState()
    val animationState = rememberPopupAnimationState()
    val scrollbarStyle = rememberPopupScrollbarStyle(palette)
    val showScrollbar = scrollState.maxValue > 0
    val isLoading by session.loadingState

    LaunchedEffect(item) {
        scrollState.scrollTo(0)
        onItemDisplayed(item)
    }

    CompositionLocalProvider(LocalScrollbarStyle provides scrollbarStyle) {
        WalkthroughPopupFrame(
            project = project,
            item = item,
            items = items,
            currentIndex = safeIndex,
            acceptsQuestions = session.acceptsQuestions,
            isLoading = isLoading,
            palette = palette,
            scrollState = scrollState,
            showScrollbar = showScrollbar,
            animationState = animationState,
            onPrevious = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
            onNext = { currentIndex = (safeIndex + 1).coerceAtMost(items.lastIndex) },
            onNavigateToSource = { onNavigateToSource(item) },
            onSubmitQuestion = { text -> session.submitQuestion(text) },
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
private fun rememberPopupScrollbarStyle(palette: WalkthroughPalette): ScrollbarStyle =
    remember(palette) {
        ScrollbarStyle(
            minimalHeight = WalkthroughPopupContentStyle.scrollbarMinHeight,
            thickness = WalkthroughPopupContentStyle.scrollbarThickness,
            shape = CircleShape,
            hoverDurationMillis = WalkthroughPopupContentStyle.SCROLLBAR_HOVER_DURATION_MS,
            unhoverColor = palette.scrollbarUnhoverColor,
            hoverColor = palette.scrollbarHoverColor
        )
    }

@Suppress("LongParameterList")
@Composable
private fun WalkthroughPopupFrame(
    project: Project,
    item: WalkthroughItem,
    items: List<WalkthroughItem>,
    currentIndex: Int,
    acceptsQuestions: Boolean,
    isLoading: Boolean,
    palette: WalkthroughPalette,
    scrollState: ScrollState,
    showScrollbar: Boolean,
    animationState: WalkthroughPopupAnimationState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNavigateToSource: () -> Unit,
    onSubmitQuestion: (String) -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(WalkthroughPopupContentStyle.cornerRadius)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .walkthroughPopupBackground(animationState, palette)
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
            WalkthroughPopupHeader(item = item, items = items, currentIndex = currentIndex, palette = palette)
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
                    palette = palette,
                    onPrevious = onPrevious,
                    onNext = onNext
                )
            }
            if (acceptsQuestions) {
                WalkthroughQuestionInput(
                    isLoading = isLoading,
                    palette = palette,
                    onSubmit = onSubmitQuestion
                )
            }
        }
    }
}

private fun Modifier.walkthroughPopupBackground(
    animationState: WalkthroughPopupAnimationState,
    palette: WalkthroughPalette
): Modifier = drawWithCache {
    val cornerRadius = CornerRadius(
        WalkthroughPopupContentStyle.cornerRadius.toPx(),
        WalkthroughPopupContentStyle.cornerRadius.toPx()
    )
    val backgroundBrush = Brush.linearGradient(
        colors = palette.backgroundGradientColors,
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
        colors = palette.glowGradientColors,
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
        colors = palette.borderGradientColors,
        start = Offset(
            size.width * (animationState.gradientShift - WalkthroughPopupContentStyle.BORDER_GRADIENT_START_SHIFT),
            0f
        ),
        end = Offset(size.width * animationState.gradientShift, size.height)
    )

    onDrawBehind {
        drawRoundRect(brush = backgroundBrush, cornerRadius = cornerRadius)
        drawRoundRect(brush = glowBrush, cornerRadius = cornerRadius)
        drawRoundRect(color = palette.overlayColor, cornerRadius = cornerRadius)
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
    currentIndex: Int,
    palette: WalkthroughPalette
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
        AiBadge(palette)
        val meta = headerMetaText(item = item, items = items, currentIndex = currentIndex)
        if (meta != null) {
            Text(
                text = meta,
                color = palette.metaTextColor,
                fontSize = WalkthroughPopupContentStyle.metaTextSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun headerMetaText(item: WalkthroughItem, items: List<WalkthroughItem>, currentIndex: Int): String? {
    val label = item.label
    val lineSuffix = item.line?.let { "Line $it" }
    val parts = buildList {
        when {
            label != null -> add("Step $label")
            items.size > 1 -> add("${currentIndex + 1} / ${items.size}")
        }
        if (lineSuffix != null) add(lineSuffix)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
