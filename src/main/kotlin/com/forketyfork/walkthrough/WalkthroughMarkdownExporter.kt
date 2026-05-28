package com.forketyfork.walkthrough

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TOP_LEVEL_HEADING_LEVEL = 2
private const val MAX_HEADING_LEVEL = 6

private val exportTimestampFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

/**
 * Renders a saved [WalkthroughRecord] as a standalone Markdown document so a walkthrough can be
 * shared outside the IDE — pasted into a pull request, an onboarding doc, or a wiki. Each item
 * becomes a section headed by its step label; inserted tangent answers nest under their parent
 * step via deeper heading levels. The item bodies are already Markdown and are emitted verbatim.
 */
internal fun renderWalkthroughMarkdown(record: WalkthroughRecord): String {
    val builder = StringBuilder()
    builder.append("# ").append(record.description.trim()).append("\n\n")
    builder.append("_Exported from Walkthrough · ")
        .append(exportTimestampFormatter.format(record.createdAtInstantOrEpoch()))
        .append("_\n")

    record.items.forEachIndexed { index, item ->
        val label = item.label?.takeIf { it.isNotBlank() } ?: (index + 1).toString()
        builder.append('\n')
        builder.append("#".repeat(headingLevelForLabel(label)))
            .append(" Step ")
            .append(label)
            .append("\n\n")
        itemLocation(item, record.targetKind)?.let { location ->
            builder.append(location).append("\n\n")
        }
        builder.append(item.text.trim()).append('\n')
    }

    return builder.toString()
}

private fun headingLevelForLabel(label: String): Int {
    val depth = label.count { character -> character == '.' }
    return (TOP_LEVEL_HEADING_LEVEL + depth).coerceAtMost(MAX_HEADING_LEVEL)
}

private fun itemLocation(item: WalkthroughItem, targetKind: WalkthroughTargetKind): String? = when (targetKind) {
    WalkthroughTargetKind.File -> fileLocation(item)
    WalkthroughTargetKind.Diff -> diffLocation(item)
}

private fun fileLocation(item: WalkthroughItem): String? {
    val file = item.file?.takeIf { it.isNotBlank() }
    val line = item.line
    return when {
        file != null && line != null -> "`$file:$line`"
        file != null -> "`$file`"
        line != null -> "Line $line"
        else -> null
    }
}

private fun diffLocation(item: WalkthroughItem): String? {
    val file = item.diffFile?.takeIf { it.isNotBlank() } ?: item.file?.takeIf { it.isNotBlank() }
    val details = buildList {
        add("diff")
        item.diffSide?.let { side ->
            add(if (side == DiffSide.Left) "left side" else "right side")
        }
        item.line?.let { line -> add("line $line") }
    }.joinToString(", ")
    return if (file != null) "`$file` ($details)" else "($details)"
}
