package com.forketyfork.walkthrough

import java.time.Instant

data class WalkthroughRecord(
    val id: String,
    val createdAt: String,
    val description: String,
    val items: List<WalkthroughItem>
)

internal fun WalkthroughRecord.createdAtInstantOrEpoch(): Instant =
    runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.EPOCH)
