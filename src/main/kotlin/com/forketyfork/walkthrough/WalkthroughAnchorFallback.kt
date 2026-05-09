package com.forketyfork.walkthrough

internal fun isResolvableWalkthroughLine(line: Int?, lineCount: Int): Boolean =
    line == null || line in 1..lineCount.coerceAtLeast(1)

internal fun WalkthroughItem.withFallbackAnchor(): WalkthroughItem =
    copy(line = null)
