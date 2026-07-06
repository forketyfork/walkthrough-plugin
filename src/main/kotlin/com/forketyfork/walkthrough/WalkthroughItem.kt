package com.forketyfork.walkthrough

import java.awt.Dimension

enum class WalkthroughTargetKind {
    File,
    Diff,
}

enum class DiffSide {
    Left,
    Right,
}

data class DiffWalkthroughDescriptor(
    val id: String,
    val file: String? = null,
    val leftFile: String? = null,
    val rightFile: String? = null,
    val leftCommit: String,
    val rightCommit: String,
)

internal object WalkthroughPopupLayout {
    val fallbackSize = Dimension(560, 360)
    const val MINIMUM_WIDTH_PX = 520
    const val MINIMUM_HEIGHT_PX = 260
    const val VIEWPORT_PADDING = 18
    const val LINE_SPACING = 20
}

data class WalkthroughItem(
    val text: String,
    val file: String? = null,
    val line: Int? = null,
    val endLine: Int? = null,
    val diffId: String? = null,
    val diffFile: String? = null,
    val diffSide: DiffSide? = null,
    val label: String? = null,
    val parentLabel: String? = null,
)
