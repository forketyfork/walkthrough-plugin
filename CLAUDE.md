# CLAUDE.md (symlinked as AGENTS.md)

[Canonical file: `CLAUDE.md`. Keep `AGENTS.md` as a symlink to `CLAUDE.md`.]

## Project

**Name:** Walkthrough Plugin
**Description:** IntelliJ IDEA plugin for presenting inline walkthrough guidance inside the editor.
Shows styled popups near target lines with a connector anchored to the line, and lets the user
step through a sequence of walkthrough items. Built with JetBrains Compose via the Jewel library.
**Stack:** Kotlin 2.3.21, JetBrains Compose (Jewel), IntelliJ Platform Gradle Plugin v2, Detekt
**Status:** Active development

## Build & Run

```bash
# Environment bootstrap
# Prerequisites: Nix (https://nixos.org/download), direnv (https://direnv.net)
direnv allow          # or: nix develop

# Build
just build            # or: ./gradlew buildPlugin

# Run in a sandboxed IDE instance
just run              # or: ./gradlew runIde

# Verify plugin compatibility
just verify           # or: ./gradlew verifyPlugin

# Lint (Detekt static analysis)
just lint             # or: ./gradlew detekt

# Run unit tests
just test             # or: ./gradlew test

# Clean
just clean            # or: ./gradlew clean

# Publish to JetBrains Marketplace
just publish          # or: ./gradlew publishPlugin

# Install pre-commit hooks (inside the Nix dev shell)
just hooks
```

The plugin is also tested by running it in the IDE via `runIde`.

## Infrastructure

- **Source code hosting:** GitHub — URL: `https://github.com/forketyfork/walkthrough-plugin` — Skill: `managing-github`
- **Issue tracker:** GitHub Issues — URL: `https://github.com/forketyfork/walkthrough-plugin/issues` — Skill: `managing-github`
- **CI/CD:** GitHub Actions — configs: `.github/workflows/build.yml`, `.github/workflows/release.yml`
- **Issue/PR linkage convention:** Reference issues in PR descriptions using `Closes #<number>` or
  `Fixes #<number>` to auto-close on merge. Include the issue number in the PR title as `(#<number>)`.

## Architecture

The plugin targets IntelliJ IDEA 261+ and uses **JetBrains Compose** (via the Jewel library) for
the walkthrough content UI. Compose content is hosted through `JewelComposePanel`, while
layered-pane integration such as popup hosting and connector painting lives in small Swing helper
components.

### Key classes

- **`WalkthroughItem.kt`** — The `WalkthroughItem` data class and `WalkthroughPopupLayout` layout
  constants.

- **`WalkthroughPopupContent.kt` / `WalkthroughPopupWidgets.kt`** — The Jewel Compose popup
  content, markdown body, navigation controls, source navigation button, and follow-up question
  input.

- **`WalkthroughOrchestrator.kt`** — The entry point: `showWalkthroughItems(project, editor, items)`
  creates and positions the walkthrough UI via `WalkthroughPopupSurface`, hosted on the editor
  layered pane above the current caret line. Used by the MCP toolset and history replay action.

- **`WalkthroughPopupSurface.kt`** — The Swing host that owns the popup surface inside the editor
  layered pane, renders the connector on the same surface as the popup content, and keeps the UI
  aligned with editor scrolling and resizing.

- **`WalkthroughSessionRegistry.kt`** — Tracks the active walkthrough session, stable step labels,
  dismissed sessions, follow-up questions, loading state, and inserted child steps.

- **`WalkthroughHistoryService.kt` / `WalkthroughHistoryStore.kt`** — Store and load per-project
  walkthrough history from `.idea/walkthroughs/`.

- **`WalkthroughSettings.kt` / `WalkthroughSettingsConfigurable.kt` / `WalkthroughPalette.kt`** —
  Persist and expose the user-selectable popup color palettes.

- **`ShowWalkthroughItemsToolset`** — An MCP toolset (`McpToolset`) exposing
  `show_walkthrough_items`, `await_walkthrough_question`, and `insert_walkthrough_tangents` to MCP
  clients. Gets the active project from the coroutine context via `projectOrNull`, dispatches UI
  changes to the EDT via `withContext(Dispatchers.EDT)`, and calls the same walkthrough session
  code used by local actions.

### MCP server integration

The plugin depends on the bundled `com.intellij.mcpServer` plugin. Toolsets are registered in
`plugin.xml` under `defaultExtensionNs="com.intellij.mcpServer"` with the `<mcpToolset>` extension
point. Tool methods are discovered by reflection: annotate a suspend method with `@McpTool` and
`@McpDescription`; annotate each parameter with `@McpDescription`. Use `mcpFail(message)` to
return an error response.

Current MCP flow:

1. `show_walkthrough_items(description, items)` shows labeled top-level steps and stores them in
   project history.
2. `await_walkthrough_question(walkthroughId)` waits for a user question from the active popup and
   returns the step label where it was asked.
3. `insert_walkthrough_tangents(walkthroughId, parentLabel, items)` inserts generated answer steps
   as labeled children and moves the popup to the first inserted step.

Key API notes:

- Get the active project via `currentCoroutineContext().projectOrNull` (import
  `com.intellij.mcpserver.projectOrNull`) — `getProjectOrNull` is the Java getter name; the
  Kotlin property is `projectOrNull`.
- Dispatch to the EDT via `withContext(Dispatchers.EDT)` — `EDT` (imported from
  `com.intellij.openapi.application`) is an extension property on `Dispatchers`, not a standalone
  value.

### IntelliJ platform dependency

`build.gradle.kts` resolves IntelliJ IDEA `2026.1` through the IntelliJ Platform Gradle Plugin,
so the project does not depend on a machine-specific local IDE path.

## Agent Rules

1. Read this file before writing any code.
2. Follow existing coding conventions; do not introduce new patterns.
3. Use conventional commit messages.
4. Always implement complete, working code — never write stub comments instead of actual
   implementation.
5. Stay focused on the requested task — avoid scope creep or unrelated changes.
6. After making changes, verify the build and linting pass: `just lint && just build`
7. Do not introduce new dependencies without asking first.
8. Keep user-facing walkthrough UI in JetBrains Compose / Jewel. If Swing hosting glue is needed,
   keep it minimal and limited to integration layers such as popup surfaces or Compose bridges.
9. Keep all external dependency versions in `gradle/libs.versions.toml` (Gradle version catalog).
