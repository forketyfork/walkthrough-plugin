@file:OptIn(ExperimentalJewelApi::class)
// Jewel Markdown APIs are marked @ExperimentalJewelApi
@file:Suppress("UnstableApiUsage")

package com.forketyfork.walkthrough

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.bridge.code.highlighting.CodeHighlighterFactory
import org.jetbrains.jewel.bridge.theme.retrieveDefaultTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveEditorTextStyle
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.alerts.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.tables.create
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.autolink.AutolinkProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.strikethrough.GitHubStrikethroughRendererExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

private val PopupMarkdownTextColor = WalkthroughColors.textPrimary
private val PopupMarkdownCodeTextColor = WalkthroughColors.textCode
private val PopupMarkdownInlineCodeBackground = WalkthroughColors.pink.copy(alpha = 0.2f)
private val PopupMarkdownLinkColor = WalkthroughColors.blue
private val PopupMarkdownBlockBackground = WalkthroughColors.blockBackground
private val PopupMarkdownDividerColor = WalkthroughColors.divider

@Composable
internal fun MarkdownContent(
    project: Project,
    markdown: String,
    modifier: Modifier = Modifier
) {
    val baseTextStyle = rememberPopupBaseTextStyle()
    val editorTextStyle = rememberPopupEditorTextStyle()
    val markdownStyling = rememberPopupMarkdownStyling(baseTextStyle, editorTextStyle)
    val processor = rememberPopupMarkdownProcessor()
    val blockRenderer = rememberPopupMarkdownBlockRenderer(markdownStyling)
    val codeHighlighter = rememberPopupCodeHighlighter(project)

    ProvideMarkdownStyling(
        markdownStyling = markdownStyling,
        markdownMode = MarkdownMode.Standalone,
        markdownProcessor = processor,
        markdownBlockRenderer = blockRenderer,
        codeHighlighter = codeHighlighter,
    ) {
        Markdown(
            markdown = markdown,
            modifier = modifier.fillMaxWidth(),
            selectable = true,
            onUrlClick = { BrowserUtil.browse(it) },
            processor = processor,
            blockRenderer = blockRenderer,
        )
    }
}

@Composable
private fun rememberPopupBaseTextStyle(): TextStyle =
    remember(JewelTheme.instanceUuid) {
        retrieveDefaultTextStyle().copy(
            color = PopupMarkdownTextColor,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }

@Composable
private fun rememberPopupEditorTextStyle(): TextStyle =
    remember(JewelTheme.instanceUuid) {
        retrieveEditorTextStyle().copy(
            color = PopupMarkdownCodeTextColor,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }

@Composable
private fun rememberPopupMarkdownStyling(
    baseTextStyle: TextStyle,
    editorTextStyle: TextStyle
): MarkdownStyling =
    remember(JewelTheme.instanceUuid) {
        createPopupMarkdownStyling(baseTextStyle, editorTextStyle)
    }

@Composable
private fun rememberPopupMarkdownProcessor(): MarkdownProcessor =
    remember {
        MarkdownProcessor(
            listOf(
                GitHubAlertProcessorExtension,
                GitHubTableProcessorExtension,
                GitHubStrikethroughProcessorExtension(),
                AutolinkProcessorExtension,
            ),
            MarkdownMode.Standalone,
            parseEmbeddedHtml = true
        )
    }

@Composable
private fun rememberPopupMarkdownBlockRenderer(markdownStyling: MarkdownStyling): MarkdownBlockRenderer {
    val tableRenderer = remember(markdownStyling) {
        GitHubTableRendererExtension(GfmTableStyling.create(), markdownStyling)
    }
    val alertRenderer = remember(markdownStyling) {
        GitHubAlertRendererExtension(AlertStyling.create(), markdownStyling)
    }
    return remember(markdownStyling, tableRenderer, alertRenderer) {
        MarkdownBlockRenderer.create(
            markdownStyling,
            listOf(tableRenderer, alertRenderer, GitHubStrikethroughRendererExtension)
        )
    }
}

@Composable
// CodeHighlighterFactory takes a Project constructor parameter, so it must be retrieved as a
// project-level service despite its @Service annotation not specifying an explicit level
@Suppress("IncorrectServiceRetrieving")
private fun rememberPopupCodeHighlighter(project: Project) =
    remember(project) {
        project.service<CodeHighlighterFactory>().createHighlighter()
    }

private fun createPopupMarkdownStyling(
    baseTextStyle: TextStyle,
    editorTextStyle: TextStyle
): MarkdownStyling {
    val inlinesStyling = createPopupInlinesStyling(baseTextStyle, editorTextStyle)
    return MarkdownStyling.create(
        baseTextStyle = baseTextStyle,
        editorTextStyle = editorTextStyle,
        inlinesStyling = inlinesStyling,
        blockVerticalSpacing = 10.dp,
        paragraph = MarkdownStyling.Paragraph.create(inlinesStyling),
        heading = createPopupHeadingStyling(baseTextStyle, editorTextStyle),
        blockQuote = MarkdownStyling.BlockQuote.create(
            textColor = PopupMarkdownTextColor.copy(alpha = 0.88f),
            lineColor = PopupMarkdownLinkColor.copy(alpha = 0.45f),
        ),
        code = MarkdownStyling.Code.create(
            editorTextStyle = editorTextStyle,
            indented = MarkdownStyling.Code.Indented.create(
                textStyle = editorTextStyle,
                padding = PaddingValues(12.dp),
                shape = RoundedCornerShape(12.dp),
                background = PopupMarkdownBlockBackground,
            ),
            fenced = MarkdownStyling.Code.Fenced.create(
                textStyle = editorTextStyle,
                padding = PaddingValues(12.dp),
                shape = RoundedCornerShape(12.dp),
                background = PopupMarkdownBlockBackground,
                infoTextStyle = editorTextStyle.copy(
                    color = PopupMarkdownLinkColor,
                    fontSize = 12.sp,
                ),
            ),
        ),
        list = MarkdownStyling.List.create(
            baseTextStyle = baseTextStyle,
            ordered = MarkdownStyling.List.Ordered.create(
                numberStyle = baseTextStyle,
                itemVerticalSpacing = 4.dp,
                itemVerticalSpacingTight = 4.dp,
            ),
            unordered = MarkdownStyling.List.Unordered.create(
                bulletStyle = baseTextStyle,
                itemVerticalSpacing = 4.dp,
                itemVerticalSpacingTight = 4.dp,
            ),
        ),
        thematicBreak = MarkdownStyling.ThematicBreak.create(
            lineWidth = 1.dp,
            lineColor = PopupMarkdownDividerColor,
        ),
        htmlBlock = MarkdownStyling.HtmlBlock.create(
            textStyle = editorTextStyle,
            padding = PaddingValues(12.dp),
            shape = RoundedCornerShape(12.dp),
            background = PopupMarkdownBlockBackground,
            borderColor = PopupMarkdownDividerColor,
        ),
    )
}

private fun createPopupHeadingStyling(
    baseTextStyle: TextStyle,
    editorTextStyle: TextStyle
): MarkdownStyling.Heading {
    val headerPadding = PaddingValues(top = 12.dp)
    val h1 = baseTextStyle.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val h2 = baseTextStyle.copy(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
    val h3 = baseTextStyle.copy(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val h4 = baseTextStyle.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val h5 = baseTextStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val h6 = baseTextStyle.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    return MarkdownStyling.Heading.create(
        baseTextStyle = baseTextStyle,
        h1 = MarkdownStyling.Heading.H1.create(
            baseTextStyle = h1,
            inlinesStyling = createPopupInlinesStyling(h1, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
        h2 = MarkdownStyling.Heading.H2.create(
            baseTextStyle = h2,
            inlinesStyling = createPopupInlinesStyling(h2, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
        h3 = MarkdownStyling.Heading.H3.create(
            baseTextStyle = h3,
            inlinesStyling = createPopupInlinesStyling(h3, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
        h4 = MarkdownStyling.Heading.H4.create(
            baseTextStyle = h4,
            inlinesStyling = createPopupInlinesStyling(h4, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
        h5 = MarkdownStyling.Heading.H5.create(
            baseTextStyle = h5,
            inlinesStyling = createPopupInlinesStyling(h5, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
        h6 = MarkdownStyling.Heading.H6.create(
            baseTextStyle = h6,
            inlinesStyling = createPopupInlinesStyling(h6, editorTextStyle),
            underlineWidth = 0.dp,
            underlineGap = 0.dp,
            padding = headerPadding,
        ),
    )
}

private fun createPopupInlinesStyling(
    textStyle: TextStyle,
    editorTextStyle: TextStyle
): InlinesStyling =
    InlinesStyling.create(
        textStyle = textStyle,
        editorTextStyle = editorTextStyle,
        inlineCode = editorTextStyle.copy(
            color = PopupMarkdownTextColor,
            fontSize = textStyle.fontSize * 0.92f,
            background = PopupMarkdownInlineCodeBackground,
        ).toSpanStyle(),
        link = SpanStyle(color = PopupMarkdownLinkColor),
        linkDisabled = SpanStyle(color = PopupMarkdownLinkColor.copy(alpha = 0.5f)),
        linkHovered = SpanStyle(
            color = PopupMarkdownLinkColor,
            textDecoration = TextDecoration.Underline,
        ),
        linkFocused = SpanStyle(
            color = PopupMarkdownLinkColor,
            textDecoration = TextDecoration.Underline,
        ),
        linkPressed = SpanStyle(
            color = PopupMarkdownLinkColor.copy(alpha = 0.8f),
            textDecoration = TextDecoration.Underline,
        ),
        linkVisited = SpanStyle(color = PopupMarkdownLinkColor.copy(alpha = 0.85f)),
    )
