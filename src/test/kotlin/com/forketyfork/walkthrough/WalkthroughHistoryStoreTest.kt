package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class WalkthroughHistoryStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun saveListAndLoadRoundTripWalkthroughRecord() {
        val store = WalkthroughHistoryStore(
            directory = tempDir,
            clock = Clock.fixed(Instant.parse("2026-05-09T04:34:00Z"), ZoneOffset.UTC),
            randomSuffix = { "abc12345" }
        )
        val items = listOf(
            WalkthroughItem(
                text = "Explain popup placement",
                file = "src/main/kotlin/com/forketyfork/walkthrough/WalkthroughPopupPlacement.kt",
                line = 13
            )
        )

        val saved = store.save("Explain popup placement", items)

        assertEquals("20260509-043400-000-explain-popup-placement-abc12345", saved.id)
        assertEquals("2026-05-09T04:34:00Z", saved.createdAt)
        assertEquals("Explain popup placement", saved.description)
        assertEquals(items, saved.items)
        assertTrue(Files.isRegularFile(tempDir.resolve("${saved.id}.json")))
        assertEquals(listOf(saved), store.list())
        assertEquals(saved, store.load(saved.id))
    }

    @Test
    fun saveListAndLoadRoundTripDiffWalkthroughRecord() {
        val store = WalkthroughHistoryStore(
            directory = tempDir,
            clock = Clock.fixed(Instant.parse("2026-05-09T04:34:00Z"), ZoneOffset.UTC),
            randomSuffix = { "abc12345" }
        )
        val descriptors = listOf(
            DiffWalkthroughDescriptor(
                id = "popup-change",
                file = "src/Foo.kt",
                leftCommit = "1111111111111111111111111111111111111111",
                rightCommit = "2222222222222222222222222222222222222222"
            )
        )
        val items = listOf(
            WalkthroughItem(
                text = "Explain changed branch",
                line = 13,
                diffId = "popup-change",
                diffFile = "src/Foo.kt",
                diffSide = DiffSide.Right
            )
        )

        val saved = store.save(
            description = "Explain PR change",
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = descriptors,
            items = items
        )

        assertEquals(WalkthroughTargetKind.Diff, saved.targetKind)
        assertEquals(descriptors, saved.diffDescriptors)
        assertEquals(items, saved.items)
        assertEquals(saved, store.load(saved.id))
    }

    @Test
    fun listSortsNewestFirstAndSkipsCorruptFiles() {
        val corruptFiles = mutableListOf<Path>()
        val store = WalkthroughHistoryStore(
            directory = tempDir,
            onCorruptFile = { path, _ -> corruptFiles.add(path) }
        )
        val items = listOf(WalkthroughItem(text = "Step"))
        val older = WalkthroughRecord(
            id = "older",
            createdAt = "2026-05-09T04:30:00Z",
            description = "Older",
            items = items
        )
        val newer = WalkthroughRecord(
            id = "newer",
            createdAt = "2026-05-09T04:35:00Z",
            description = "Newer",
            items = items
        )
        store.save(older)
        store.save(newer)
        Files.writeString(tempDir.resolve("corrupt.json"), "{")

        assertEquals(listOf("newer", "older"), store.list().map { record -> record.id })
        assertEquals(listOf(tempDir.resolve("corrupt.json")), corruptFiles)
    }

    @Test
    fun loadReturnsNullForUnsafeIds() {
        val store = WalkthroughHistoryStore(directory = tempDir)

        assertEquals(null, store.load("../other-project"))
    }

    @Test
    fun slugifyKeepsFileNamesInspectable() {
        assertEquals("explain-popup-placement", slugifyDescription("Explain popup placement"))
        assertEquals("", slugifyDescription("!!!"))
    }

    @Test
    fun saveRejectsFileRecordCarryingDiffDescriptors() {
        val store = WalkthroughHistoryStore(directory = tempDir)
        val record = WalkthroughRecord(
            id = "20260509-043400-000-mismatched-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "Mismatched",
            targetKind = WalkthroughTargetKind.File,
            diffDescriptors = listOf(
                DiffWalkthroughDescriptor(
                    id = "stray",
                    file = "src/Foo.kt",
                    leftCommit = "1111111111111111111111111111111111111111",
                    rightCommit = "2222222222222222222222222222222222222222"
                )
            ),
            items = listOf(WalkthroughItem(text = "Step"))
        )

        assertThrows(com.google.gson.JsonParseException::class.java) { store.save(record) }
    }

    @Test
    fun saveRejectsDiffRecordWithoutDescriptors() {
        val store = WalkthroughHistoryStore(directory = tempDir)
        val record = WalkthroughRecord(
            id = "20260509-043400-000-empty-diff-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "Empty diff",
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = emptyList(),
            items = listOf(
                WalkthroughItem(
                    text = "Step",
                    line = 1,
                    diffId = "missing",
                    diffSide = DiffSide.Right
                )
            )
        )

        assertThrows(com.google.gson.JsonParseException::class.java) { store.save(record) }
    }

    @Test
    fun saveRejectsDiffDescriptorWithoutUsablePath() {
        val store = WalkthroughHistoryStore(directory = tempDir)
        val record = WalkthroughRecord(
            id = "20260509-043400-000-no-path-abc12345",
            createdAt = "2026-05-09T04:34:00Z",
            description = "No path",
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = listOf(
                DiffWalkthroughDescriptor(
                    id = "no-path",
                    leftCommit = "1111111111111111111111111111111111111111",
                    rightCommit = "2222222222222222222222222222222222222222"
                )
            ),
            items = listOf(
                WalkthroughItem(
                    text = "Step",
                    line = 1,
                    diffId = "no-path",
                    diffSide = DiffSide.Right
                )
            )
        )

        assertThrows(com.google.gson.JsonParseException::class.java) { store.save(record) }
    }

    @Test
    fun saveAcceptsDiffDescriptorWithOnlySideFiles() {
        val store = WalkthroughHistoryStore(
            directory = tempDir,
            clock = Clock.fixed(Instant.parse("2026-05-09T04:34:00Z"), ZoneOffset.UTC),
            randomSuffix = { "abc12345" }
        )
        val descriptors = listOf(
            DiffWalkthroughDescriptor(
                id = "renamed",
                leftFile = "src/Old.kt",
                rightFile = "src/New.kt",
                leftCommit = "1111111111111111111111111111111111111111",
                rightCommit = "2222222222222222222222222222222222222222"
            )
        )
        val items = listOf(
            WalkthroughItem(
                text = "Renamed file step",
                line = 1,
                diffId = "renamed",
                diffFile = "src/New.kt",
                diffSide = DiffSide.Right
            )
        )

        val saved = store.save(
            description = "Renamed",
            targetKind = WalkthroughTargetKind.Diff,
            diffDescriptors = descriptors,
            items = items
        )

        assertEquals(descriptors, saved.diffDescriptors)
        assertEquals(saved, store.load(saved.id))
    }
}
