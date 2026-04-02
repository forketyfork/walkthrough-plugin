package com.forketyfork.walkthrough

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ShowWalkthroughItemAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        showWalkthroughItems(
            project, editor, listOf(
                WalkthroughItem(
                    $$"""## Walkthrough

This popup supports **rich Markdown** formatting:

- **Bold text** and *italic text*
- `inline code` with highlighting
- Fenced code blocks with scrolling
- Bullet and numbered lists

```kotlin
fun greet(name: String) {
    println("Hello, $name!")
}
```

---

Use the MCP tool `show_walkthrough_items` to create **interactive code tours** with formatted content."""
                )
            )
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }
}
