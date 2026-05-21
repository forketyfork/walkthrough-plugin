package com.forketyfork.walkthrough

import androidx.compose.runtime.mutableStateOf
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffExtension
import com.intellij.diff.DiffManager
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities
import com.intellij.diff.util.Side as PlatformDiffSide

private val DIFF_WALKTHROUGH_CONTROLLER_KEY: Key<DiffWalkthroughController> =
    Key.create("walkthrough.diff.controller")
private val DIFF_WALKTHROUGH_ITEM_KEY: Key<WalkthroughItem> =
    Key.create("walkthrough.diff.item")
private const val SHORT_COMMIT_LENGTH = 12

@Suppress("LongMethod")
fun showDiffWalkthroughSession(
    project: Project,
    descriptors: List<DiffWalkthroughDescriptor>,
    items: List<WalkthroughItem>,
    acceptsQuestions: Boolean,
): WalkthroughSession? {
    if (descriptors.isEmpty() || items.isEmpty()) return null

    val paletteState = mutableStateOf(WalkthroughSettings.getInstance().selectedPalette)
    val sessionDisposable = Disposer.newCheckedDisposable("DiffWalkthroughPopupSession")
    Disposer.register(project, sessionDisposable)

    val registry = WalkthroughSessionRegistry.getInstance(project)
    registry.swapActive(sessionDisposable)?.let(Disposer::dispose)
    val session = registry.create(
        items = items,
        acceptsQuestions = acceptsQuestions,
        targetKind = WalkthroughTargetKind.Diff,
        diffDescriptors = descriptors,
    )
    Disposer.register(sessionDisposable) {
        registry.remove(session.id)
        registry.clearActive(sessionDisposable)
    }

    var popupRef: WalkthroughPopupSurface? = null
    var currentEditor: Editor? = null
    lateinit var controller: DiffWalkthroughController

    fun updatePopupPalette(palette: WalkthroughPalette) {
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed) {
                return@invokeLater
            }
            paletteState.value = palette
            popupRef?.updatePalette(palette)
        }
    }

    controller = DiffWalkthroughController(
        project = project,
        descriptors = descriptors,
        sessionDisposable = sessionDisposable,
        popupProvider = { popupRef },
        currentEditorUpdater = { editor -> currentEditor = editor },
    )

    val panel = createWalkthroughPanel(
        project = project,
        session = session,
        paletteProvider = { paletteState.value },
        onItemDisplayed = controller::scheduleItemNavigation,
        onNavigateToSource = controller::scheduleItemNavigation,
        onClose = { popupRef?.cancel() },
    )
    makeComponentHierarchyTransparent(panel)

    installPopupInteractionHandler(
        panel = panel,
        popupProvider = { popupRef },
        editorProvider = { currentEditor },
        onInteractionEnd = { saveCurrentGeometry(popupRef) },
    )

    ApplicationManager.getApplication().messageBus.connect(sessionDisposable).subscribe(
        WalkthroughSettingsListener.TOPIC,
        object : WalkthroughSettingsListener {
            override fun paletteChanged(palette: WalkthroughPalette) {
                updatePopupPalette(palette)
            }
        },
    )

    val popup = WalkthroughPopupSurface(
        content = panel,
        palette = paletteState.value,
        onCloseRequested = {
            popupRef = null
            registry.remove(session.id)
            Disposer.dispose(sessionDisposable)
        },
    )
    popupRef = popup
    Disposer.register(sessionDisposable, popup)
    controller.scheduleItemNavigation(items.first())
    return session
}

class WalkthroughDiffExtension : DiffExtension() {
    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        val controller = request.getUserData(DIFF_WALKTHROUGH_CONTROLLER_KEY) ?: return
        val item = request.getUserData(DIFF_WALKTHROUGH_ITEM_KEY) ?: return
        controller.attachToViewer(viewer, item)
    }
}

@Suppress("LongParameterList")
private class DiffWalkthroughController(
    private val project: Project,
    private val descriptors: List<DiffWalkthroughDescriptor>,
    private val sessionDisposable: CheckedDisposable,
    private val popupProvider: () -> WalkthroughPopupSurface?,
    private val currentEditorUpdater: (Editor) -> Unit,
) {
    private var pendingNavigationId = 0
    private var activeViewer: FrameDiffTool.DiffViewer? = null
    private var activeDescriptorId: String? = null

    fun scheduleItemNavigation(item: WalkthroughItem) {
        pendingNavigationId += 1
        val navigationId = pendingNavigationId
        SwingUtilities.invokeLater {
            if (sessionDisposable.isDisposed || navigationId != pendingNavigationId) {
                return@invokeLater
            }
            showItem(item)
        }
    }

    fun attachToViewer(viewer: FrameDiffTool.DiffViewer, item: WalkthroughItem) {
        if (sessionDisposable.isDisposed || viewer !is EditorDiffViewer) return
        trackActiveViewer(viewer, resolveDescriptor(item)?.id)
        val editor = selectEditor(viewer, item.diffSide ?: DiffSide.Right)
        val popup = popupProvider()
        if (editor != null && popup != null) {
            attachWhenShowing(popup, editor, item)
        }
    }

    private fun trackActiveViewer(viewer: FrameDiffTool.DiffViewer, descriptorId: String?) {
        activeDescriptorId = descriptorId
        if (activeViewer === viewer) return
        activeViewer = viewer
        // Avoid capturing `this` strongly inside the viewer-owned disposable: the diff viewer may
        // outlive the walkthrough session, and a strong reference would keep the whole controller
        // graph (session disposable, popup state, etc.) reachable until the diff tab is closed.
        val cleanup = createActiveViewerCleanup(WeakReference(this), viewer)
        Disposer.register(viewer, cleanup)
        // Also dispose the cleanup when the session ends so the references held by the cleanup
        // disposable itself are released even if the diff viewer remains open.
        Disposer.register(sessionDisposable, cleanup)
    }

    private fun attachWhenShowing(popup: WalkthroughPopupSurface, editor: Editor, item: WalkthroughItem) {
        val component = editor.contentComponent
        if (component.isShowing) {
            attachPopupToEditor(popup, editor, item)
            return
        }
        val listener = object : HierarchyListener {
            override fun hierarchyChanged(event: HierarchyEvent) {
                val showingChanged = event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L
                if (showingChanged && component.isShowing) {
                    component.removeHierarchyListener(this)
                    if (!sessionDisposable.isDisposed) {
                        attachPopupToEditor(popup, editor, item)
                    }
                }
            }
        }
        component.addHierarchyListener(listener)
        Disposer.register(
            sessionDisposable,
            Disposable { component.removeHierarchyListener(listener) },
        )
    }

    private fun attachPopupToEditor(popup: WalkthroughPopupSurface, editor: Editor, item: WalkthroughItem) {
        val popupItem = if (isResolvableWalkthroughLine(item.line, editor.document.lineCount)) {
            moveCaretToLine(editor, item.line)
            item
        } else {
            item.copy(line = null)
        }
        currentEditorUpdater(editor)
        popup.update(editor, popupItem)
        popup.connectorHidden = false
        applyPopupGeometryForItem(popup, editor, popupItem)
    }

    // Called from [createActiveViewerCleanup] via `WeakReference<DiffWalkthroughController>.get()?.…`.
    // Qodana's UnusedSymbol inspection can't resolve the call target through the weak reference
    // (the generic is erased to `Object?`), so it flags the function as unused — suppress here.
    @Suppress("unused")
    fun clearActiveViewerIfMatches(viewer: FrameDiffTool.DiffViewer) {
        if (activeViewer === viewer) {
            activeViewer = null
            activeDescriptorId = null
        }
    }

    private fun showItem(item: WalkthroughItem) {
        val descriptor = resolveDescriptor(item) ?: return
        val existing = activeViewer
        if (existing != null && !existing.isViewerDisposed() && activeDescriptorId == descriptor.id) {
            attachToViewer(existing, item)
            return
        }
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequestChain.fromProducer(
                DiffWalkthroughRequestProducer(
                    project = project,
                    descriptor = descriptor,
                    item = item,
                    controller = this,
                ),
            ),
            DiffDialogHints.DEFAULT,
        )
    }

    private fun resolveDescriptor(item: WalkthroughItem): DiffWalkthroughDescriptor? = item.diffId
        ?.let { id -> descriptors.firstOrNull { descriptor -> descriptor.id == id } }
        ?: item.diffFile?.let(::resolveDescriptorByFile)
        ?: descriptors.singleOrNull()

    private fun resolveDescriptorByFile(diffFile: String): DiffWalkthroughDescriptor? = descriptors
        .singleOrNull { descriptor ->
            diffFile == descriptor.file || diffFile == descriptor.leftFile || diffFile == descriptor.rightFile
        }

    private fun selectEditor(viewer: EditorDiffViewer, side: DiffSide): Editor? {
        val editors = viewer.editors
        return when (side) {
            DiffSide.Left -> editors.getOrNull(0)
            DiffSide.Right -> editors.getOrNull(1) ?: editors.getOrNull(0)
        }
    }
}

/**
 * Creates a [Disposable] that clears the controller's reference to [viewer] when either the viewer
 * or the walkthrough session is disposed. The controller is held weakly so a still-open diff viewer
 * cannot keep the walkthrough session graph reachable after the session itself has been closed.
 */
private fun createActiveViewerCleanup(
    controllerRef: WeakReference<DiffWalkthroughController>,
    viewer: FrameDiffTool.DiffViewer,
): Disposable = Disposable {
    controllerRef.get()?.clearActiveViewerIfMatches(viewer)
    controllerRef.clear()
}

/**
 * Non-deprecated replacement for `Disposer.isDisposed(disposable)`: relies on [CheckedDisposable]
 * when the viewer implements it. If the viewer doesn't expose its disposal state we conservatively
 * treat it as not disposed; in that case the viewer-owned cleanup callback above will clear
 * `activeViewer` once the platform actually disposes the viewer.
 */
private fun FrameDiffTool.DiffViewer.isViewerDisposed(): Boolean = (this as? CheckedDisposable)?.isDisposed == true

private class DiffWalkthroughRequestProducer(
    private val project: Project,
    private val descriptor: DiffWalkthroughDescriptor,
    private val item: WalkthroughItem,
    private val controller: DiffWalkthroughController,
) : DiffRequestProducer {
    override fun getName(): String = descriptor.displayFile

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest = try {
        val leftPath = descriptor.leftFilePath()
        val rightPath = descriptor.rightFilePath()
        val leftContent = loadRevisionContent(
            filePath = leftPath,
            commit = descriptor.leftCommit,
        )
        val rightContent = loadRevisionContent(
            filePath = rightPath,
            commit = descriptor.rightCommit,
        )
        if (!leftContent.loaded && !rightContent.loaded) {
            throw VcsException("Failed to load both revisions for ${descriptor.displayFile}")
        }
        SimpleDiffRequest(
            descriptor.displayTitle,
            leftContent.content,
            rightContent.content,
            descriptor.leftTitle,
            descriptor.rightTitle,
        ).apply {
            putUserData(DIFF_WALKTHROUGH_CONTROLLER_KEY, controller)
            putUserData(DIFF_WALKTHROUGH_ITEM_KEY, item)
            item.line?.let { line ->
                putUserData(
                    DiffUserDataKeys.SCROLL_TO_LINE,
                    Pair.create(item.diffSide.toPlatformSide(), (line - 1).coerceAtLeast(0)),
                )
            }
            putUserData(DiffUserDataKeys.MASTER_SIDE, item.diffSide.toPlatformSide())
        }
    } catch (exception: VcsException) {
        throw DiffRequestProducerException(exception)
    } catch (exception: IllegalArgumentException) {
        throw DiffRequestProducerException(exception)
    }

    private data class RevisionContent(val content: DiffContent, val loaded: Boolean)

    private fun loadRevisionContent(filePath: FilePath, commit: String): RevisionContent {
        val contentFactory = DiffContentFactory.getInstance()
        return try {
            val revision = GitContentRevision.createRevision(filePath, GitRevisionNumber(commit), project)
            val text = revision.content ?: return RevisionContent(contentFactory.createEmpty(), loaded = false)
            RevisionContent(contentFactory.create(project, text, filePath), loaded = true)
        } catch (_: VcsException) {
            RevisionContent(contentFactory.createEmpty(), loaded = false)
        }
    }

    private fun DiffWalkthroughDescriptor.leftFilePath(): FilePath = resolveDiffFilePath(leftFile ?: file ?: rightFile)

    private fun DiffWalkthroughDescriptor.rightFilePath(): FilePath = resolveDiffFilePath(rightFile ?: file ?: leftFile)

    private fun resolveDiffFilePath(relativePath: String?): FilePath {
        require(!relativePath.isNullOrBlank()) { "Diff file path must not be blank" }
        val absolutePath = resolveProjectRelativeWalkthroughPath(project.basePath, relativePath)
            ?: throw IllegalArgumentException("Invalid project-relative diff path: $relativePath")
        return VcsUtil.getFilePath(absolutePath.toString(), false)
    }
}

private val DiffWalkthroughDescriptor.displayFile: String
    get() = rightFile ?: file ?: leftFile ?: id

private val DiffWalkthroughDescriptor.displayTitle: String
    get() = "$displayFile: ${leftCommit.shortCommit()} vs ${rightCommit.shortCommit()}"

private val DiffWalkthroughDescriptor.leftTitle: String
    get() = leftCommit.shortCommit()

private val DiffWalkthroughDescriptor.rightTitle: String
    get() = rightCommit.shortCommit()

private fun String.shortCommit(): String = take(SHORT_COMMIT_LENGTH)

private fun DiffSide?.toPlatformSide(): PlatformDiffSide = when (this) {
    DiffSide.Left -> PlatformDiffSide.LEFT
    DiffSide.Right, null -> PlatformDiffSide.RIGHT
}
