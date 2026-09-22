package com.gamehost.presentation

/**
 * Which control in the bar is currently *live*.
 *
 * A readout of presentation state, never of what was last tapped (§6.1). Computed by one
 * pure function so the bar cannot drift out of agreement with the player surface — the
 * failure being guarded against is tapping INFO while slot 3 is live and leaving slot 3
 * lit while the players see the info panel.
 */
sealed interface ActiveControl {
    data object Blackout : ActiveControl
    data object Info : ActiveControl
    data class Slot(val id: SlotId) : ActiveControl

    /** Something is shown, but not from a slot — *Afficher* straight from the browser. */
    data object None : ActiveControl
}

fun PresentationState.activeControl(): ActiveControl = when {
    // Same precedence as frame(): NOIR overrides INFO overrides the image.
    blackout -> ActiveControl.Blackout
    scene.mode == SceneMode.Info -> ActiveControl.Info
    liveSlot != null -> ActiveControl.Slot(liveSlot)
    else -> ActiveControl.None
}
