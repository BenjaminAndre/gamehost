package com.gamehost.presentation

import com.gamehost.content.ContentId

/**
 * How a visual is fitted to the surface it is drawn on.
 *
 * These are presentation behaviours, not properties of the image (§13). v0.1 only ever
 * sets [Fit]; the other two exist so that adding them later is a UI change and not a
 * model change.
 */
enum class ScalingMode {
    /** Whole image visible, aspect preserved, remainder letterboxed in black. */
    Fit,

    /** Fills the surface, cropping the overflow. */
    Fill,

    /** One image pixel per surface pixel. */
    Native,
}

sealed interface VisualSource {

    /** Nothing to show. Renders as black. */
    data object None : VisualSource

    data class Image(val id: ContentId) : VisualSource
}

data class VisualPresentation(
    val source: VisualSource = VisualSource.None,
    val scaling: ScalingMode = ScalingMode.Fit,
)

/**
 * What the session is currently about.
 *
 * Composition, not subclassing (§16 Trap 7). Audio becomes `val audio: AudioPresentation?`
 * here and sound effects `val overlay: OverlayPresentation?`, with no
 * `ImageWithMusicPresentation` in sight.
 */
data class Scene(
    val visual: VisualPresentation = VisualPresentation(),
)

/**
 * The single source of truth for what the players can see.
 *
 * Independent of the UI and of any physical display (§10). The GM surface writes it,
 * the two renderers read it, and neither renderer may write it.
 */
data class PresentationState(
    val scene: Scene = Scene(),

    /**
     * Whether the players are allowed to see [scene].
     *
     * Orthogonal to the scene rather than a [VisualSource] variant, on purpose. Blanking
     * is a *modifier over* the scene, not a scene of its own, which makes
     * "blank then un-blank returns to the same image" free, and keeps the split honest
     * once audio arrives — blanking the screen should probably not stop the music.
     */
    val blackout: Boolean = false,

    /** Which slot is live, or null when shown ad hoc via *Afficher*. */
    val liveSlot: SlotId? = null,

    /** Monotonic. Not used in v0.1; transitions will key off it rather than off wall time. */
    val revision: Long = 0L,
)

/**
 * The only function the renderers call.
 *
 * Pure, so the "one state, two renderers" guarantee is a property that can be
 * unit-tested rather than a convention that has to be remembered.
 */
fun PresentationState.effectiveVisual(): VisualPresentation =
    if (blackout) scene.visual.copy(source = VisualSource.None) else scene.visual
