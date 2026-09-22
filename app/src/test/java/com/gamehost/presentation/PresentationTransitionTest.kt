package com.gamehost.presentation

import com.gamehost.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationTransitionTest {

    private val jadeFox = ContentId("doc://jade-fox")
    private val taverne = ContentId("doc://taverne")
    private val donjon = ContentId("doc://donjon")

    /** Controllable clock: the store takes `nowNanos` precisely so this is testable. */
    private var now = 0L

    private fun store(spec: TransitionSpec = TransitionSpec.WATERCOLOR) =
        PresentationStore(spec = { spec }, nowNanos = { now })

    private fun advance(millis: Long) {
        now += millis * 1_000_000
    }

    // ---- Stamping -----------------------------------------------------------------

    @Test
    fun `a cut stamps no transition at all`() {
        val store = store(TransitionSpec.CUT)
        store.show(jadeFox)

        assertNull(store.state.value.transition)
    }

    @Test
    fun `a dissolve stamps the outgoing frame and the start time`() {
        val store = store()
        store.show(jadeFox)
        advance(1000)
        store.show(taverne)

        val transition = store.state.value.transition
        assertNotNull(transition)
        assertEquals(Frame.Picture(jadeFox, ScalingMode.Fit), transition!!.from)
        assertEquals(now, transition.startNanos)
    }

    @Test
    fun `showing the image already shown does not restart the dissolve`() {
        val store = store()
        store.show(jadeFox)
        val stamped = store.state.value.transition

        advance(50)
        store.show(jadeFox)

        assertEquals(stamped, store.state.value.transition)
    }

    @Test
    fun `entering info mode dissolves`() {
        val store = store()
        store.restore(
            PresentationState(scene = Scene(info = InfoPanel(title = "Renard de Jade"))),
        )
        store.setInfoMode(true)

        assertNotNull(store.state.value.transition)
    }

    // ---- Interruption -------------------------------------------------------------

    @Test
    fun `interrupting before the midpoint keeps the original outgoing frame`() {
        val store = store(TransitionSpec.FADE) // 320ms
        store.show(jadeFox)
        advance(1000)
        store.show(taverne) // jadeFox -> taverne begins

        advance(80) // 25% through: the players are still mostly seeing jadeFox
        store.show(donjon)

        assertEquals(Frame.Picture(jadeFox, ScalingMode.Fit), store.state.value.transition?.from)
    }

    @Test
    fun `interrupting after the midpoint carries over the interrupted target`() {
        val store = store(TransitionSpec.FADE)
        store.show(jadeFox)
        advance(1000)
        store.show(taverne)

        advance(260) // 81% through: taverne is what the players are mostly seeing
        store.show(donjon)

        assertEquals(Frame.Picture(taverne, ScalingMode.Fit), store.state.value.transition?.from)
    }

    @Test
    fun `a finished transition is not treated as running when the next change arrives`() {
        val store = store(TransitionSpec.FADE)
        store.show(jadeFox)
        advance(1000)
        store.show(taverne)

        advance(5000) // long finished; nothing clears it, and nothing needs to
        store.show(donjon)

        assertEquals(Frame.Picture(taverne, ScalingMode.Fit), store.state.value.transition?.from)
    }

    // ---- Blanking -----------------------------------------------------------------

    @Test
    fun `blanking clamps the duration so NOIR still feels instant`() {
        val store = store() // watercolor, 900ms
        store.show(jadeFox)
        advance(1000)
        store.setBlackout(true)

        assertEquals(
            TransitionSpec.BLANK_MAX_MILLIS,
            store.state.value.transition?.spec?.durationMillis,
        )
    }

    @Test
    fun `blanking keeps the configured kind, only the duration is clamped`() {
        val store = store()
        store.show(jadeFox)
        advance(1000)
        store.toggleBlackout()

        assertEquals(TransitionKind.Watercolor, store.state.value.transition?.spec?.kind)
    }

    /** Regression: a recommended snippet in one design hardcoded `true` and ignored the argument. */
    @Test
    fun `setBlackout honours its argument`() {
        val store = store()
        store.setBlackout(true)
        store.setBlackout(false)

        assertTrue(!store.state.value.blackout)
    }

    // ---- Progress -----------------------------------------------------------------

    @Test
    fun `progress runs from zero to one and clamps at both ends`() {
        val transition = ActiveTransition(
            from = Frame.Black,
            spec = TransitionSpec.FADE, // 320ms
            startNanos = 0L,
            seed = 0,
        )

        assertEquals(0f, transition.progressAt(0L), 0.0001f)
        assertEquals(0.5f, transition.progressAt(160_000_000L), 0.0001f)
        assertEquals(1f, transition.progressAt(320_000_000L), 0.0001f)
        assertEquals(1f, transition.progressAt(9_000_000_000L), 0.0001f)
        assertEquals(0f, transition.progressAt(-5_000_000L), 0.0001f)
    }

    @Test
    fun `a zero-length spec is simply already finished`() {
        val transition = ActiveTransition(Frame.Black, TransitionSpec.CUT, startNanos = 0L, seed = 0)
        assertEquals(1f, transition.progressAt(0L), 0.0001f)
    }

    // ---- Restore ------------------------------------------------------------------

    @Test
    fun `a restored state never arrives mid-dissolve`() {
        val store = store()
        store.show(jadeFox)
        advance(1000)
        store.show(taverne)
        assertNotNull(store.state.value.transition)

        store.restore(PresentationState())

        assertNull(store.state.value.transition)
    }

    // ---- Naming -------------------------------------------------------------------

    @Test
    fun `transition names resolve, and a typo falls back to cut rather than to something pretty`() {
        assertEquals(TransitionSpec.WATERCOLOR, TransitionSpec.byName("watercolor"))
        assertEquals(TransitionSpec.WATERCOLOR, TransitionSpec.byName("  WaterColor "))
        assertEquals(TransitionSpec.FADE, TransitionSpec.byName("fade"))
        assertEquals(TransitionSpec.CUT, TransitionSpec.byName("cut"))
        assertEquals(TransitionSpec.CUT, TransitionSpec.byName("watercolour"))
        assertEquals(TransitionSpec.CUT, TransitionSpec.byName("aquarelle"))
    }

    @Test
    fun `no campaign config at all gets the watercolor default`() {
        assertEquals(TransitionSpec.WATERCOLOR, TransitionSpec.byName(null))
    }
}
