package com.forketyfork.walkthrough

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Path

class WalkthroughHistoryService(private val project: Project) {
    private val store: WalkthroughHistoryStore? by lazy {
        project.basePath?.let { basePath ->
            WalkthroughHistoryStore(
                directory = Path.of(basePath, ".idea", "walkthroughs"),
                onCorruptFile = { path, exception ->
                    LOG.warn("Skipping corrupt walkthrough history file: $path", exception)
                }
            )
        }
    }

    fun save(description: String, items: List<WalkthroughItem>): WalkthroughRecord? =
        runHistoryOperation(operation = "save walkthrough history", fallback = null) {
            store?.save(description, items)
        }

    fun list(): List<WalkthroughRecord> =
        runHistoryOperation(operation = "list walkthrough history", fallback = emptyList()) {
            store?.list().orEmpty()
        }

    fun load(id: String): WalkthroughRecord? =
        runHistoryOperation(operation = "load walkthrough history", fallback = null) {
            store?.load(id)
        }

    private fun <T> runHistoryOperation(operation: String, fallback: T, block: () -> T): T =
        runCatching(block).getOrElse { exception ->
            LOG.warn("Failed to $operation", exception)
            fallback
        }

    companion object {
        private val LOG = Logger.getInstance(WalkthroughHistoryService::class.java)

        fun getInstance(project: Project): WalkthroughHistoryService =
            project.getService(WalkthroughHistoryService::class.java)
    }
}
