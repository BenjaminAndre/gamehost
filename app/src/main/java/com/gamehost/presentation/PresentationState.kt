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

/** Which composed component is on the surface. INFO replaces the image; it does not overlay it. */
enum class SceneMode { Visual, Info }

/**
 * What the session is currently about.
 *
 * Composition, not subclassing (§16 Trap 7). Audio becomes `val audio: AudioPresentation?`
 * here and sound effects `val overlay: OverlayPresentation?`, with no
 * `ImageWithMusicPresentation` in sight.
 *
 * [mode] selects which component is on the surface while the others stay composed and
 * intact — deliberately the same shape as [PresentationState.blackout]. That buys
 * "INFO then back returns to the same image" for free, exactly as "blank then un-blank"
 * already does, and means audio can later arrive as a sibling field that INFO does not
 * switch off.
 */
data class Scene(
    val mode: SceneMode = SceneMode.Visual,
    val visual: VisualPresentation = VisualPresentation(),
    val info: InfoPanel? = null,
    val infoBackground: ContentId? = null,
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

    /**
     * Monotonic, one increment per change of intent by the GM.
     *
     * Not incremented when a transition finishes — completion is not a new intent.
     */
    val revision: Long = 0L,

    /**
     * The cross-dissolve currently running, or null when the surface is settled.
     *
     * Null in the settled case is load-bearing: with no transition the renderer composes
     * exactly one layer and ticks no frame clock, so a campaign configured for `cut` gets
     * v0.1's render path unchanged rather than a dormant animation harness.
     */
    val transition: ActiveTransition? = null,
)

// The projection the renderers use lives in Frame.kt as `PresentationState.frame()`.
// The old `effectiveVisual()` was deleted with v0.2: it returned a VisualPresentation,
// which cannot express "black" distinctly from "no image", so once INFO existed a
// transition written against it could not tell blanking apart from an empty scene.
