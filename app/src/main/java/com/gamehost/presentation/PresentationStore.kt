package com.gamehost.presentation

import com.gamehost.content.ContentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the GM surface is allowed to do to the presentation. */
interface PresentationController {

    val state: StateFlow<PresentationState>

    /** Presents [id]. Reveals it if currently blanked. */
    fun show(id: ContentId, fromSlot: SlotId? = null)

    fun setBlackout(black: Boolean)

    fun toggleBlackout()

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
) : PresentationController {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<PresentationState> = _state.asStateFlow()

    override fun show(id: ContentId, fromSlot: SlotId?) = update {
        it.copy(
            scene = it.scene.copy(visual = it.scene.visual.copy(source = VisualSource.Image(id))),
            liveSlot = fromSlot,
            // v0.1 reveals immediately. Staging a scene behind the blank for a
            // deliberate reveal is a later UI addition and needs no model change.
            blackout = false,
        )
    }

    override fun setBlackout(black: Boolean) = update { it.copy(blackout = black) }

    override fun toggleBlackout() = update { it.copy(blackout = !it.blackout) }

    override fun setScaling(mode: ScalingMode) = update {
        it.copy(scene = it.scene.copy(visual = it.scene.visual.copy(scaling = mode)))
    }

    override fun clearScene() = update {
        it.copy(scene = Scene(visual = it.scene.visual.copy(source = VisualSource.None)), liveSlot = null)
    }

    override fun restore(state: PresentationState) {
        _state.value = state
    }

    private fun update(block: (PresentationState) -> PresentationState) {
        _state.update { current -> block(current).copy(revision = current.revision + 1) }
    }
}
