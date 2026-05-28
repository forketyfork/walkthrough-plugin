package com.forketyfork.walkthrough

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

class WalkthroughExportAction : AnAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        showWalkthroughHistoryPopup(project, event.dataContext, "Export Walkthrough to Markdown") { record ->
            exportWalkthrough(project, record, event.dataContext)
        }
    }
}

private fun exportWalkthrough(project: Project, record: WalkthroughRecord, dataContext: DataContext) {
    val markdown = renderWalkthroughMarkdown(record)
    val descriptor = FileSaverDescriptor(
        "Export Walkthrough to Markdown",
        "Save the selected walkthrough as a Markdown document",
        "md",
    )
    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val baseDir = project.basePath?.let { basePath -> LocalFileSystem.getInstance().findFileByPath(basePath) }
    val defaultName = "${slugifyDescription(record.description).ifBlank { "walkthrough" }}.md"
    val wrapper = dialog.save(baseDir, defaultName) ?: return

    var exportedFile: VirtualFile? = null
    var failure: IOException? = null
    ApplicationManager.getApplication().runWriteAction {
        try {
            val file = wrapper.getVirtualFile(true)
            if (file != null) {
                VfsUtil.saveText(file, markdown)
                exportedFile = file
            }
        } catch (exception: IOException) {
            failure = exception
        }
    }

    val saved = exportedFile
    val caught = failure
    when {
        saved != null -> FileEditorManager.getInstance(project).openFile(saved, true)

        caught != null -> {
            LOG.warn("Failed to export walkthrough to Markdown", caught)
            JBPopupFactory.getInstance()
                .createMessage("Failed to export walkthrough: ${caught.message}")
                .showInBestPositionFor(dataContext)
        }
    }
}

private val LOG = Logger.getInstance(WalkthroughExportAction::class.java)
