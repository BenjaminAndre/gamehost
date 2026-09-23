package com.brigade.presentation

import com.brigade.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerOverlayTest {

    private val jadeFox = ContentId("doc://jade-fox")

    private var now = 0L
    private fun store() = PresentationStore(nowNanos = { now })
    private fun advanceSeconds(seconds: Long) {
        now += seconds * 1_000_000_000L
    }

    private fun minutes(n: Int) = TimerOverlay(startNanos = 0L, durationMillis = n * 60_000L)
    private fun atSeconds(s: Long) = s * 1_000_000_000L

    // ---- Burning -------------------------------------------------------------------

    @Test
    fun `a fresh stick is unburnt and a spent one is fully burnt`() {
        val timer = minutes(5)

        assertEquals(0f, timer.burnedAt(0L), 0.0001f)
        assertEquals(0.5f, timer.burnedAt(atSeconds(150)), 0.0001f)
        assertEquals(1f, timer.burnedAt(atSeconds(300)), 0.0001f)
    }

    @Test
    fun `burning clamps at both ends rather than running past`() {
        val timer = minutes(1)

        assertEquals(1f, timer.burnedAt(atSeconds(3600)), 0.0001f)
        assertEquals(0f, timer.burnedAt(-atSeconds(10)), 0.0001f)
    }

    @Test
    fun `remaining counts down and stops at zero`() {
        val timer = minutes(2)

        assertEquals(120_000L, timer.remainingMillisAt(0L))
        assertEquals(60_000L, timer.remainingMillisAt(atSeconds(60)))
        assertEquals(0L, timer.remainingMillisAt(atSeconds(120)))
        assertEquals(0L, timer.remainingMillisAt(atSeconds(9999)))
    }

    @Test
    fun `burnt out is exactly when nothing remains`() {
        val timer = minutes(1)

        assertFalse(timer.isBurntOutAt(atSeconds(59)))
        assertTrue(timer.isBurntOutAt(atSeconds(60)))
    }

    // ---- Extending -----------------------------------------------------------------

    /**
     * Extending keeps the time already burnt, so the stick visibly grows. Restarting it
     * would reset to full, which tells the players something quite different.
     */
    @Test
    fun `extending lengthens the stick without resetting the burn`() {
        val store = store()
        store.startTimer(2)
        advanceSeconds(90)

        store.extendTimer()

        val timer = store.state.value.scene.overlay!!
        assertEquals(0L, timer.startNanos)
        assertEquals(180_000L, timer.durationMillis)
        assertEquals(90_000L, timer.remainingMillisAt(now))
    }

    /** A minute added to a stick that already went out has to actually give them a minute. */
    @Test
    fun `extending a burnt-out stick lights a fresh one`() {
        val store = store()
        store.startTimer(1)
        advanceSeconds(300)
        assertTrue(store.state.value.scene.overlay!!.isBurntOutAt(now))

        store.extendTimer()

        val timer = store.state.value.scene.overlay!!
        assertEquals(now, timer.startNanos)
        assertEquals(60_000L, timer.remainingMillisAt(now))
    }

    @Test
    fun `extending with nothing lit does nothing`() {
        val store = store()
        store.extendTimer()

        assertNull(store.state.value.scene.overlay)
    }

    // ---- The store -----------------------------------------------------------------

    @Test
    fun `lighting replaces whatever was burning`() {
        val store = store()
        store.startTimer(5)
        advanceSeconds(60)
        store.startTimer(1)

        assertEquals(60_000L, store.state.value.scene.overlay?.durationMillis)
        assertEquals(now, store.state.value.scene.overlay?.startNanos)
    }

    @Test
    fun `clearing puts it out`() {
        val store = store()
        store.startTimer(2)
        store.clearTimer()

        assertNull(store.state.value.scene.overlay)
    }

    // ---- Orthogonality -------------------------------------------------------------

    /**
     * The timer is §14's Overlay: a sibling of the visual, not part of it. Changing what is
     * presented must leave it burning, or a GM could not light one and then keep working.
     */
    @Test
    fun `the stick keeps burning across scene changes`() {
        val store = store()
        store.startTimer(5)
        val lit = store.state.value.scene.overlay

        store.show(jadeFox)
        assertEquals(lit, store.state.value.scene.overlay)

        store.setInfoMode(true)
        assertEquals(lit, store.state.value.scene.overlay)

        store.setInfoMode(false)
        assertEquals(lit, store.state.value.scene.overlay)
    }

    /**
     * The overlay is not part of the picture, so lighting one must not stamp a dissolve —
     * the image the players are looking at should not wash just because a timer started.
     */
    @Test
    fun `lighting a timer does not dissolve the scene`() {
        val store = PresentationStore(spec = { TransitionSpec.WATERCOLOR }, nowNanos = { now })
        store.show(jadeFox)
        advanceSeconds(10)
        val settled = store.state.value.transition

        store.startTimer(5)

        assertEquals(settled, store.state.value.transition)
    }

    @Test
    fun `the frame is unchanged by the timer, so the players' picture is unaffected`() {
        val withoutTimer = PresentationState(
            scene = Scene(visual = VisualPresentation(VisualSource.Image(jadeFox))),
        )
        val withTimer = withoutTimer.copy(
            scene = withoutTimer.scene.copy(overlay = minutes(5)),
        )

        assertEquals(withoutTimer.frame(), withTimer.frame())
    }

    // ---- Persistence ---------------------------------------------------------------

    /** A timer is ephemeral: restoring one that burnt out an hour ago would be nonsense. */
    @Test
    fun `a timer is never restored from a snapshot`() {
        val snapshot = PresentationState(
            scene = Scene(
                visual = VisualPresentation(VisualSource.Image(jadeFox)),
                overlay = minutes(5),
            ),
        ).toSnapshot()

        assertNull(snapshot.toState(startInInfo = false).scene.overlay)
    }

    @Test
    fun `the offered durations are the three the GM asked for`() {
        assertEquals(listOf(1, 2, 5), TimerOverlay.DURATIONS_MINUTES)
        assertNotNull(TimerOverlay.ofMinutes(1, 0L))
    }
}
