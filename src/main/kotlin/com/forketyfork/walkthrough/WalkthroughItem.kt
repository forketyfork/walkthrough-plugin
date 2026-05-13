package com.forketyfork.walkthrough

import java.awt.Dimension

internal object WalkthroughPopupLayout {
    val fallbackSize = Dimension(560, 300)
    const val MINIMUM_WIDTH_PX = 520
    const val MINIMUM_HEIGHT_PX = 260
    const val VIEWPORT_PADDING = 18
    const val LINE_SPACING = 10
}

data class WalkthroughItem(
    val text: String,
    val file: String? = null,
    val line: Int? = null,
    val label: String? = null,
    val parentLabel: String? = null
)
