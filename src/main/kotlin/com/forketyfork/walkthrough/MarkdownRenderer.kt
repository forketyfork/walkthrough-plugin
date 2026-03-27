package com.forketyfork.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text

// ── Data model ──────────────────────────────────────────────────────────────

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val inlineText: String) : MarkdownBlock()
    data class Paragraph(val inlineText: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class NumberedList(val items: List<String>) : MarkdownBlock()
    data object HorizontalRule : MarkdownBlock()
}

// ── Block parser ────────────────────────────────────────────────────────────

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        when {
            // Fenced code block
            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim().ifEmpty { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
                if (i < lines.size) i++ // skip closing ```
            }

            // Heading
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                val text = trimmed.drop(level).trimStart()
                blocks.add(MarkdownBlock.Heading(level, text))
                i++
            }

            // Horizontal rule (---, ***, ___)
            trimmed.matches(Regex("^[-*_]{3,}$")) && trimmed.isNotBlank() -> {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
            }

            // Bullet list item (- or *)
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trimStart()
                    if (l.startsWith("- ") || l.startsWith("* ")) {
                        items.add(l.drop(2))
                    } else if (l.isNotEmpty() && items.isNotEmpty()) {
                        items[items.lastIndex] = items.last() + " " + l
                    } else {
                        break
                    }
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(items))
            }

            // Numbered list item (1. 2. etc.)
            trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trimStart()
                    val match = Regex("^\\d+\\.\\s(.*)").matchEntire(l)
                    if (match != null) {
                        items.add(match.groupValues[1])
                    } else if (l.isNotEmpty() && items.isNotEmpty()) {
                        items[items.lastIndex] = items.last() + " " + l
                    } else {
                        break
                    }
                    i++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
            }

            // Empty line — skip
            trimmed.isEmpty() -> i++

            // Paragraph — consecutive non-empty, non-special lines
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val lt = l.trimStart()
                    if (lt.isEmpty() || lt.startsWith("#") || lt.startsWith("```") ||
                        lt.startsWith("- ") || lt.startsWith("* ") ||
                        lt.matches(Regex("^\\d+\\.\\s.*")) ||
                        lt.matches(Regex("^[-*_]{3,}$"))
                    ) break
                    paragraphLines.add(l)
                    i++
                }
                blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
            }
        }
    }
    return blocks
}

// ── Inline parser ───────────────────────────────────────────────────────────

private val mdTextColor = Color(0xFFF8FAFC)
private val mdCodeColor = Color(0xFFE879F9)
private val mdCodeBgColor = Color(0x33E879F9)
private val mdLinkColor = Color(0xFF60A5FA)

private fun parseInlineContent(
    text: String,
    baseFontSize: Float = 15f,
    baseWeight: FontWeight = FontWeight.Normal
): AnnotatedString = buildAnnotatedString {
    pushStyle(
        SpanStyle(
            color = mdTextColor,
            fontSize = baseFontSize.sp,
            fontWeight = baseWeight
        )
    )

    var pos = 0
    while (pos < text.length) {
        when {
            // Inline code: `code`
            text[pos] == '`' && pos + 1 < text.length -> {
                val end = text.indexOf('`', pos + 1)
                if (end > pos) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = mdCodeColor,
                            background = mdCodeBgColor,
                            fontSize = (baseFontSize - 1).sp
                        )
                    )
                    append("\u2009${text.substring(pos + 1, end)}\u2009")
                    pop()
                    pos = end + 1
                } else {
                    append(text[pos])
                    pos++
                }
            }

            // Bold + Italic: ***text***
            text.startsWith("***", pos) -> {
                val end = text.indexOf("***", pos + 3)
                if (end > pos) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    appendInlineSegment(text.substring(pos + 3, end), baseFontSize)
                    pop()
                    pos = end + 3
                } else {
                    append(text[pos])
                    pos++
                }
            }

            // Bold: **text**
            text.startsWith("**", pos) -> {
                val end = text.indexOf("**", pos + 2)
                if (end > pos) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendInlineSegment(text.substring(pos + 2, end), baseFontSize)
                    pop()
                    pos = end + 2
                } else {
                    append(text[pos])
                    pos++
                }
            }

            // Italic: *text* (not preceded by space-star which is a list)
            text[pos] == '*' && pos + 1 < text.length && text[pos + 1] != ' ' -> {
                val end = text.indexOf('*', pos + 1)
                if (end > pos + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInlineSegment(text.substring(pos + 1, end), baseFontSize)
                    pop()
                    pos = end + 1
                } else {
                    append(text[pos])
                    pos++
                }
            }

            // Link: [text](url)
            text[pos] == '[' -> {
                val closeBracket = text.indexOf(']', pos + 1)
                if (closeBracket > pos && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket) {
                        val linkText = text.substring(pos + 1, closeBracket)
                        pushStyle(SpanStyle(color = mdLinkColor, fontWeight = FontWeight.Medium))
                        append(linkText)
                        pop()
                        pos = closeParen + 1
                    } else {
                        append(text[pos])
                        pos++
                    }
                } else {
                    append(text[pos])
                    pos++
                }
            }

            else -> {
                append(text[pos])
                pos++
            }
        }
    }

    pop() // base style
}

/**
 * Handles inline code within bold/italic spans — processes backtick-delimited
 * code inside an already-styled segment.
 */
private fun AnnotatedString.Builder.appendInlineSegment(text: String, baseFontSize: Float) {
    var pos = 0
    while (pos < text.length) {
        if (text[pos] == '`') {
            val end = text.indexOf('`', pos + 1)
            if (end > pos) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = mdCodeColor,
                        background = mdCodeBgColor,
                        fontSize = (baseFontSize - 1).sp
                    )
                )
                append("\u2009${text.substring(pos + 1, end)}\u2009")
                pop()
                pos = end + 1
            } else {
                append(text[pos])
                pos++
            }
        } else {
            append(text[pos])
            pos++
        }
    }
}

// ── Composables ─────────────────────────────────────────────────────────────

@Composable
internal fun MarkdownContent(markdown: String) {
    val blocks = parseMarkdownBlocks(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block)
                is MarkdownBlock.CodeBlock -> CodeBlockComposable(block)
                is MarkdownBlock.BulletList -> BulletListBlock(block)
                is MarkdownBlock.NumberedList -> NumberedListBlock(block)
                is MarkdownBlock.HorizontalRule -> HorizontalRuleBlock()
            }
        }
    }
}

@Composable
private fun HeadingBlock(heading: MarkdownBlock.Heading) {
    val (fontSize, weight) = when (heading.level) {
        1 -> 22f to FontWeight.Bold
        2 -> 19f to FontWeight.Bold
        3 -> 17f to FontWeight.SemiBold
        else -> 16f to FontWeight.SemiBold
    }
    Text(
        text = parseInlineContent(heading.inlineText, baseFontSize = fontSize, baseWeight = weight),
        lineHeight = (fontSize + 8).sp
    )
}

@Composable
private fun ParagraphBlock(paragraph: MarkdownBlock.Paragraph) {
    Text(
        text = parseInlineContent(paragraph.inlineText),
        lineHeight = 22.sp
    )
}

@Composable
private fun CodeBlockComposable(codeBlock: MarkdownBlock.CodeBlock) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33000000))
            .padding(12.dp)
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = codeBlock.code,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun BulletListBlock(list: MarkdownBlock.BulletList) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (item in list.items) {
            Row {
                Text(
                    text = "  \u2022  ",
                    color = Color(0xFFA855F7),
                    fontSize = 15.sp
                )
                Text(
                    text = parseInlineContent(item),
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun NumberedListBlock(list: MarkdownBlock.NumberedList) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((index, item) in list.items.withIndex()) {
            Row {
                Text(
                    text = "  ${index + 1}.  ",
                    color = Color(0xFF60A5FA),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = parseInlineContent(item),
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun HorizontalRuleBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFA855F7),
                        Color(0xFFE879F9),
                        Color(0xFF60A5FA),
                        Color.Transparent
                    )
                )
            )
    )
}
