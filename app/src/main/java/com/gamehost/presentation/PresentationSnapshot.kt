package com.gamehost.presentation

import com.gamehost.content.ContentId

/**
 * [PresentationState] flattened to primitives, for surviving process death.
 *
 * Five scalars, no serialization library, no `Parcelable` — which is the payoff for
 * [ContentId] wrapping a `String` rather than a `Uri`.
 *
 * Configuration changes do *not* go through here: presentation state lives on the
 * application graph and survives those for free. This exists only for a cold start
 * after the process was killed.
 */
data class PresentationSnapshot(
    val imageId: String?,
    val scaling: String,
    val blackout: Boolean,
    val liveSlot: Int?,
)

interface SnapshotStore {
    fun load(): PresentationSnapshot?
    fun save(snapshot: PresentationSnapshot)
}

fun PresentationState.toSnapshot(): PresentationSnapshot = PresentationSnapshot(
    imageId = (scene.visual.source as? VisualSource.Image)?.id?.value,
    scaling = scene.visual.scaling.name,
    blackout = blackout,
    liveSlot = liveSlot?.index,
)

/**
 * @param forceBlackout the caller's safety policy. On a cold start after a crash or a
 * reboot, silently blasting the last image onto the players' screen during relaunch is
 * a worse failure than a black screen plus one tap, so the application graph passes
 * true. A reconnecting display does **not** come through here — it re-renders live
 * state, because a display that could mutate state would be the source of truth
 * (§16 Trap 5).
 */
fun PresentationSnapshot.toState(forceBlackout: Boolean): PresentationState {
    val source = imageId?.let { VisualSource.Image(ContentId(it)) } ?: VisualSource.None
    val mode = ScalingMode.entries.firstOrNull { it.name == scaling } ?: ScalingMode.Fit
    return PresentationState(
        scene = Scene(visual = VisualPresentation(source = source, scaling = mode)),
        blackout = forceBlackout || blackout,
        liveSlot = liveSlot?.let(::SlotId),
    )
}
