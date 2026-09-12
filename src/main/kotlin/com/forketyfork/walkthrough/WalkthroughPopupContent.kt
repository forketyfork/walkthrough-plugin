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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
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
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.time.TimeSource

private object WalkthroughPopupContentStyle {
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
    const val HEADER_BACKGROUND_ALPHA = 0.06f
    val headerBorderWidth = 1.dp
    const val HEADER_BORDER_ALPHA = 0.08f
    val headerPaddingHorizontal = 8.dp
    val headerPaddingVertical = 6.dp
    val metaTextSize = 12.sp
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

private data class WalkthroughPopupAnimationState(val gradientShift: Float, val glowShift: Float)

@Suppress("LongParameterList")
@Composable
internal fun WalkthroughItemContent(
    project: Project,
    session: WalkthroughSession,
    palette: WalkthroughPalette,
    onItemDisplay: (WalkthroughItem) -> Unit,
    onNavigateToSource: (WalkthroughItem) -> Unit,
    onClose: () -> Unit,
) {
    val items = session.items
    var currentIndex by session.currentIndexState
    val safeIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val item = items.getOrNull(safeIndex) ?: return
    val scrollState = rememberScrollState()
    val popupColors = palette.popupColors()
    val animationState = rememberPopupAnimationState(palette)
    val scrollbarStyle = rememberPopupScrollbarStyle(popupColors)
    val showScrollbar = scrollState.maxValue > 0
    val questionStatus by session.questionStatusState

    // Qodana's UnusedVariable inspection doesn't see that the delegated
    // property is read inside LaunchedEffect below, so it flags this as
    // unused. The indirection is required by Detekt's
    // LambdaParameterInRestartableEffect rule (Compose ruleset) — capturing
    // `onItemDisplay` directly inside a restartable effect would restart it
    // on every recomposition. Suppress the false positive locally.
    @Suppress("UnusedVariable")
    val currentOnItemDisplay by rememberUpdatedState(onItemDisplay)

    LaunchedEffect(item) {
        scrollState.scrollTo(0)
        currentOnItemDisplay(item)
    }

    CompositionLocalProvider(LocalScrollbarStyle provides scrollbarStyle) {
        WalkthroughPopupFrame(
            project = project,
            item = item,
            items = items,
            currentIndex = safeIndex,
            acceptsQuestions = session.acceptsQuestions,
            questionStatus = questionStatus,
            palette = palette,
            popupColors = popupColors,
            scrollState = scrollState,
            showScrollbar = showScrollbar,
            animationState = animationState,
            onPrevious = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
            onNext = { currentIndex = (safeIndex + 1).coerceAtMost(items.lastIndex) },
            onNavigateToSource = { onNavigateToSource(item) },
            onSubmitQuestion = { text -> session.submitQuestion(text) },
            onClose = onClose,
        )
    }
}

@Composable
private fun rememberPopupAnimationState(palette: WalkthroughPalette): WalkthroughPopupAnimationState {
    if (palette.isThemeBased || WalkthroughDebugOptions.disablePopupContentAnimation) {
        return remember {
            WalkthroughPopupAnimationState(
                gradientShift = WalkthroughPopupContentStyle.ANIMATION_START,
                glowShift = WalkthroughPopupContentStyle.ANIMATION_START,
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
private fun rememberPopupScrollbarStyle(colors: WalkthroughPopupColors): ScrollbarStyle = remember(colors) {
    ScrollbarStyle(
        minimalHeight = WalkthroughPopupContentStyle.scrollbarMinHeight,
        thickness = WalkthroughPopupContentStyle.scrollbarThickness,
        shape = CircleShape,
        hoverDurationMillis = WalkthroughPopupContentStyle.SCROLLBAR_HOVER_DURATION_MS,
        unhoverColor = colors.scrollbarUnhoverColor,
        hoverColor = colors.scrollbarHoverColor,
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun WalkthroughPopupFrame(
    project: Project,
    item: WalkthroughItem,
    items: List<WalkthroughItem>,
    currentIndex: Int,
    acceptsQuestions: Boolean,
    questionStatus: WalkthroughQuestionStatus,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
    scrollState: ScrollState,
    showScrollbar: Boolean,
    animationState: WalkthroughPopupAnimationState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNavigateToSource: () -> Unit,
    onSubmitQuestion: (String) -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(palette.popupCornerRadius())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .walkthroughPopupBackground(animationState, palette, popupColors),
    ) {
        AiCloseButton(
            palette = palette,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WalkthroughPopupContentStyle.closeButtonPadding),
            onClick = onClose,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = WalkthroughPopupContentStyle.contentPaddingStart,
                    top = WalkthroughPopupContentStyle.contentPaddingTop,
                    end = WalkthroughPopupContentStyle.contentPaddingEnd,
                    bottom = WalkthroughPopupContentStyle.contentPaddingBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(WalkthroughPopupContentStyle.contentSectionSpacing),
        ) {
            WalkthroughPopupHeader(
                item = item,
                items = items,
                currentIndex = currentIndex,
                palette = palette,
                popupColors = popupColors,
            )
            WalkthroughPopupBody(
                project = project,
                item = item,
                palette = palette,
                popupColors = popupColors,
                scrollState = scrollState,
                showScrollbar = showScrollbar,
            )
            val showNavigation = items.size > 1
            val sourceCallback = onNavigateToSource.takeIf { item.hasNavigationTarget() }
            if (showNavigation || sourceCallback != null) {
                WalkthroughPopupNavigation(
                    showNavigation = showNavigation,
                    currentIndex = currentIndex,
                    lastIndex = items.lastIndex,
                    palette = palette,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onNavigateToSource = sourceCallback,
                )
            }
            if (acceptsQuestions) {
                WalkthroughQuestionInput(
                    status = questionStatus,
                    palette = palette,
                    popupColors = popupColors,
                    onSubmit = onSubmitQuestion,
                )
            }
        }
    }
}

private fun WalkthroughItem.hasNavigationTarget(): Boolean =
    file != null || line != null || diffId != null || diffFile != null

@Suppress("LongMethod")
private fun Modifier.walkthroughPopupBackground(
    animationState: WalkthroughPopupAnimationState,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
): Modifier = drawWithCache {
    val cornerRadius = CornerRadius(
        palette.popupCornerRadius().toPx(),
        palette.popupCornerRadius().toPx(),
    )
    val backgroundBrush = Brush.linearGradient(
        colors = if (palette.isThemeBased) {
            listOf(popupColors.backgroundColor, popupColors.backgroundColor)
        } else {
            palette.backgroundGradientColors
        },
        start = Offset(
            size.width * (animationState.gradientShift - WalkthroughPopupContentStyle.BACKGROUND_START_X_SHIFT),
            -size.height * WalkthroughPopupContentStyle.BACKGROUND_START_Y_SHIFT,
        ),
        end = Offset(
            size.width * (animationState.gradientShift + WalkthroughPopupContentStyle.BACKGROUND_END_X_SHIFT),
            size.height * WalkthroughPopupContentStyle.BACKGROUND_END_Y_SHIFT,
        ),
    )
    val glowBrush = Brush.radialGradient(
        colors = if (palette.isThemeBased) {
            listOf(Color.Transparent, Color.Transparent)
        } else {
            palette.glowGradientColors
        },
        center = Offset(
            size.width * (
                WalkthroughPopupContentStyle.GLOW_CENTER_BASE_X +
                    animationState.glowShift * WalkthroughPopupContentStyle.GLOW_CENTER_SHIFT_X
                ),
            size.height * WalkthroughPopupContentStyle.GLOW_CENTER_Y,
        ),
        radius = size.minDimension * WalkthroughPopupContentStyle.GLOW_RADIUS_FACTOR,
    )
    val borderBrush = Brush.linearGradient(
        colors = if (palette.isThemeBased) {
            listOf(popupColors.borderColor, popupColors.borderColor)
        } else {
            palette.borderGradientColors
        },
        start = Offset(
            size.width * (animationState.gradientShift - WalkthroughPopupContentStyle.BORDER_GRADIENT_START_SHIFT),
            0f,
        ),
        end = Offset(size.width * animationState.gradientShift, size.height),
    )

    onDrawBehind {
        drawRoundRect(brush = backgroundBrush, cornerRadius = cornerRadius)
        if (!palette.isThemeBased) {
            drawRoundRect(brush = glowBrush, cornerRadius = cornerRadius)
            drawRoundRect(color = palette.overlayColor, cornerRadius = cornerRadius)
        }
        drawRoundRect(
            brush = borderBrush,
            cornerRadius = cornerRadius,
            alpha = if (palette.isThemeBased) 1f else WalkthroughPopupContentStyle.BORDER_ALPHA,
            style = Stroke(width = WalkthroughPopupContentStyle.borderStrokeWidth.toPx()),
        )
    }
}

@Composable
private fun WalkthroughPopupHeader(
    item: WalkthroughItem,
    items: List<WalkthroughItem>,
    currentIndex: Int,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughPopupContentStyle.headerSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(palette.headerCornerRadius()))
            .background(
                if (palette.isThemeBased) {
                    popupColors.fieldBackgroundColor
                } else {
                    Color.White.copy(alpha = WalkthroughPopupContentStyle.HEADER_BACKGROUND_ALPHA)
                },
            )
            .border(
                width = WalkthroughPopupContentStyle.headerBorderWidth,
                color = if (palette.isThemeBased) {
                    popupColors.borderColor
                } else {
                    Color.White.copy(alpha = WalkthroughPopupContentStyle.HEADER_BORDER_ALPHA)
                },
                shape = RoundedCornerShape(palette.headerCornerRadius()),
            )
            .padding(
                horizontal = WalkthroughPopupContentStyle.headerPaddingHorizontal,
                vertical = WalkthroughPopupContentStyle.headerPaddingVertical,
            ),
    ) {
        AiBadge(palette)
        val meta = headerMetaText(item = item, items = items, currentIndex = currentIndex)
        if (meta != null) {
            Text(
                text = meta,
                color = popupColors.metaTextColor,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = WalkthroughPopupContentStyle.metaTextSize,
                    fontWeight = FontWeight.Medium,
                ),
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
@Suppress("LongParameterList")
private fun ColumnScope.WalkthroughPopupBody(
    project: Project,
    item: WalkthroughItem,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
    scrollState: ScrollState,
    showScrollbar: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
            .heightIn(min = WalkthroughPopupContentStyle.textMinHeight)
            .clip(RoundedCornerShape(palette.bodyCornerRadius()))
            .background(
                if (palette.isThemeBased) {
                    popupColors.fieldBackgroundColor
                } else {
                    Color.White.copy(alpha = WalkthroughPopupContentStyle.BODY_BACKGROUND_ALPHA)
                },
            )
            .border(
                width = WalkthroughPopupContentStyle.bodyBorderWidth,
                color = if (palette.isThemeBased) {
                    popupColors.borderColor
                } else {
                    Color.White.copy(alpha = WalkthroughPopupContentStyle.BODY_BORDER_ALPHA)
                },
                shape = RoundedCornerShape(palette.bodyCornerRadius()),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = WalkthroughPopupContentStyle.markdownPaddingStart,
                    top = WalkthroughPopupContentStyle.markdownPaddingTop,
                    end = WalkthroughPopupContentStyle.markdownPaddingEnd,
                    bottom = WalkthroughPopupContentStyle.markdownPaddingBottom,
                )
                .verticalScroll(scrollState),
        ) {
            MarkdownContent(project, item.text, palette)
        }

        if (showScrollbar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        end = WalkthroughPopupContentStyle.scrollbarPaddingEnd,
                        top = WalkthroughPopupContentStyle.scrollbarPaddingTop,
                        bottom = WalkthroughPopupContentStyle.scrollbarPaddingBottom,
                    ),
            ) {
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}
