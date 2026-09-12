package com.forketyfork.walkthrough

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
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
        Color.White.copy(alpha = 0.08f),
    )
    const val NAV_TEXT_DISABLED_ALPHA = 0.45f
    val navHorizontalPadding = 14.dp
    val navVerticalPadding = 8.dp
    val navTextSize = 13.sp
    val questionRowSpacing = 8.dp
    val questionFieldPaddingHorizontal = 14.dp
    val questionFieldTextSize = 13.sp
    val questionStatusSpacing = 4.dp
    val questionStatusTextSize = 12.sp
    const val QUESTION_FIELD_BACKGROUND_ALPHA = 0.12f
    const val QUESTION_FIELD_BORDER_ALPHA = 0.2f
    const val QUESTION_PLACEHOLDER_ALPHA = 0.55f
    const val QUESTION_STATUS_ALPHA = 0.72f
    val sendButtonSize = 34.dp
    val spinnerSize = 18.dp
    val spinnerStrokeWidth = 2.dp
    const val SPINNER_SWEEP_DEGREES = 270f
    const val SPINNER_ROTATION_DURATION_MS = 1000
    const val SPINNER_TRACK_ALPHA = 0.18f
}

@Suppress("LongParameterList")
@Composable
internal fun WalkthroughPopupNavigation(
    showNavigation: Boolean,
    currentIndex: Int,
    lastIndex: Int,
    palette: WalkthroughPalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNavigateToSource: (() -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughWidgetStyle.navigationSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.wrapContentWidth(),
    ) {
        if (showNavigation) {
            AiNavButton(
                label = "Previous",
                enabled = currentIndex > 0,
                emphasized = false,
                palette = palette,
                onClick = onPrevious,
            )
            AiNavButton(
                label = "Next",
                enabled = currentIndex < lastIndex,
                emphasized = true,
                palette = palette,
                onClick = onNext,
            )
        }
        if (onNavigateToSource != null) {
            GoToSourceButton(palette = palette, onClick = onNavigateToSource)
        }
    }
}

@Composable
internal fun AiBadge(palette: WalkthroughPalette) {
    val popupColors = palette.popupColors()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(palette.headerCornerRadius()))
            .background(
                brush = if (palette.isThemeBased) {
                    Brush.linearGradient(
                        listOf(
                            popupColors.accentColor.copy(alpha = 0.16f),
                            popupColors.accentColor.copy(alpha = 0.16f),
                        ),
                    )
                } else {
                    Brush.linearGradient(palette.badgeGradientColors)
                },
            )
            .padding(
                horizontal = WalkthroughWidgetStyle.badgePaddingHorizontal,
                vertical = WalkthroughWidgetStyle.badgePaddingVertical,
            ),
    ) {
        Text(
            text = "Walkthrough",
            color = if (palette.isThemeBased) popupColors.contentColor else Color.White,
            style = if (palette.isThemeBased) {
                JewelTheme.defaultTextStyle.copy(
                    fontSize = WalkthroughWidgetStyle.badgeTextSize,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                TextStyle(
                    fontSize = WalkthroughWidgetStyle.badgeTextSize,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}

@Composable
internal fun AiCloseButton(palette: WalkthroughPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (palette.isThemeBased) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(WalkthroughWidgetStyle.closeButtonSize),
        ) {
            Icon(
                key = AllIconsKeys.General.Close,
                contentDescription = "Close walkthrough",
            )
        }
        return
    }
    Box(
        modifier = modifier
            .size(WalkthroughWidgetStyle.closeButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BACKGROUND_ALPHA))
            .border(
                WalkthroughWidgetStyle.closeButtonBorderWidth,
                Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BORDER_ALPHA),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "\u2715",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = WalkthroughWidgetStyle.closeButtonTextSize,
        )
    }
}

@Composable
internal fun GoToSourceButton(palette: WalkthroughPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (palette.isThemeBased) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(WalkthroughWidgetStyle.sendButtonSize),
        ) {
            Icon(
                key = AllIconsKeys.General.Locate,
                contentDescription = "Go to source",
            )
        }
        return
    }
    Box(
        modifier = modifier
            .size(WalkthroughWidgetStyle.sendButtonSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BACKGROUND_ALPHA))
            .border(
                WalkthroughWidgetStyle.closeButtonBorderWidth,
                Color.White.copy(alpha = WalkthroughWidgetStyle.CLOSE_BUTTON_BORDER_ALPHA),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.General.Locate,
            contentDescription = "Go to source",
            tint = Color.White,
        )
    }
}

@Composable
internal fun AiNavButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean,
    palette: WalkthroughPalette,
    onClick: () -> Unit,
) {
    if (palette.isThemeBased) {
        if (emphasized) {
            DefaultButton(onClick = onClick, enabled = enabled) {
                Text(label)
            }
        } else {
            OutlinedButton(onClick = onClick, enabled = enabled) {
                Text(label)
            }
        }
        return
    }
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
                vertical = WalkthroughWidgetStyle.navVerticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White.copy(
                alpha = if (enabled) 1f else WalkthroughWidgetStyle.NAV_TEXT_DISABLED_ALPHA,
            ),
            fontSize = WalkthroughWidgetStyle.navTextSize,
            style = textStyle,
        )
    }
}

@Composable
internal fun WalkthroughQuestionInput(
    status: WalkthroughQuestionStatus,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val canType = status == WalkthroughQuestionStatus.AgentNotWaiting ||
        status == WalkthroughQuestionStatus.WaitingForQuestion
    val canSubmit = canType && text.isNotBlank()
    val submit: () -> Unit = {
        if (canSubmit) {
            onSubmit(text)
            text = ""
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WalkthroughWidgetStyle.questionStatusSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WalkthroughWidgetStyle.questionRowSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuestionTextField(
                text = text,
                enabled = canType,
                placeholder = questionPlaceholder(status),
                palette = palette,
                popupColors = popupColors,
                onTextChange = { value -> text = value },
                onSend = submit,
                modifier = Modifier.weight(1f),
            )
            if (status == WalkthroughQuestionStatus.ProcessingQuestion) {
                QuestionSpinner(palette = palette, popupColors = popupColors)
            } else {
                SendQuestionButton(
                    enabled = canSubmit,
                    palette = palette,
                    onClick = submit,
                )
            }
        }
        QuestionStatusText(status = status, popupColors = popupColors)
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun QuestionTextField(
    text: String,
    enabled: Boolean,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    palette: WalkthroughPalette,
    popupColors: WalkthroughPopupColors,
    modifier: Modifier = Modifier,
) {
    val fieldShape = RoundedCornerShape(palette.questionFieldCornerRadius())
    Box(
        modifier = modifier
            .height(WalkthroughWidgetStyle.sendButtonSize)
            .clip(fieldShape)
            .background(
                if (palette.isThemeBased) {
                    popupColors.fieldBackgroundColor
                } else {
                    Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_FIELD_BACKGROUND_ALPHA)
                },
                fieldShape,
            )
            .border(
                WalkthroughWidgetStyle.closeButtonBorderWidth,
                if (palette.isThemeBased) {
                    popupColors.fieldBorderColor
                } else {
                    Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_FIELD_BORDER_ALPHA)
                },
                fieldShape,
            )
            .padding(horizontal = WalkthroughWidgetStyle.questionFieldPaddingHorizontal),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            singleLine = true,
            textStyle = if (palette.isThemeBased) {
                JewelTheme.defaultTextStyle.copy(
                    color = popupColors.contentColor,
                    fontSize = WalkthroughWidgetStyle.questionFieldTextSize,
                    fontWeight = FontWeight.Normal,
                )
            } else {
                TextStyle(
                    color = Color.White,
                    fontSize = WalkthroughWidgetStyle.questionFieldTextSize,
                    fontWeight = FontWeight.Normal,
                )
            },
            cursorBrush = SolidColor(if (palette.isThemeBased) popupColors.accentColor else Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = if (palette.isThemeBased) {
                            popupColors.disabledContentColor
                        } else {
                            Color.White.copy(alpha = WalkthroughWidgetStyle.QUESTION_PLACEHOLDER_ALPHA)
                        },
                        style = if (palette.isThemeBased) {
                            JewelTheme.defaultTextStyle.copy(fontSize = WalkthroughWidgetStyle.questionFieldTextSize)
                        } else {
                            TextStyle(fontSize = WalkthroughWidgetStyle.questionFieldTextSize)
                        },
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun QuestionStatusText(status: WalkthroughQuestionStatus, popupColors: WalkthroughPopupColors) {
    val text = questionStatusText(status) ?: return
    Text(
        text = text,
        color = popupColors.disabledContentColor.copy(alpha = WalkthroughWidgetStyle.QUESTION_STATUS_ALPHA),
        style = JewelTheme.defaultTextStyle.copy(fontSize = WalkthroughWidgetStyle.questionStatusTextSize),
    )
}

private fun questionPlaceholder(status: WalkthroughQuestionStatus): String = when (status) {
    WalkthroughQuestionStatus.AgentNotWaiting,
    WalkthroughQuestionStatus.WaitingForQuestion,
    -> "Ask a question about this step…"

    WalkthroughQuestionStatus.QuestionQueued -> "Question queued"

    WalkthroughQuestionStatus.ProcessingQuestion -> "Question sent"
}

private fun questionStatusText(status: WalkthroughQuestionStatus): String? = when (status) {
    WalkthroughQuestionStatus.AgentNotWaiting,
    WalkthroughQuestionStatus.QuestionQueued,
    ->
        "⚠\uFE0F Agent is not listening, it should call await_walkthrough_question"

    WalkthroughQuestionStatus.WaitingForQuestion,
    WalkthroughQuestionStatus.ProcessingQuestion,
    -> null
}

@Composable
private fun SendQuestionButton(enabled: Boolean, palette: WalkthroughPalette, onClick: () -> Unit) {
    if (palette.isThemeBased) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(WalkthroughWidgetStyle.sendButtonSize),
        ) {
            Icon(
                key = AllIconsKeys.Actions.Forward,
                contentDescription = "Send question",
            )
        }
        return
    }
    val backgroundBrush = Brush.linearGradient(palette.navPrimaryGradientColors)
    Box(
        modifier = Modifier
            .size(WalkthroughWidgetStyle.sendButtonSize)
            .clip(CircleShape)
            .background(backgroundBrush, CircleShape)
            .border(WalkthroughWidgetStyle.closeButtonBorderWidth, palette.navPrimaryBorderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.Actions.Forward,
            contentDescription = "Send question",
            tint = Color.White.copy(
                alpha = if (enabled) 1f else WalkthroughWidgetStyle.NAV_TEXT_DISABLED_ALPHA,
            ),
        )
    }
}

@Composable
private fun QuestionSpinner(palette: WalkthroughPalette, popupColors: WalkthroughPopupColors) {
    val transition = rememberInfiniteTransition(label = "walkthrough-question-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WalkthroughWidgetStyle.SPINNER_ROTATION_DURATION_MS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "walkthrough-question-spinner-rotation",
    )
    Box(
        modifier = Modifier.size(WalkthroughWidgetStyle.sendButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(WalkthroughWidgetStyle.spinnerSize).rotate(rotation)) {
            drawSpinnerArc(palette = palette, popupColors = popupColors)
        }
    }
}

private fun DrawScope.drawSpinnerArc(palette: WalkthroughPalette, popupColors: WalkthroughPopupColors) {
    val strokeWidthPx = WalkthroughWidgetStyle.spinnerStrokeWidth.toPx()
    val arcSize = Size(
        size.width - strokeWidthPx,
        size.height - strokeWidthPx,
    )
    val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
    drawArc(
        color = if (palette.isThemeBased) {
            popupColors.disabledContentColor.copy(alpha = WalkthroughWidgetStyle.SPINNER_TRACK_ALPHA)
        } else {
            Color.White.copy(alpha = WalkthroughWidgetStyle.SPINNER_TRACK_ALPHA)
        },
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx),
    )
    drawArc(
        color = if (palette.isThemeBased) popupColors.accentColor else palette.navPrimaryBorderColor,
        startAngle = 0f,
        sweepAngle = WalkthroughWidgetStyle.SPINNER_SWEEP_DEGREES,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidthPx),
    )
}
