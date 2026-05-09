package com.forketyfork.walkthrough

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val MAX_SLUG_LENGTH = 48
private const val RANDOM_SUFFIX_LENGTH = 8
private const val RECORD_EXTENSION = ".json"
private const val INDEX_FILE_NAME = "index.json"
private val recordIdPattern = Regex("[A-Za-z0-9._-]+")
private val slugSeparatorPattern = Regex("[^a-z0-9]+")
private val recordTimestampFormatter = DateTimeFormatter
    .ofPattern("yyyyMMdd-HHmmss-SSS")
    .withZone(ZoneOffset.UTC)

private data class WalkthroughRecordJson(
    val id: String?,
    val createdAt: String?,
    val description: String?,
    val items: List<WalkthroughRecordItemJson>?
)

private data class WalkthroughRecordItemJson(
    val text: String?,
    val file: String?,
    val line: Int?
)

internal class WalkthroughHistoryStore(
    private val directory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val randomSuffix: () -> String = { UUID.randomUUID().toString().take(RANDOM_SUFFIX_LENGTH) },
    private val onCorruptFile: (Path, Exception) -> Unit = { _, _ -> }
) {
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun save(description: String, items: List<WalkthroughItem>): WalkthroughRecord {
        require(description.isNotBlank()) { "description must not be blank" }
        require(items.isNotEmpty()) { "items must not be empty" }

        val createdAt = clock.instant()
        val record = WalkthroughRecord(
            id = createRecordId(createdAt, description),
            createdAt = createdAt.toString(),
            description = description,
            items = items
        )
        save(record)
        return record
    }

    fun save(record: WalkthroughRecord) {
        validateRecord(record)
        Files.createDirectories(directory)
        val target = recordPath(record.id)
        val temporary = directory.resolve("${record.id}.tmp")
        Files.writeString(temporary, gson.toJson(record))
        try {
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, REPLACE_EXISTING)
        }
    }

    fun list(): List<WalkthroughRecord> {
        if (!Files.isDirectory(directory)) return emptyList()

        return try {
            Files.list(directory).use { paths ->
                paths
                    .filter(::isRecordFile)
                    .map { path -> readRecordOrNull(path) }
                    .toList()
                    .filterNotNull()
                    .sortedWith(
                        compareByDescending<WalkthroughRecord> { record -> record.createdAtInstantOrEpoch() }
                            .thenByDescending { record -> record.id }
                    )
            }
        } catch (exception: DirectoryIteratorException) {
            onCorruptFile(directory, exception)
            emptyList()
        } catch (exception: IOException) {
            onCorruptFile(directory, exception)
            emptyList()
        } catch (exception: SecurityException) {
            onCorruptFile(directory, exception)
            emptyList()
        } catch (exception: UncheckedIOException) {
            onCorruptFile(directory, exception)
            emptyList()
        }
    }

    fun load(id: String): WalkthroughRecord? {
        val path = id
            .takeIf(::isSafeRecordId)
            ?.let(::recordPath)
            ?.takeIf { candidate -> Files.isRegularFile(candidate) }
        return path?.let(::readRecordOrNull)
    }

    private fun createRecordId(createdAt: Instant, description: String): String {
        val timestamp = recordTimestampFormatter.format(createdAt)
        val slug = slugifyDescription(description).ifBlank { "walkthrough" }
        val suffix = randomSuffix().takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().take(RANDOM_SUFFIX_LENGTH)
        return "$timestamp-$slug-$suffix"
    }

    private fun recordPath(id: String): Path = directory.resolve("$id$RECORD_EXTENSION")

    private fun isRecordFile(path: Path): Boolean {
        val fileName = path.fileName.toString()
        return Files.isRegularFile(path) &&
            fileName.endsWith(RECORD_EXTENSION) &&
            fileName != INDEX_FILE_NAME
    }

    private fun readRecordOrNull(path: Path): WalkthroughRecord? =
        try {
            gson.fromJson(Files.readString(path), WalkthroughRecordJson::class.java)
                ?.toRecord()
                ?: throw JsonParseException("Invalid walkthrough history record")
        } catch (exception: JsonParseException) {
            onCorruptFile(path, exception)
            null
        } catch (exception: IOException) {
            onCorruptFile(path, exception)
            null
        } catch (exception: IllegalStateException) {
            onCorruptFile(path, exception)
            null
        }

    private fun validateRecord(record: WalkthroughRecord) {
        val hasValidCreatedAt = runCatching { Instant.parse(record.createdAt) }.isSuccess
        val hasValidFields = isSafeRecordId(record.id) &&
            record.description.isNotBlank() &&
            record.items.isNotEmpty() &&
            record.items.all { item -> item.text.isNotBlank() }

        if (!hasValidCreatedAt || !hasValidFields) {
            throw JsonParseException("Invalid walkthrough history record")
        }
    }
}

internal fun slugifyDescription(description: String): String =
    slugSeparatorPattern
        .replace(description.lowercase(Locale.US), "-")
        .trim('-')
        .take(MAX_SLUG_LENGTH)
        .trim('-')

private fun isSafeRecordId(id: String): Boolean =
    id.isNotBlank() && recordIdPattern.matches(id)

private fun WalkthroughRecordJson.toRecord(): WalkthroughRecord? {
    val parsedId = id?.takeIf(::isSafeRecordId)
    val parsedCreatedAt = createdAt?.takeIf { value -> runCatching { Instant.parse(value) }.isSuccess }
    val parsedDescription = description?.takeIf { value -> value.isNotBlank() }
    val parsedItems = items?.mapNotNull { item -> item.toWalkthroughItemOrNull() }
    val hasRequiredFields = parsedId != null && parsedCreatedAt != null && parsedDescription != null
    val hasMatchingItems = parsedItems != null && parsedItems.size == items.size && parsedItems.isNotEmpty()

    return if (hasRequiredFields && hasMatchingItems) {
        WalkthroughRecord(
            id = parsedId.orEmpty(),
            createdAt = parsedCreatedAt.orEmpty(),
            description = parsedDescription.orEmpty(),
            items = parsedItems.orEmpty()
        )
    } else {
        null
    }
}

private fun WalkthroughRecordItemJson.toWalkthroughItemOrNull(): WalkthroughItem? =
    text
        ?.takeIf { value -> value.isNotBlank() }
        ?.let { value -> WalkthroughItem(text = value, file = file, line = line) }
