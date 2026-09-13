package com.forketyfork.walkthrough

import java.nio.file.Path

internal fun isResolvableWalkthroughLine(line: Int?, lineCount: Int): Boolean =
    line == null || line in 1..lineCount.coerceAtLeast(1)

internal fun isResolvableWalkthroughEndLine(line: Int?, endLine: Int?, lineCount: Int): Boolean =
    endLine == null || (line != null && endLine >= line && endLine in 1..lineCount.coerceAtLeast(1))

internal fun WalkthroughItem.withResolvedEndLine(lineCount: Int): WalkthroughItem =
    if (isResolvableWalkthroughEndLine(line, endLine, lineCount)) this else copy(endLine = null)

internal fun WalkthroughItem.withFallbackAnchor(): WalkthroughItem =
    copy(file = null, line = null, endLine = null, diffId = null, diffFile = null, diffSide = null)

internal fun resolveProjectRelativeWalkthroughPath(basePath: String?, relativePath: String): Path? = runCatching {
    val base = basePath
        ?.takeIf { value -> value.isNotBlank() }
        ?.let { value -> Path.of(value).toAbsolutePath().normalize() }
        ?: return@runCatching null
    val candidate = Path.of(relativePath)
    if (candidate.isAbsolute) return@runCatching null

    base.resolve(candidate)
        .normalize()
        .takeIf { path -> path.startsWith(base) }
}.getOrNull()
