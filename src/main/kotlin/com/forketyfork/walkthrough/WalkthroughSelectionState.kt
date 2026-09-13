package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.SelectionModel

internal class WalkthroughSelectionState {
    private var ownedSelection: OwnedSelection? = null

    fun moveCaretToLine(editor: Editor, line: Int?, endLine: Int? = null) {
        clearOwnedSelection()
        if (line == null) return

        editor.caretModel.moveToLogicalPosition(LogicalPosition(line - 1, 0))
        applyLineRangeSelection(editor, line, endLine)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    fun applyLineRangeSelection(editor: Editor, line: Int?, endLine: Int?) {
        clearOwnedSelection()
        if (line == null || endLine == null) return

        val document = editor.document
        val startOffset = document.getLineStartOffset(line - 1)
        val endLineIndex = endLine - 1
        val endOffset = if (endLineIndex >= document.lineCount - 1) {
            document.getLineEndOffset(document.lineCount - 1)
        } else {
            document.getLineStartOffset(endLineIndex + 1)
        }
        editor.selectionModel.setSelection(startOffset, endOffset)
        ownedSelection = OwnedSelection(editor, startOffset, endOffset)
    }

    fun clearOwnedSelection() {
        val selection = ownedSelection ?: return
        val selectionModel = selection.editor.selectionModel
        if (isOwnedSelectionActive(selection, selectionModel)) {
            selection.editor.selectionModel.removeSelection()
        }
        ownedSelection = null
    }

    private fun isOwnedSelectionActive(selection: OwnedSelection, selectionModel: SelectionModel): Boolean {
        if (selection.editor.isDisposed || !selectionModel.hasSelection()) return false
        return selectionModel.selectionStart == selection.startOffset &&
            selectionModel.selectionEnd == selection.endOffset
    }

    private data class OwnedSelection(val editor: Editor, val startOffset: Int, val endOffset: Int)
}
