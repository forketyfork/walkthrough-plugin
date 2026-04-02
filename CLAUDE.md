# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

This is an IntelliJ IDEA plugin built with the [IntelliJ Platform Gradle Plugin v2](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

```bash
# Build the plugin
./gradlew buildPlugin

# Run the plugin in a sandboxed IDE instance
./gradlew runIde

# Verify the plugin (compatibility checks)
./gradlew verifyPlugin
```

There are no unit tests in this project. The plugin is tested by running it in the IDE via `runIde`.

## Architecture

The plugin targets IntelliJ IDEA 252+ and uses **Jetbrains Compose** (via the Jewel library) for all UI. There is no Swing UI code — all panels and composables use `JewelComposePanel` as the Swing bridge.

### Key classes

- **`WalkthroughItem.kt`** — The shared core: `showWalkthroughItems(project, editor, items)` creates and positions a `JBPopup` with a Compose panel above the current caret line. `WalkthroughItemContent` is the composable inside the popup. Both are used by the action and the MCP toolset.

- **`ShowWalkthroughItemAction`** — An `AnAction` (shortcut `Ctrl+Shift+X`, also in the editor context menu) that calls `showWalkthroughItems` with a fixed walkthrough item.

- **`ShowWalkthroughItemsToolset`** — An MCP toolset (`McpToolset`) exposing one tool `show_walkthrough_items(items)` to MCP clients (e.g., Claude Desktop). Gets the active project from the coroutine context via `projectOrNull`, dispatches to the EDT via `withContext(Dispatchers.EDT)`, and calls the same `showWalkthroughItems` function.

### MCP server integration

The plugin depends on the bundled `com.intellij.mcpServer` plugin. Toolsets are registered in `plugin.xml` under `defaultExtensionNs="com.intellij.mcpServer"` with the `<mcpToolset>` extension point. Tool methods are discovered by reflection: annotate a suspend method with `@McpTool` and `@McpDescription`; annotate each parameter with `@McpDescription`. Use `mcpFail(message)` to return an error response.

Key API notes:
- Get the active project via `currentCoroutineContext().projectOrNull` (import `com.intellij.mcpserver.projectOrNull`) — `getProjectOrNull` is the Java getter name; the Kotlin property is `projectOrNull`.
- Dispatch to the EDT via `withContext(Dispatchers.EDT)` — `EDT` (imported from `com.intellij.openapi.application`) is an extension property on `Dispatchers`, not a standalone value.

### IntelliJ platform dependency

`build.gradle.kts` resolves IntelliJ IDEA `2025.3.4` through the IntelliJ Platform Gradle Plugin, so the project does not depend on a machine-specific local IDE path.
