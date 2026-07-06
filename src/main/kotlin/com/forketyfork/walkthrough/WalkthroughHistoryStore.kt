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
    val targetKind: WalkthroughTargetKind?,
    val diffDescriptors: List<DiffWalkthroughDescriptorJson>?,
    val items: List<WalkthroughRecordItemJson>?,
)

private data class DiffWalkthroughDescriptorJson(
    val id: String?,
    val file: String?,
    val leftFile: String?,
    val rightFile: String?,
    val leftCommit: String?,
    val rightCommit: String?,
)

private data class WalkthroughRecordItemJson(
    val text: String?,
    val file: String?,
    val line: Int?,
    val diffId: String?,
    val diffFile: String?,
    val diffSide: DiffSide?,
    val label: String?,
    val parentLabel: String?,
)

internal sealed interface WalkthroughOverwriteResult {
    data class Success(val record: WalkthroughRecord) : WalkthroughOverwriteResult
    data object NotFound : WalkthroughOverwriteResult
    data class TargetKindMismatch(val existingKind: WalkthroughTargetKind) : WalkthroughOverwriteResult
    data object Failure : WalkthroughOverwriteResult
}

internal class WalkthroughHistoryStore(
    private val directory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val randomSuffix: () -> String = { UUID.randomUUID().toString().take(RANDOM_SUFFIX_LENGTH) },
    private val onCorruptFile: (Path, Exception) -> Unit = { _, _ -> },
) {
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    fun save(description: String, items: List<WalkthroughItem>): WalkthroughRecord = save(
        description = description,
        targetKind = WalkthroughTargetKind.File,
        diffDescriptors = emptyList(),
        items = items,
    )

    fun save(
        description: String,
        targetKind: WalkthroughTargetKind,
        diffDescriptors: List<DiffWalkthroughDescriptor>,
        items: List<WalkthroughItem>,
    ): WalkthroughRecord {
        require(description.isNotBlank()) { "description must not be blank" }
        require(items.isNotEmpty()) { "items must not be empty" }

        val createdAt = clock.instant()
        val record = WalkthroughRecord(
            id = createRecordId(createdAt, description),
            createdAt = createdAt.toString(),
            description = description,
            targetKind = targetKind,
            diffDescriptors = diffDescriptors,
            items = items,
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

    fun overwrite(
        historyId: String,
        description: String,
        targetKind: WalkthroughTargetKind,
        diffDescriptors: List<DiffWalkthroughDescriptor>,
        items: List<WalkthroughItem>,
    ): WalkthroughOverwriteResult {
        val existing = load(historyId)
        return when {
            existing == null -> WalkthroughOverwriteResult.NotFound

            existing.targetKind != targetKind -> WalkthroughOverwriteResult.TargetKindMismatch(existing.targetKind)

            else -> {
                val updated = existing.copy(
                    description = description,
                    diffDescriptors = diffDescriptors,
                    items = items,
                )
                save(updated)
                WalkthroughOverwriteResult.Success(updated)
            }
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
                            .thenByDescending { record -> record.id },
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

    private fun readRecordOrNull(path: Path): WalkthroughRecord? = try {
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
        val hasValidTargetKind = when (record.targetKind) {
            WalkthroughTargetKind.File -> record.diffDescriptors.isEmpty()
            WalkthroughTargetKind.Diff -> record.diffDescriptors.isNotEmpty()
        }
        val hasValidDescriptors = record.diffDescriptors.all { descriptor ->
            descriptor.id.isNotBlank() &&
                descriptor.leftCommit.isNotBlank() &&
                descriptor.rightCommit.isNotBlank() &&
                descriptor.hasUsablePath()
        }
        val hasValidFields = isSafeRecordId(record.id) &&
            record.description.isNotBlank() &&
            record.items.isNotEmpty() &&
            record.items.all { item -> item.text.isNotBlank() } &&
            hasValidTargetKind &&
            hasValidDescriptors

        if (!hasValidCreatedAt || !hasValidFields) {
            throw JsonParseException("Invalid walkthrough history record")
        }
    }
}

private fun DiffWalkthroughDescriptor.hasUsablePath(): Boolean {
    val hasSharedFile = !file.isNullOrBlank()
    val hasBothSideFiles = !leftFile.isNullOrBlank() && !rightFile.isNullOrBlank()
    return hasSharedFile || hasBothSideFiles
}

internal fun slugifyDescription(description: String): String = slugSeparatorPattern
    .replace(description.lowercase(Locale.US), "-")
    .trim('-')
    .take(MAX_SLUG_LENGTH)
    .trim('-')

private fun isSafeRecordId(id: String): Boolean = id.isNotBlank() && recordIdPattern.matches(id)

@Suppress("ComplexCondition")
private fun WalkthroughRecordJson.toRecord(): WalkthroughRecord? {
    val parsedId = id?.takeIf(::isSafeRecordId)
    val parsedCreatedAt = createdAt?.takeIf { value -> runCatching { Instant.parse(value) }.isSuccess }
    val parsedDescription = description?.takeIf { value -> value.isNotBlank() }
    val parsedTargetKind = targetKind ?: WalkthroughTargetKind.File
    val parsedDiffDescriptors = diffDescriptors
        ?.mapNotNull { descriptor -> descriptor.toDiffWalkthroughDescriptorOrNull() }
        .orEmpty()
    val parsedItems = items?.mapNotNull { item -> item.toWalkthroughItemOrNull() }
    // Single null-guard so Kotlin smart-casts the four locals to non-null at
    // the construction site below; splitting it would force `!!` everywhere.
    if (parsedId == null || parsedCreatedAt == null || parsedDescription == null || parsedItems == null) {
        return null
    }
    val hasMatchingItems = parsedItems.size == items.size && parsedItems.isNotEmpty()
    val hasMatchingDiffDescriptors = diffDescriptors == null ||
        parsedDiffDescriptors.size == diffDescriptors.size

    return if (hasMatchingItems && hasMatchingDiffDescriptors) {
        WalkthroughRecord(
            id = parsedId,
            createdAt = parsedCreatedAt,
            description = parsedDescription,
            targetKind = parsedTargetKind,
            diffDescriptors = parsedDiffDescriptors,
            items = parsedItems,
        )
    } else {
        null
    }
}

private fun DiffWalkthroughDescriptorJson.toDiffWalkthroughDescriptorOrNull(): DiffWalkthroughDescriptor? {
    val parsedId = id?.takeIf { it.isNotBlank() }
    val parsedLeftCommit = leftCommit?.takeIf { it.isNotBlank() }
    val parsedRightCommit = rightCommit?.takeIf { it.isNotBlank() }
    return if (parsedId != null && parsedLeftCommit != null && parsedRightCommit != null) {
        DiffWalkthroughDescriptor(
            id = parsedId,
            file = file?.takeIf { it.isNotBlank() },
            leftFile = leftFile?.takeIf { it.isNotBlank() },
            rightFile = rightFile?.takeIf { it.isNotBlank() },
            leftCommit = parsedLeftCommit,
            rightCommit = parsedRightCommit,
        )
    } else {
        null
    }
}

private fun WalkthroughRecordItemJson.toWalkthroughItemOrNull(): WalkthroughItem? = text
    ?.takeIf { value -> value.isNotBlank() }
    ?.let { value ->
        WalkthroughItem(
            text = value,
            file = file,
            line = line,
            diffId = diffId?.takeIf { it.isNotBlank() },
            diffFile = diffFile?.takeIf { it.isNotBlank() },
            diffSide = diffSide,
            label = label?.takeIf { it.isNotBlank() },
            parentLabel = parentLabel?.takeIf { it.isNotBlank() },
        )
    }
