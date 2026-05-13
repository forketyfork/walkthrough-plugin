# Walkthrough Plugin

[![Build status](https://github.com/forketyfork/walkthrough-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/forketyfork/walkthrough-plugin/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-purple.svg)](https://kotlinlang.org/)

## About

Walkthrough Plugin is a prototype IntelliJ IDEA plugin for presenting inline walkthrough guidance
inside the editor. It shows a styled popup near a target line, keeps a connector anchored to that
line, and lets the user step through a sequence of walkthrough items.

Available on the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31637-walkthrough/).

<img src="docs/screenshot.png" alt="Walkthrough popup rendering a markdown step in IntelliJ IDEA, with a connector pointing at a JewelComposePanel call site" width="800">

## Features

- An MCP tool, `show_walkthrough_items`, that accepts JSON input and displays one or more
  walkthrough items with a description for history.
- Optional file and line navigation for each item, so a walkthrough can jump to the right place
  before rendering.
- Previous and Next navigation inside the popup for multi-step walkthroughs.
- Per-project walkthrough history stored under `.idea/walkthroughs/`, with a keymap-bindable
  action for replaying previous walkthroughs.
- A Compose-based popup UI rendered through Jewel on the IntelliJ Platform.

## Claude Code skill

A companion skill that teaches agents how to author walkthroughs is published in the
[agentic-skills](https://github.com/forketyfork/agentic-skills) marketplace. Install it in
Claude Code with:

```
/plugin marketplace add forketyfork/agentic-skills
/plugin install walkthrough@agentic-skills
```

Once installed, the skill activates whenever you ask for a guided tour or step-by-step code
walkthrough, and can be invoked explicitly as `/walkthrough`.

## Prerequisites

**Recommended:** Install [Nix](https://nixos.org/download) and [direnv](https://direnv.net), then
run `direnv allow` in the project directory. This provides JDK 21, pre-commit hooks, and all
development tooling automatically.

**Manual:** Install JDK 21. The Gradle wrapper is included in the repository.

## Development

| Command | Description |
| --- | --- |
| `just build` | Build the plugin |
| `just run` | Run in a sandboxed IDE instance |
| `just verify` | Verify plugin compatibility |
| `just lint` | Run Detekt static analysis |
| `just clean` | Clean build artifacts |
| `just hooks` | Install pre-commit hooks (Nix dev shell) |

Without `just`, use `./gradlew buildPlugin`, `./gradlew runIde`, etc. directly.

## Architecture

The plugin targets IntelliJ IDEA 261+ and uses JetBrains Compose (via the Jewel library) for all
UI. See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.
