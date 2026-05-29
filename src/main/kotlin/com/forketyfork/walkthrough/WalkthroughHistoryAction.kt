package com.forketyfork.walkthrough

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WalkthroughHistoryAction : AnAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        showWalkthroughHistoryPopup(project, event.dataContext, "Walkthrough History") { record ->
            replayWalkthrough(project, record, event.dataContext)
        }
    }
}

private fun replayWalkthrough(project: Project, record: WalkthroughRecord, dataContext: DataContext) {
    val shown = when (record.targetKind) {
        WalkthroughTargetKind.File -> showWalkthroughItems(project, record.items)

        WalkthroughTargetKind.Diff -> showDiffWalkthroughSession(
            project = project,
            descriptors = record.diffDescriptors,
            items = record.items,
            acceptsQuestions = false,
        ) != null
    }
    if (!shown) {
        JBPopupFactory.getInstance()
            .createMessage("No active editor for walkthrough replay")
            .showInBestPositionFor(dataContext)
    }
}

/**
 * Shows the saved walkthroughs for [project] in a searchable list popup. Selecting an entry invokes
 * [onSelect] with the chosen record. Shared by the replay and Markdown-export actions so both expose
 * the same history list.
 */
internal fun showWalkthroughHistoryPopup(
    project: Project,
    dataContext: DataContext,
    title: String,
    onSelect: (WalkthroughRecord) -> Unit,
) {
    val records = WalkthroughHistoryService.getInstance(project).list()
    if (records.isEmpty()) {
        JBPopupFactory.getInstance()
            .createMessage("No walkthrough history for this project")
            .showInBestPositionFor(dataContext)
        return
    }

    val actionGroup = DefaultActionGroup().apply {
        records.forEach { record ->
            add(SelectWalkthroughAction(record, onSelect))
        }
    }
    JBPopupFactory.getInstance()
        .createActionGroupPopup(
            title,
            actionGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
        )
        .showInBestPositionFor(dataContext)
}

private class SelectWalkthroughAction(
    private val record: WalkthroughRecord,
    private val onSelect: (WalkthroughRecord) -> Unit,
) : AnAction(formatRecord(record)) {
    override fun actionPerformed(event: AnActionEvent) {
        onSelect(record)
    }
}

private fun formatRecord(record: WalkthroughRecord): String {
    val timestamp = recordDisplayFormatter.format(record.createdAtInstantOrEpoch())
    return "$timestamp - ${record.description}"
}

private val recordDisplayFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())
