package com.forketyfork.walkthrough

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
        val records = WalkthroughHistoryService.getInstance(project).list()
        if (records.isEmpty()) {
            JBPopupFactory.getInstance()
                .createMessage("No walkthrough history for this project")
                .showInBestPositionFor(event.dataContext)
            return
        }

        val actionGroup = DefaultActionGroup().apply {
            records.forEach { record ->
                add(ReplayWalkthroughAction(project, record))
            }
        }
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Walkthrough History",
                actionGroup,
                event.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            .showInBestPositionFor(event.dataContext)
    }
}

private class ReplayWalkthroughAction(
    private val project: Project,
    private val record: WalkthroughRecord
) : AnAction(formatRecord(record)) {
    override fun actionPerformed(event: AnActionEvent) {
        val shown = showWalkthroughItems(project, record.items)
        if (!shown) {
            JBPopupFactory.getInstance()
                .createMessage("No active editor for walkthrough replay")
                .showInBestPositionFor(event.dataContext)
        }
    }
}

private fun formatRecord(record: WalkthroughRecord): String {
    val timestamp = recordDisplayFormatter.format(record.createdAtInstantOrEpoch())
    return "$timestamp - ${record.description}"
}

private val recordDisplayFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())
