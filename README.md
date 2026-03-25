# Walkthrough Plugin

Walkthrough Plugin is a prototype IntelliJ IDEA plugin for presenting inline walkthrough guidance inside the editor.
It shows a styled popup near a target line, keeps a connector anchored to that line, and lets the user step through a
sequence of walkthrough items.

## Implemented in this prototype

- An editor action that shows a sample walkthrough item above the current line.
- An MCP tool, `show_walkthrough_items`, that accepts JSON input and displays one or more walkthrough items.
- Optional file and line navigation for each item, so a walkthrough can jump to the right place before rendering.
- Previous and Next navigation inside the popup for multi-step walkthroughs.
- A Compose-based popup UI rendered through Jewel on the IntelliJ Platform.

## Development

```bash
./gradlew build
./gradlew runIde
./gradlew verifyPlugin
```

The project resolves IntelliJ IDEA `2025.3.4` through Gradle, so it does not depend on a local IDE installation path.
