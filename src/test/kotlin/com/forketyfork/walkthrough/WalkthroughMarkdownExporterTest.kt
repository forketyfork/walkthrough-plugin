package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughMarkdownExporterTest {
    @Test
    fun rendersTitleNestedStepsAndFileLocations() {
        val record = WalkthroughRecord(
            id = "20260509-043400-000-auth-tour-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "How authentication works",
            items = listOf(
                WalkthroughItem(
                    text = "Requests enter through the filter.",
                    file = "src/Auth.kt",
                    line = 12,
                    label = "1",
                ),
                WalkthroughItem(
                    text = "The token is validated here.",
                    file = "src/Token.kt",
                    label = "1.1",
                    parentLabel = "1",
                ),
                WalkthroughItem(
                    text = "Finally the session is created.",
                    label = "2",
                ),
            ),
        )

        val markdown = renderWalkthroughMarkdown(record)
        val body = markdown.substringAfter("_\n")

        assertTrue(markdown.startsWith("# How authentication works\n\n"))
        assertEquals(
            """

            ## Step 1

            `src/Auth.kt:12`

            Requests enter through the filter.

            ### Step 1.1

            `src/Token.kt`

            The token is validated here.

            ## Step 2

            Finally the session is created.

            """.trimIndent(),
            body,
        )
    }

    @Test
    fun rendersDiffLocationsWithSideAndLine() {
        val record = WalkthroughRecord(
            id = "20260509-043400-000-pr-review-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "Review the change",
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = listOf(
                DiffWalkthroughDescriptor(
                    id = "change",
                    file = "src/Foo.kt",
                    leftCommit = "1111111111111111111111111111111111111111",
                    rightCommit = "2222222222222222222222222222222222222222",
                ),
            ),
            items = listOf(
                WalkthroughItem(
                    text = "This line moved.",
                    line = 20,
                    diffId = "change",
                    diffFile = "src/Foo.kt",
                    diffSide = DiffSide.Right,
                    label = "1",
                ),
            ),
        )

        val markdown = renderWalkthroughMarkdown(record)

        assertTrue(markdown.contains("## Step 1\n\n`src/Foo.kt` (diff, right side, line 20)\n\nThis line moved.\n"))
    }

    @Test
    fun preservesSignificantLeadingAndTrailingBodyWhitespace() {
        val record = WalkthroughRecord(
            id = "20260509-043400-000-code-block-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "Indented code block",
            items = listOf(
                WalkthroughItem(
                    text = "    val x = 1\n    val y = 2\n",
                    label = "1",
                ),
            ),
        )

        val markdown = renderWalkthroughMarkdown(record)

        // The indented code block (leading spaces) must survive, and the trailing newline must
        // not be doubled even though the body already ends with one.
        assertTrue(markdown.endsWith("## Step 1\n\n    val x = 1\n    val y = 2\n"))
    }

    @Test
    fun fallsBackToSequentialNumbersWhenLabelsAreMissing() {
        val record = WalkthroughRecord(
            id = "20260509-043400-000-unlabeled-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "Unlabeled tour",
            items = listOf(
                WalkthroughItem(text = "First step."),
                WalkthroughItem(text = "Second step."),
            ),
        )

        val markdown = renderWalkthroughMarkdown(record)

        assertTrue(markdown.contains("## Step 1\n\nFirst step.\n"))
        assertTrue(markdown.contains("## Step 2\n\nSecond step.\n"))
        // No location line should be emitted when the item has no file or line.
        assertFalse(markdown.contains("``"))
    }
}
