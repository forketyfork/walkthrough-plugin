# Diff Walkthroughs Design

## Goal

Support walkthroughs anchored to IntelliJ IDEA diff viewers in addition to
walkthroughs anchored to normal file editors.

Each walkthrough session should have exactly one target kind:

- `file`: items point at project files and 1-based file lines.
- `diff`: items point at one side of an IDEA diff and 1-based lines in that
  side's full text.

Agents should choose the target kind before preparing items. A request about
changes, commits, branches, or PR review should normally use a diff walkthrough.
A request about how existing code works should normally use a file walkthrough.

## Feasibility

Opening a diff view from a plugin is supported by the IntelliJ Platform.

Relevant public API:

- `DiffManager.getInstance().showDiff(project, request)` opens a diff request.
- `DiffRequestFactory.getInstance().createFromFiles(project, left, right)`
  creates file-backed diff requests.
- `DiffContentFactory.getInstance().create(project, text, highlightFile)` and
  `SimpleDiffRequest` can create text-backed diffs when the agent supplies both
  sides.
- `DiffUserDataKeys.SCROLL_TO_LINE` can request initial scrolling to a side and
  line.
- `DiffExtension.onViewerCreated(viewer, context, request)` can customize
  existing diff viewers.
- `EditorDiffViewer.getEditors()` exposes the editor instances for text diff
  viewers.
- Diff tabs opened as file editors can also expose embedded editors via
  `FileEditorWithTextEditors.getEmbeddedEditors()`.

This means a diff walkthrough can be implemented without replacing the existing
Compose popup. The main change is target resolution: instead of resolving a
project file to a selected text editor, resolve a diff item to a diff request,
open the request, select the correct embedded editor, then pass that editor to
the same popup surface.

## Recommended Implementation

### Data model

Keep a top-level session target kind so mixed file/diff sessions are rejected.
Avoid inferring per item.

Suggested model:

```kotlin
enum class WalkthroughTargetKind {
    File,
    Diff
}

data class WalkthroughItem(
    val text: String,
    val file: String? = null,
    val line: Int? = null,
    val diffFile: String? = null,
    val diffSide: DiffSide? = null,
    val label: String? = null,
    val parentLabel: String? = null
)

enum class DiffSide {
    Left,
    Right
}
```

The current `file` and `line` fields stay file-mode fields. Diff-mode items use
`diffFile`, `diffSide`, and `line`. The line is 1-based in the chosen side's
full file text, not a patch hunk line.

### Diff content source

Start with text-backed diffs prepared by the agent:

```json
{
  "kind": "diff",
  "diffs": [
    {
      "file": "src/Foo.kt",
      "leftTitle": "base",
      "rightTitle": "working tree",
      "leftText": "...",
      "rightText": "..."
    }
  ],
  "items": [
    {
      "text": "This new branch handles the null result.",
      "diffFile": "src/Foo.kt",
      "diffSide": "right",
      "line": 42
    }
  ]
}
```

Reasons:

- It keeps the plugin independent of Git, GitHub, and PR provider APIs.
- It lets agents review arbitrary diffs, including uncommitted changes, branch
  comparisons, copied patches, and PR context obtained through MCP.
- It makes line-number verification explicit: the agent must verify the line in
  the side text it submits.

Later, add a VCS-backed mode if needed:

```json
{
  "kind": "diff",
  "source": {
    "type": "git",
    "baseRef": "main",
    "headRef": "HEAD"
  },
  "items": [...]
}
```

That second mode is more native, but it couples the plugin to VCS APIs and makes
non-Git/remote-review workflows harder.

### Opening and anchoring

For each diff item:

1. Find the matching submitted diff by `diffFile`.
2. Create left and right `DocumentContent` with `DiffContentFactory`.
3. Use `SimpleDiffRequest` with content titles.
4. Put `DiffUserDataKeys.SCROLL_TO_LINE` on the request using the requested
   side and zero-based line.
5. Add plugin-specific user data to the request containing the walkthrough
   session id and target item.
6. Call `DiffManager.getInstance().showDiff(project, request)` on the EDT.
7. Use a `DiffExtension` to detect tagged requests in `onViewerCreated`.
8. If the viewer is an `EditorDiffViewer`, choose the editor by side from
   `getEditors()`, move its caret to the requested line, and attach
   `WalkthroughPopupSurface` to that editor.

The existing `WalkthroughPopupSurface` can remain editor-based. Diff editors are
normal editor instances for anchoring, scrolling listeners, and line-to-screen
coordinate calculations.

### Navigation behavior

When the user clicks Previous or Next:

- If the next diff item references the same `diffFile`, keep the current diff
  open and move the popup to the requested side and line.
- If the next item references a different `diffFile`, open that diff and attach
  after the viewer is created.

If the user switches away from the diff tab, hide the connector using the same
principle already used for file editor changes.

### History

Persist `kind`, diff descriptors, and item-side fields in history.

For text-backed diffs, storing full left/right texts can make history large. A
reasonable first implementation is:

- Persist diff walkthrough metadata and items.
- Do not persist full diff contents unless needed for replay.
- Mark replay unavailable if the stored diff contents are absent.

If replay is important from day one, persist the texts but cap record size and
fail gracefully when a diff is too large.

## MCP Tool Shape

Prefer separate show tools. This gives agents a clearer decision point and keeps
the existing file walkthrough tool backward-compatible:

- `show_walkthrough_items`: file walkthroughs only.
- `show_diff_walkthrough_items`: diff walkthroughs only.

Keep `await_walkthrough_question` unchanged. For follow-up insertions, either:

- keep `insert_walkthrough_tangents` and parse items according to the stored
  session kind, or
- add `insert_diff_walkthrough_tangents` for symmetry and clearer descriptions.

The lower-risk first pass is to keep one insertion tool and make its description
explicit that item shape depends on the parent walkthrough kind.

### File Tool Description

Suggested `show_walkthrough_items` description:

> Shows a file walkthrough anchored to normal project files. Use this when the
> user asks how code works, wants an architecture tour, asks for onboarding, or
> needs an explanation of existing behavior. Do not use this for PR review,
> branch review, commit review, or "what changed" requests; use
> `show_diff_walkthrough_items` instead. Every item must refer to the current
> full file contents, and line numbers must be verified from the actual file.

Suggested file item parameter description:

> JSON array of file walkthrough items:
> `[{"text":"...","file":"src/Foo.kt","line":10}]`. Each item requires `text`.
> `file` is project-root-relative. `line` is 1-based in the current full file,
> not a diff hunk. Verify line numbers by reading the actual file before calling
> this tool.

### Diff Tool Description

Suggested `show_diff_walkthrough_items` description:

> Shows a diff walkthrough anchored to IntelliJ IDEA diff viewers. Use this when
> the user asks about changes, a PR, a review, a commit, a branch comparison, a
> patch, or "what changed". Do not use this for general code explanation unless
> the user specifically wants the explanation in terms of a change. All items in
> one call must target submitted diffs; do not mix file walkthrough items and
> diff walkthrough items.

Suggested diff payload parameter description:

> JSON object with `diffs` and `items`. `diffs` supplies the text to compare:
> `file`, `leftTitle`, `rightTitle`, `leftText`, `rightText`. `items` is an
> array of walkthrough items with `text`, `diffFile`, `diffSide`, and `line`.
> `diffSide` is `left` or `right`. `line` is 1-based in that side's full text,
> not the patch hunk line. Use `right` for added or modified new code, `left` for
> removed old code, and `right` for unchanged context unless discussing the old
> version. Verify every line against the side text before calling.

## Companion Skill Guidance

Add a mode-selection section to the walkthrough skill:

```markdown
## Choose File vs Diff Mode

Before preparing items, choose exactly one mode.

Use file mode for:
- explaining how existing code works
- architecture or onboarding tours
- tracing runtime behavior
- debugging a current implementation without focusing on a change set

Use diff mode for:
- PR review
- commit, branch, or patch review
- "what changed?", "review my changes", or "explain this diff"
- risk analysis, regression analysis, or test recommendations for a change set

Never mix file and diff items in one walkthrough. If the user asks both to
review changes and explain surrounding architecture, start with a diff
walkthrough and use follow-up tangent steps for extra context, or ask whether
they want a separate file walkthrough.
```

Add a line-number section:

```markdown
## Line Numbers

File walkthrough lines are 1-based lines in the current full file.

Diff walkthrough lines are 1-based lines in the selected diff side's full text:
`right` for added/new code, `left` for deleted/old code. Do not use patch hunk
line numbers. Read or generate the exact side text first, then verify the
anchor line before calling the tool.
```

## Open Questions

- Should diff history replay persist full diff texts, or should diff sessions be
  intentionally non-replayable unless backed by VCS refs?
- Should the first implementation force side-by-side diffs for stable side
  anchoring, or support unified diffs by mapping item anchors to the unified
  synthetic editor?
- Should there be a separate `show_diff_walkthrough_items` tool, or a single
  `show_walkthrough_items` tool with a `kind` payload? Separate tools are easier
  for agents to choose correctly and preserve backward compatibility.
