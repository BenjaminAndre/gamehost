package com.gamehost.presentation

import com.gamehost.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationStoreTest {

    private val jadeFox = ContentId("doc://jade-fox")
    private val taverne = ContentId("doc://taverne")

    @Test
    fun `show puts the image in the scene`() {
        val store = PresentationStore()
        store.show(jadeFox)

        assertEquals(VisualSource.Image(jadeFox), store.state.value.scene.visual.source)
    }

    @Test
    fun `show records which slot it came from`() {
        val store = PresentationStore()
        store.show(jadeFox, fromSlot = SlotId(2))

        assertEquals(SlotId(2), store.state.value.liveSlot)
    }

    /** *Afficher* is ad hoc, so no slot is live afterwards. */
    @Test
    fun `showing ad hoc clears the live slot`() {
        val store = PresentationStore()
        store.show(jadeFox, fromSlot = SlotId(2))
        store.show(taverne, fromSlot = null)

        assertNull(store.state.value.liveSlot)
    }

    /**
     * The property that makes blackout a modifier over the scene rather than a scene of
     * its own: un-blanking returns to exactly what was there.
     */
    @Test
    fun `blackout preserves the scene and un-blackout restores it`() {
        val store = PresentationStore()
        store.show(jadeFox)

        store.setBlackout(true)
        assertEquals(VisualSource.Image(jadeFox), store.state.value.scene.visual.source)
        assertEquals(VisualSource.None, store.state.value.effectiveVisual().source)

        store.setBlackout(false)
        assertEquals(VisualSource.Image(jadeFox), store.state.value.effectiveVisual().source)
    }

    @Test
    fun `toggle flips blackout both ways`() {
        val store = PresentationStore()
        assertFalse(store.state.value.blackout)

        store.toggleBlackout()
        assertTrue(store.state.value.blackout)

        store.toggleBlackout()
        assertFalse(store.state.value.blackout)
    }

    @Test
    fun `showing while blanked reveals immediately`() {
        val store = PresentationStore()
        store.setBlackout(true)
        store.show(jadeFox)

        assertFalse(store.state.value.blackout)
    }

    @Test
    fun `clearing the scene leaves blackout alone`() {
        val store = PresentationStore()
        store.show(jadeFox)
        store.setBlackout(true)
        store.clearScene()

        assertTrue(store.state.value.blackout)
        assertEquals(VisualSource.None, store.state.value.scene.visual.source)
        assertNull(store.state.value.liveSlot)
    }

    @Test
    fun `scaling is carried on the scene, not on the image`() {
        val store = PresentationStore()
        store.show(jadeFox)
        store.setScaling(ScalingMode.Fill)

        assertEquals(ScalingMode.Fill, store.state.value.scene.visual.scaling)
        assertEquals(VisualSource.Image(jadeFox), store.state.value.scene.visual.source)
    }

    @Test
    fun `revision increases on every change`() {
        val store = PresentationStore()
        val start = store.state.value.revision

        store.show(jadeFox)
        store.toggleBlackout()
        store.setScaling(ScalingMode.Fit)

        assertEquals(start + 3, store.state.value.revision)
    }

    @Test
    fun `effectiveVisual keeps the scaling mode while blanked`() {
        val state = PresentationState(
            scene = Scene(VisualPresentation(VisualSource.Image(jadeFox), ScalingMode.Fill)),
            blackout = true,
        )

        assertEquals(VisualSource.None, state.effectiveVisual().source)
        assertEquals(ScalingMode.Fill, state.effectiveVisual().scaling)
    }
}
