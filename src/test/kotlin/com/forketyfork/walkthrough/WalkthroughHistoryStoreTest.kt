package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
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
}
