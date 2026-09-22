package com.gamehost.display

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

sealed interface PlayerDisplayStatus {

    /**
     * No usable second surface.
     *
     * A first-class, explained state rather than an error. It happens in normal use:
     * the cable is out, or the tablet is in *standard* DeX rather than dual mode, where
     * the external display becomes the desktop and there is no second surface to own.
     */
    data object Absent : PlayerDisplayStatus

    data class Attached(
        val displayId: Int,
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
    ) : PlayerDisplayStatus {

        val aspectRatio: Float
            get() = if (widthPx > 0 && heightPx > 0) {
                widthPx.toFloat() / heightPx.toFloat()
            } else {
                PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT
            }
    }

    companion object {
        /**
         * The only occurrence of 16:9 in Gamehost (§16 Trap 6), and it is used only
         * when there is no display to ask. Everything else derives the ratio from the
         * real attached display.
         */
        const val DEFAULT_PLAYER_ASPECT: Float = 16f / 9f
    }
}

/**
 * Owns the player-facing window, whatever that turns out to mean on a given device.
 *
 * This interface is the whole of §16 Trap 10. Every Samsung and DeX quirk, and every
 * future per-device strategy, lives behind it; the GM UI and the renderer see only
 * [PlayerDisplayStatus].
 *
 * Concretely, it is also the escape hatch: if `android.app.Presentation` turns out not
 * to reach HDMI in the user's DeX configuration, the fallback is a `PlayerActivity`
 * launched with `ActivityOptions.setLaunchDisplayId`, rendering the same
 * `PresentationSurface` against the same process-scoped state. That is a different
 * implementation of this interface and *nothing else in the app changes*.
 */
interface PlayerDisplayHost {

    val status: StateFlow<PlayerDisplayStatus>

    /**
     * Binds window creation to [activity]. Safe to call repeatedly.
     *
     * A `Presentation` is a `Dialog` and needs an Activity context. The host detaches
     * itself on that Activity's `ON_DESTROY` so it cannot leak one.
     */
    fun attach(activity: ComponentActivity)

    fun detach()
}
