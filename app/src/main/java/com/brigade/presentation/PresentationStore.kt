package com.brigade.presentation

import com.brigade.content.ContentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the GM surface is allowed to do to the presentation. */
interface PresentationController {

    val state: StateFlow<PresentationState>

    /** Presents an image directly. Clears any note bar — a plain image came from no note. */
    fun show(id: ContentId, fromSlot: SlotId? = null)

    /**
     * Presents a Markdown note: the players see [imageId] (black when null), and the GM bar
     * shows [note].
     */
    fun showNote(imageId: ContentId?, note: NoteBar, fromSlot: SlotId? = null)

    /**
     * Shows or hides the full-screen campaign info panel.
     *
     * This is also the blank control: a campaign with no info configured projects to black,
     * which is the "nothing specific, I am preparing" state.
     */
    fun setInfoMode(on: Boolean)

    fun setScaling(mode: ScalingMode)

    /** Drops the scene without changing whether the display is blanked. */
    fun clearScene()

    fun restore(state: PresentationState)
}

/**
 * Holds [PresentationState] for the life of the process.
 *
 * **Owned by the application graph, never by a ViewModel.** The player window is an
 * `android.app.Presentation` with its own lifecycle: it can outlive an Activity
 * recreation. State scoped to an Activity would stop emitting underneath it, freezing
 * the players' screen on a stale frame while the GM's UI moves on — a failure you find
 * at the table, not in development.
 *
 * Note this is a plain [MutableStateFlow] and not `stateIn(scope, WhileSubscribed())`.
 * A sharing policy would restart the upstream when collectors momentarily drop to zero,
 * which is exactly what happens while the Activity is being recreated.
 */
class PresentationStore(
    initial: PresentationState = PresentationState(),
    /**
     * The campaign's configured transition. A supplier rather than a constructor value so
     * it can change when a different campaign folder is opened — and a lambda rather than
     * a method on [PresentationController], which stays a list of things a *GM* can do,
     * not a place to put configuration.
     */
    private val spec: () -> TransitionSpec = { TransitionSpec.CUT },
    private val nowNanos: () -> Long = System::nanoTime,
) : PresentationController {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<PresentationState> = _state.asStateFlow()

    override fun show(id: ContentId, fromSlot: SlotId?) =
        present(VisualSource.Image(id), note = null, fromSlot = fromSlot)

    override fun showNote(imageId: ContentId?, note: NoteBar, fromSlot: SlotId?) =
        present(
            source = if (imageId == null) VisualSource.None else VisualSource.Image(imageId),
            note = note,
            fromSlot = fromSlot,
        )

    private fun present(source: VisualSource, note: NoteBar?, fromSlot: SlotId?) = change {
        it.copy(
            scene = it.scene.copy(
                mode = SceneMode.Visual,
                visual = it.scene.visual.copy(source = source),
                // Always assigned, never merged: showing a plain image must clear a bar left
                // over from the note before it.
                note = note,
            ),
            liveSlot = fromSlot,
        )
    }

    /**
     * Loads the campaign info panel.
     *
     * Not on [PresentationController]: that interface is the list of things a *GM* can do,
     * and this is data arriving from `Campagne.md`. While the scene is showing an image it
     * changes no frame and so stamps no transition; re-reading the file *while* the panel is
     * up does change the frame, and dissolving to the updated panel is the right thing.
     */
    fun setInfo(panel: InfoPanel?, background: ContentId?) = change {
        it.copy(scene = it.scene.copy(info = panel, infoBackground = background))
    }

    override fun setInfoMode(on: Boolean) = change {
        it.copy(
            scene = it.scene.copy(mode = if (on) SceneMode.Info else SceneMode.Visual),
            // Deliberately does NOT clear liveSlot, so "INFO then back returns to the same
            // slot" comes free. The control bar's highlight is computed from state, so
            // nothing lights up wrongly meanwhile.
        )
    }

    override fun setScaling(mode: ScalingMode) = change {
        it.copy(scene = it.scene.copy(visual = it.scene.visual.copy(scaling = mode)))
    }

    override fun clearScene() = change {
        it.copy(
            // copy, not a fresh Scene: the info panel is campaign-derived, not session
            // state, so clearing what is *shown* must not discard what was *loaded*.
            scene = it.scene.copy(
                mode = SceneMode.Visual,
                visual = it.scene.visual.copy(source = VisualSource.None),
                note = null,
            ),
            liveSlot = null,
        )
    }

    override fun restore(state: PresentationState) {
        // A restored state never arrives mid-dissolve.
        _state.value = state.copy(transition = null)
    }

    /**
     * Applies [block], bumps the revision, and stamps a transition when what the players
     * can see actually changed.
     */
    private fun change(block: (PresentationState) -> PresentationState) {
        _state.update { current ->
            val next = block(current)
            next.copy(
                revision = current.revision + 1,
                transition = transitionFor(current, next),
            )
        }
    }

    private fun transitionFor(
        current: PresentationState,
        next: PresentationState,
    ): ActiveTransition? {
        // Nothing the players can see moved — a scaling tweak, a slot re-tap on the image
        // already showing. Leave any running dissolve alone rather than restarting it.
        if (current.frame() == next.frame()) return current.transition

        val chosen = spec()
        if (chosen.kind == TransitionKind.Cut || chosen.durationMillis <= 0) return null

        return ActiveTransition(
            from = outgoingFrame(current),
            spec = chosen,
            startNanos = nowNanos(),
            seed = (current.revision + 1).toInt(),
        )
    }

    /**
     * What the new dissolve should start *from*.
     *
     * When one is already running — the GM taps slot 3 then slot 5 immediately, which will
     * happen at a real table — carry over whichever layer the players are mostly seeing.
     * Worst case is a single-frame step at the midpoint. Always taking the interrupted
     * target instead pops to an image the GM has already moved past, on every interruption.
     */
    private fun outgoingFrame(current: PresentationState): Frame {
        val running = current.transition ?: return current.frame()
        return if (running.progressAt(nowNanos()) < 0.5f) running.from else current.frame()
    }
}
