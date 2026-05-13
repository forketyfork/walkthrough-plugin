# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
