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
- `DiffUserDataKeys.SCROLL_TO_LINE` can request initial scrolling to a side and
  line.
- `DiffExtension.onViewerCreated(viewer, context, request)` can customize
  existing diff viewers.
- `EditorDiffViewer.getEditors()` exposes the editor instances for text diff
  viewers.
- Diff tabs opened as file editors can also expose embedded editors via
  `FileEditorWithTextEditors.getEmbeddedEditors()`.
- With a `Git4Idea` bundled plugin dependency, `GitContentRevision` can resolve
  a project file at a specific Git commit hash, and the VCS `Change`/diff
  request machinery can render a standard IDEA diff from those revisions.

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
    val diffId: String? = null,
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
`diffId`, `diffFile`, `diffSide`, and `line`. The line is 1-based in the chosen
side's full file text at that commit, not a patch hunk line. Commit hashes live
on the diff descriptor, not duplicated into every item.

### Diff content source

Use commit-backed diffs. The agent should not submit file text. It should
submit the commit hashes that identify the two file revisions to compare.

```json
{
  "kind": "diff",
  "diffs": [
    {
      "id": "foo-main-to-pr",
      "file": "src/Foo.kt",
      "leftCommit": "1a2b3c4d5e6f...",
      "rightCommit": "9f8e7d6c5b4a..."
    }
  ],
  "items": [
    {
      "text": "This new branch handles the null result.",
      "diffId": "foo-main-to-pr",
      "diffFile": "src/Foo.kt",
      "diffSide": "right",
      "line": 42
    }
  ]
}
```

Reasons for this contract:

- It keeps large file contents out of MCP tool arguments and history records.
- It lets IDEA render a native VCS diff and load contents through the platform's
  Git revision cache.
- It makes PR walkthroughs precise: the agent can compare a file at the merge
  base to the same file at the PR head.
- It makes history replay feasible as long as the commits remain available in
  the local repository.

For a PR walkthrough, the agent should determine the merge-base commit and the
PR head commit, ensure both commits are available locally, and pass those hashes
for every file diff it wants to annotate. For a single commit walkthrough, the
agent should compare the parent commit to the commit being explained. For a
branch walkthrough, compare the merge base of the target branch and topic branch
to the topic branch head.

The first implementation should require full commit hashes or refs that Git can
resolve locally. Full hashes are preferred because they are stable for history
replay. The plugin can optionally resolve refs to full hashes before saving the
record.

Support renamed files by allowing separate left/right paths:

```json
{
  "kind": "diff",
  "diffs": [
    {
      "id": "foo-rename",
      "leftFile": "src/OldFoo.kt",
      "rightFile": "src/Foo.kt",
      "leftCommit": "1a2b3c4d5e6f...",
      "rightCommit": "9f8e7d6c5b4a..."
    }
  ],
  "items": [...]
}
```

In the common non-rename case, `file` is shorthand for both `leftFile` and
`rightFile`. `id` should be unique within the walkthrough payload. It prevents
ambiguity if the same file is shown across more than one commit pair.

This does couple diff walkthroughs to Git. That is acceptable for this feature
because the requested workflow is PR/change review by commit hash. The plugin
will need `bundledPlugin("Git4Idea")` in Gradle and `<depends
optional="true|false">Git4Idea</depends>` in `plugin.xml`. Make the dependency
non-optional if diff walkthroughs are a core advertised feature; make it
optional only if the tool is registered conditionally or fails with a clear
"Git plugin unavailable" error.

### Opening and anchoring

For each diff item:

1. Find the matching diff descriptor by `diffId`, falling back to `diffFile`
   only if the payload has a single descriptor for that file.
2. Resolve the project Git root for the left/right file paths.
3. Build `FilePath` instances for `leftFile` and `rightFile`.
4. Build `GitRevisionNumber(leftCommit)` and `GitRevisionNumber(rightCommit)`.
   Optionally call `GitRevisionNumber.resolve(project, root, ref)` first to
   normalize refs and short hashes to full hashes.
5. Build `GitContentRevision.createRevision(filePath, revisionNumber, project)`
   for each side.
6. Build a VCS `Change(leftRevision, rightRevision)`.
7. Create a `ChangeDiffRequestProducer` or directly create equivalent
   `DiffContent` instances from the revisions. Prefer the platform producer if
   it gives better VCS metadata and title handling without relying on internal
   APIs.
8. Put `DiffUserDataKeys.SCROLL_TO_LINE` on the request using the requested
   side and zero-based line.
9. Add plugin-specific user data to the request containing the walkthrough
   session id and target item.
10. Call `DiffManager.getInstance().showDiff(project, request)` on the EDT.
11. Use a `DiffExtension` to detect tagged requests in `onViewerCreated`.
12. If the viewer is an `EditorDiffViewer`, choose the editor by side from
   `getEditors()`, move its caret to the requested line, and attach
   `WalkthroughPopupSurface` to that editor.

The existing `WalkthroughPopupSurface` can remain editor-based. Diff editors are
normal editor instances for anchoring, scrolling listeners, and line-to-screen
coordinate calculations.

Never call `ContentRevision.getContent()` or `getContentAsBytes()` on the EDT.
Git revision content loading can hit Git and the platform cache. Prepare the
diff request under background/progress-aware work, then switch to the EDT to
show the diff and attach the popup.

### Navigation behavior

When the user clicks Previous or Next:

- If the next diff item references the same diff descriptor, keep the current
  diff open and move the popup to the requested side and line.
- If the next item references a different file or a different commit pair, open
  that diff and attach after the viewer is created.

If the user switches away from the diff tab, hide the connector using the same
principle already used for file editor changes.

### History

Persist `kind`, diff descriptors, and item-side fields in history.

For commit-backed diffs, history should persist:

- `leftCommit` and `rightCommit`.
- `leftFile` and `rightFile`, or `file` when paths are unchanged.
- the descriptor `id`.
- item anchors: `diffId`, `diffFile`, `diffSide`, and `line`.

Replay should fail gracefully when the repository no longer has one of the
commits. The failure message should name the missing commit and file.

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
> one call must target Git commit-backed file diffs; do not mix file walkthrough
> items and diff walkthrough items. Do not submit file contents. Submit commit
> hashes for the two file revisions to compare.

Suggested diff payload parameter description:

> JSON object with `diffs` and `items`. `diffs` supplies Git revisions to
> compare: `id`, `file`, `leftCommit`, and `rightCommit`; for renames, use
> `leftFile` and `rightFile` instead of `file`. `items` is an array of
> walkthrough items with `text`, `diffId`, `diffFile`, `diffSide`, and `line`.
> `diffSide` is `left` or `right`. `line` is 1-based in that side's full file
> text at that commit, not the patch hunk line. Use `right` for added or modified
> new code, `left` for removed old code, and `right` for unchanged context unless
> discussing the old version. Verify every line by inspecting that exact file at
> that exact commit before calling. For PRs, pass the merge-base commit as
> `leftCommit` and the PR head commit as `rightCommit`.

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
line numbers. Inspect the exact file at the exact commit first, then verify the
anchor line before calling the tool.
```

Add a commit-selection section:

```markdown
## Diff Commits

For diff walkthroughs, submit commit hashes, not file text.

For PR walkthroughs:
- Fetch the PR branch and target branch if needed.
- Resolve the merge base between the target branch and PR head.
- Use the merge-base commit as `leftCommit`.
- Use the PR head commit as `rightCommit`.
- For every walkthrough item, verify the anchor line in the selected side at
  that commit.

For single-commit walkthroughs, use the first parent as `leftCommit` and the
commit as `rightCommit`. For branch walkthroughs, use the merge base with the
target branch as `leftCommit` and the branch head as `rightCommit`.
```

## Open Questions

- Should the first implementation force side-by-side diffs for stable side
  anchoring, or support unified diffs by mapping item anchors to the unified
  synthetic editor?
- Should there be a separate `show_diff_walkthrough_items` tool, or a single
  `show_walkthrough_items` tool with a `kind` payload? Separate tools are easier
  for agents to choose correctly and preserve backward compatibility.
- Should the tool accept short hashes and refs, or require full hashes? Full
  hashes are better for persistence; accepting refs is more ergonomic but should
  resolve them to full hashes before history is saved.
