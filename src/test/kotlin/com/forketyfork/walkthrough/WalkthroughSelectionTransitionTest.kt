package com.forketyfork.walkthrough

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.SelectionModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class WalkthroughSelectionTransitionTest {
    @Test
    fun nullAnchorClearsPreviousWalkthroughRange() {
        val editor = fakeEditor()
        val selectionState = WalkthroughSelectionState()

        selectionState.moveCaretToLine(editor, line = 2, endLine = 3)
        assertTrue(editor.selectionModel.hasSelection())
        assertEquals(4, editor.selectionModel.selectionStart)
        assertEquals(14, editor.selectionModel.selectionEnd)

        selectionState.moveCaretToLine(editor, line = null)

        assertFalse(editor.selectionModel.hasSelection())
    }

    @Test
    fun singleLineTransitionClearsPreviousWalkthroughRange() {
        val editor = fakeEditor()
        val selectionState = WalkthroughSelectionState()

        selectionState.moveCaretToLine(editor, line = 2, endLine = 3)
        selectionState.moveCaretToLine(editor, line = 4)

        assertFalse(editor.selectionModel.hasSelection())
    }

    @Test
    fun staleAnchorTransitionClearsPreviousWalkthroughRange() {
        val editor = fakeEditor()
        val selectionState = WalkthroughSelectionState()

        selectionState.moveCaretToLine(editor, line = 2, endLine = 3)
        selectionState.clearOwnedSelection()

        assertFalse(editor.selectionModel.hasSelection())
    }

    @Test
    fun crossEditorTransitionClearsPreviousWalkthroughRange() {
        val previousEditor = fakeEditor()
        val nextEditor = fakeEditor()
        val selectionState = WalkthroughSelectionState()

        selectionState.moveCaretToLine(previousEditor, line = 2, endLine = 3)
        selectionState.moveCaretToLine(nextEditor, line = null)

        assertFalse(previousEditor.selectionModel.hasSelection())
    }

    @Test
    fun userSelectionIsNotClearedWhenNoWalkthroughRangeIsOwned() {
        val editor = fakeEditor()
        editor.selectionModel.setSelection(0, 3)
        val selectionState = WalkthroughSelectionState()

        selectionState.moveCaretToLine(editor, line = 2)

        assertTrue(editor.selectionModel.hasSelection())
        assertTrue(editor.selectionModel.selectionStart == 0)
        assertTrue(editor.selectionModel.selectionEnd == 3)
    }

    @Test
    fun userModifiedSelectionIsNotClearedOnLaterTransition() {
        val editor = fakeEditor()
        val selectionState = WalkthroughSelectionState()
        selectionState.moveCaretToLine(editor, line = 2, endLine = 3)
        editor.selectionModel.setSelection(0, 3)

        selectionState.moveCaretToLine(editor, line = null)

        assertTrue(editor.selectionModel.hasSelection())
        assertTrue(editor.selectionModel.selectionStart == 0)
        assertTrue(editor.selectionModel.selectionEnd == 3)
    }

    private fun fakeEditor(): Editor {
        val document = proxy<Document>(::documentInvocation)
        val selection = FakeSelection()
        val selectionModel = proxy<SelectionModel>(selection::invoke)
        val caretModel = proxy<CaretModel> { method, _ -> defaultValue(method.returnType) }
        val scrollingModel = proxy<ScrollingModel> { method, _ -> defaultValue(method.returnType) }
        return proxy { method, _ ->
            when (method.name) {
                "getDocument" -> document
                "getSelectionModel" -> selectionModel
                "getCaretModel" -> caretModel
                "getScrollingModel" -> scrollingModel
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun documentInvocation(method: Method, arguments: Array<out Any>?): Any? = when (method.name) {
        "getLineCount" -> 4
        "getLineStartOffset" -> listOf(0, 4, 8, 14)[arguments!![0] as Int]
        "getLineEndOffset" -> listOf(3, 7, 13, 18)[arguments!![0] as Int]
        else -> defaultValue(method.returnType)
    }

    private class FakeSelection {
        var start = -1
        var end = -1

        fun invoke(method: Method, arguments: Array<out Any>?): Any? = when (method.name) {
            "hasSelection" -> start >= 0

            "getSelectionStart" -> start

            "getSelectionEnd" -> end

            "setSelection" -> {
                start = arguments!![0] as Int
                end = arguments[1] as Int
                null
            }

            "removeSelection" -> {
                start = -1
                end = -1
                null
            }

            else -> defaultValue(method.returnType)
        }
    }

    private inline fun <reified T> proxy(crossinline handler: (Method, Array<out Any>?) -> Any?): T =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
            InvocationHandler { _, method, arguments -> handler(method, arguments) },
        ) as T
}

private fun defaultValue(type: Class<*>): Any? = when (type) {
    Boolean::class.javaPrimitiveType -> false
    Int::class.javaPrimitiveType -> 0
    Long::class.javaPrimitiveType -> 0L
    Float::class.javaPrimitiveType -> 0f
    Double::class.javaPrimitiveType -> 0.0
    Short::class.javaPrimitiveType -> 0.toShort()
    Byte::class.javaPrimitiveType -> 0.toByte()
    Char::class.javaPrimitiveType -> '\u0000'
    else -> null
}
