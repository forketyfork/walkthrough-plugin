package com.forketyfork.walkthrough

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType

internal fun moveEditorCaretToLine(editor: Editor, line: Int?) {
    if (line == null) return

    editor.caretModel.moveToLogicalPosition(LogicalPosition(line - 1, 0))
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
}
