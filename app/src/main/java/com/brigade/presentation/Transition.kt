package com.brigade.presentation

/**
 * The fixed set of transitions the app knows how to draw.
 *
 * A small sealed set plus one `when` in `render/`, not a class hierarchy with a `draw()`
 * each (§16 Trap 7). The `when` is the extension point: adding a kind is one constant and
 * one branch, and the compiler finds the branch for you.
 *
 * There is no user-facing transition authoring system. A campaign picks one of these by
 * name in `Campagne.md`.
 */
enum class TransitionKind { Cut, Fade, Watercolor }

/**
 * A transition as configured for a campaign: which one, and how long.
 *
 * Duration lives here rather than as a constant in `render/` because it is part of the
 * state both windows read. A duration known only to the renderer would be a second place
 * the two windows could disagree.
 */
data class TransitionSpec(
    val kind: TransitionKind,
    val durationMillis: Int,
) {
    companion object {
        val CUT = TransitionSpec(TransitionKind.Cut, 0)
        val FADE = TransitionSpec(TransitionKind.Fade, 320)
        val WATERCOLOR = TransitionSpec(TransitionKind.Watercolor, 900)

        /**
         * Resolves the campaign's `transition:` key.
         *
         * An unrecognised name falls back to [CUT] rather than to something pretty: a typo
         * must be *visible*, because the alternative is a GM who thinks they configured
         * watercolor and cannot work out why it looks wrong.
         */
        fun byName(name: String?): TransitionSpec = when (name?.trim()?.lowercase()) {
            "watercolor" -> WATERCOLOR
            "fade" -> FADE
            "cut" -> CUT
            null -> WATERCOLOR // no campaign config at all: the reason this feature exists
            else -> CUT
        }
    }
}

/**
 * A cross-dissolve in progress.
 *
 * ### How two windows stay in step
 *
 * [startNanos] is stamped **once**, in the store, at the moment the state changed. Each
 * window then computes progress as a pure function of it while sampling its own frame
 * clock — so they are synchronised by construction, with no shared animation object and
 * without the state itself ticking at 60 Hz.
 *
 * It also means a window that attaches *mid*-transition — HDMI hotplug, routine on DeX —
 * picks up the correct progress immediately. A renderer that remembered its own "previous
 * frame" would have nothing to show.
 *
 * Never persisted: [PresentationSnapshot] drops it, and it is always null after `restore`.
 */
data class ActiveTransition(
    /** What the players were seeing. Held in state because *both* windows need it. */
    val from: Frame,
    val spec: TransitionSpec,
    val startNanos: Long,
    /** Varies the wash between consecutive dissolves. Both windows read the same value. */
    val seed: Int,
) {
    /** Clamped to `[0,1]`. Returns 1 for a zero-length spec, so a cut is simply "already done". */
    fun progressAt(nowNanos: Long): Float {
        if (spec.durationMillis <= 0) return 1f
        val elapsedMillis = (nowNanos - startNanos) / 1_000_000f
        return (elapsedMillis / spec.durationMillis).coerceIn(0f, 1f)
    }
}
