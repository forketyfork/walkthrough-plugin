package com.forketyfork.walkthrough

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private object WalkthroughWidgetStyle {
    val navigationSpacing = 10.dp
    val badgePaddingHorizontal = 12.dp
    val badgePaddingVertical = 6.dp
    val badgeTextSize = 11.sp
    val closeButtonSize = 30.dp
    const val CLOSE_BUTTON_BACKGROUND_ALPHA = 0.12f
    val closeButtonBorderWidth = 1.dp
    const val CLOSE_BUTTON_BORDER_ALPHA = 0.18f
    val closeButtonTextSize = 13.sp
    val navSecondaryGradientColors = listOf(
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.08f)
    )
    const val NAV_TEXT_DISABLED_ALPHA = 0.45f
    val navHorizontalPadding = 14.dp
    val navVerticalPadding = 8.dp
    val navTextSize = 13.sp
    val questionRowSpacing = 8.dp
    val questionFieldRadius = 18.dp
    val questionFieldPaddingHorizontal = 14.dp
    val questionFieldPaddingVertical = 10.dp
    val questionFieldTextSize = 13.sp
    const val QUESTION_FIELD_BACKGROUND_ALPHA = 0.12f
    const val QUESTION_FIELD_BORDER_ALPHA = 0.2f
    const val QUESTION_PLACEHOLDER_ALPHA = 0.55f
    val sendButtonSize = 34.dp
    val spinnerSize = 18.dp
    val spinnerStrokeWidth = 2.dp
    const val SPINNER_SWEEP_DEGREES = 270f
    const val SPINNER_ROTATION_DURATION_MS = 1000
    const val SPINNER_TRACK_ALPHA = 0.18f
}

@Composable
internal fun WalkthroughPopupNavigation(
    currentIndex: Int,
    lastIndex: Int,
    palette: WalkthroughPalette,
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
            palette = palette,
            onClick = onPrevious
        )
        AiNavButton(
            label = "Next",
            enabled = currentIndex < lastIndex,
            emphasized = true,
            palette = palette,
            onClick = onNext
        )
    }
}

@Composable
internal fun AiBadge(palette: WalkthroughPalette) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(palette.badgeGradientColors))
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
    palette: WalkthroughPalette,
    onClick: () -> Unit
) {
    val backgroundBrush = if (emphasized) {
        Brush.linearGradient(palette.navPrimaryGradientColors)
    } else {
        Brush.linearGradient(WalkthroughWidgetStyle.navSecondaryGradientColors)
    }
    val borderColor = if (emphasized) {
        palette.navPrimaryBorderColor
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

@Composable
internal fun WalkthroughQuestionInput(
    isLoading: Boolean,
    palette: WalkthroughPalette,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val canSubmit = !isLoading && text.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            onSubmit(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WalkthroughWidgetStyle.questionRowSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(WalkthroughWidgetStyle.questionFieldRadius))
                .background(
                    Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_FIELD_BACKGROUND_ALPHA),
                    RoundedCornerShape(WalkthroughWidgetStyle.questionFieldRadius)
                )
                .border(
                    WalkthroughWidgetStyle.closeButtonBorderWidth,
                    Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_FIELD_BORDER_ALPHA),
                    RoundedCornerShape(WalkthroughWidgetStyle.questionFieldRadius)
                )
                .padding(
                    horizontal = WalkthroughWidgetStyle.questionFieldPaddingHorizontal,
                    vertical = WalkthroughWidgetStyle.questionFieldPaddingVertical
                )
        ) {
            BasicTextField(
                value = text,
                onValueChange = { value -> text = value },
                enabled = !isLoading,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = WalkthroughWidgetStyle.questionFieldTextSize,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Ask a question about this step…",
                            color = Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_PLACEHOLDER_ALPHA),
                            fontSize = WalkthroughWidgetStyle.questionFieldTextSize
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (isLoading) {
            QuestionSpinner(palette = palette)
        } else {
            SendQuestionButton(enabled = canSubmit, palette = palette, onClick = submit)
        }
    }
}

@Composable
private fun SendQuestionButton(enabled: Boolean, palette: WalkthroughPalette, onClick: () -> Unit) {
    val backgroundBrush = Brush.linearGradient(palette.navPrimaryGradientColors)
    Box(
        modifier = Modifier
            .size(WalkthroughWidgetStyle.sendButtonSize)
            .clip(CircleShape)
            .background(backgroundBrush, CircleShape)
            .border(WalkthroughWidgetStyle.closeButtonBorderWidth, palette.navPrimaryBorderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            key = AllIconsKeys.Actions.Forward,
            contentDescription = "Send question",
            tint = Color.White.copy(
                alpha = if (enabled) 1f else WalkthroughWidgetStyle.NAV_TEXT_DISABLED_ALPHA
            )
        )
    }
}

@Composable
private fun QuestionSpinner(palette: WalkthroughPalette) {
    val transition = rememberInfiniteTransition(label = "walkthrough-question-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WalkthroughWidgetStyle.SPINNER_ROTATION_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "walkthrough-question-spinner-rotation"
    )
    Box(
        modifier = Modifier.size(WalkthroughWidgetStyle.sendButtonSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(WalkthroughWidgetStyle.spinnerSize).rotate(rotation)) {
            drawSpinnerArc(palette = palette)
        }
    }
}

private fun DrawScope.drawSpinnerArc(palette: WalkthroughPalette) {
    val strokeWidthPx = WalkthroughWidgetStyle.spinnerStrokeWidth.toPx()
    val arcSize = Size(
        size.width - strokeWidthPx,
        size.height - strokeWidthPx
    )
    val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
    drawArc(
        color = Color.White.copy(alpha = WalkthroughWidgetStyle.SPINNER_TRACK_ALPHA),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx)
    )
    drawArc(
        color = palette.navPrimaryBorderColor,
        startAngle = 0f,
        sweepAngle = WalkthroughWidgetStyle.SPINNER_SWEEP_DEGREES,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx)
    )
}
