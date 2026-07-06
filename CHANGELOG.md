# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- A `persistTangents` option on the walkthrough MCP tools that lets a reusable or presentation
  walkthrough keep its stored history pristine: follow-up tangent steps are still shown live but
  no longer written back to the saved walkthrough when disabled (#47).

### Fixed

- Passing an empty or `null` items payload to the walkthrough MCP tools now returns a clear
  "items must not be empty" error instead of failing with an internal error.

## [0.5.0]

### Added

- A **Tools → Walkthrough → Export Walkthrough to Markdown…** action that saves any walkthrough from
  the project history as a shareable Markdown document, with each step (and its follow-up answer
  steps) rendered as a section annotated with its source location (PR #37).
  
### Fixed

- Dragging or resizing a walkthrough popup that overlaps the editor tab bar no longer reorders the
  tabs underneath (#38).

## [0.4.1]

### Added

- Improved linting and quality tooling configuration (PR #33).

### Fixed

- Stepping between diff walkthrough items now reuses the already-open diff viewer instead of
  opening a new tab (PR #32).
- The popup no longer briefly flashes the "agent is not listening" warning when the MCP client
  cancels and immediately re-issues a question wait (PR #31).
- Answer steps inserted via follow-up questions are now persisted to walkthrough history and
  restored when the walkthrough is reopened (#34, PR #35).

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
