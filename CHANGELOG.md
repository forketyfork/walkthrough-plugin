# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Expanded Detekt rule sets with `potential-bugs`, `exceptions`, and `coroutines` audits, plus
  the `ktlint-wrapper` formatting rules and the `io.nlopez.compose.rules:detekt` Compose-specific
  ruleset. Existing findings are grandfathered through `detekt-baseline.xml`.
- `.editorconfig` at the repo root to keep IDE-, Detekt-, and ktlint-managed formatting in sync.
- Qodana JVM (Community) static analysis via the `JetBrains/qodana-action` GitHub Actions
  workflow, configured by `qodana.yaml`. Catches DevKit / IntelliJ Platform inspections that
  Detekt does not implement.
- `typos` and `zizmor` pre-commit hooks (wired through `flake.nix`), with `.typos.toml` and
  `.github/zizmor.yml` configuration files documenting the intentional exemptions.
- `verifyPlugin` now runs on every pull request, not just on pushes to `main`.

### Fixed

- The popup no longer briefly flashes the "agent is not listening" warning when the MCP client
  cancels and immediately re-issues `await_walkthrough_question`. The status now waits for a
  short grace period (5 s) before flipping. The underlying `CancellationException` is also no
  longer surfaced to the IDE log as a tool-call error. The inline spinner is now cleared as soon
  as `insert_walkthrough_tangents` returns, instead of staying on screen until the grace window
  expires.

## [0.4.0]

### Added

- It's now possible to attach walkthrough items to diff views by asking the agent for PR or changes review (#29)

### Fixed

- The popup now shows the agent status if it's not listening to user questions (#27)

## [0.3.2]

### Fixed

- Popup location and size are now preserved.
- Popup size is increased and the layout is improved.

## [0.3.1]

### Fixed

- Popup no longer jumps after appearing; it now opens at its final position and keeps the
  target line clear of the popup body (#19).
- Replaced `runReadActionBlocking` with `WriteIntentReadAction` to avoid read-action assertions
  when navigating between walkthrough steps (#17).
- Only one walkthrough popup is visible at a time; previous sessions are disposed when a new one
  starts (#16).

## [0.3.0]

### Added

- Users can ask follow-up questions from a walkthrough step. Agents can wait for those questions
  and insert clarifying answer steps back into the walkthrough.

## [0.2.0]

### Added

- Per-project walkthrough history callable via a hotkey (#10).
- Settings with selectable color palettes for the popups (#11).

## [0.1.1]

### Fixed

- Fixed an issue with high CPU usage (#6).

## [0.1.0]

### Added

- MCP tool `show_walkthrough_items` for programmatic walkthrough guidance
- Compose-based popup UI with markdown rendering and line-anchored connector
