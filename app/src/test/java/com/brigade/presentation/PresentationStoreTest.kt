package com.brigade.presentation

import com.brigade.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
     * INFO covers the scene rather than replacing it, so leaving INFO returns to exactly
     * what was there — the property that used to belong to blanking.
     */
    @Test
    fun `info mode preserves the scene and leaving it restores the image`() {
        val store = PresentationStore()
        store.show(jadeFox)

        store.setInfoMode(true)
        assertEquals(VisualSource.Image(jadeFox), store.state.value.scene.visual.source)

        store.setInfoMode(false)
        assertEquals(Frame.Picture(jadeFox, ScalingMode.Fit), store.state.value.frame())
    }

    /** With no campaign info configured, INFO *is* the blank control. */
    @Test
    fun `info mode with nothing configured shows black`() {
        val store = PresentationStore()
        store.show(jadeFox)
        store.setInfoMode(true)

        assertEquals(Frame.Black, store.state.value.frame())
    }

    @Test
    fun `showing an image leaves info mode`() {
        val store = PresentationStore()
        store.setInfoMode(true)
        store.show(jadeFox)

        assertEquals(SceneMode.Visual, store.state.value.scene.mode)
        assertEquals(Frame.Picture(jadeFox, ScalingMode.Fit), store.state.value.frame())
    }

    @Test
    fun `clearing the scene returns to Visual mode with nothing shown`() {
        val store = PresentationStore()
        store.show(jadeFox)
        store.setInfoMode(true)
        store.clearScene()

        assertEquals(SceneMode.Visual, store.state.value.scene.mode)
        assertEquals(VisualSource.None, store.state.value.scene.visual.source)
        assertEquals(Frame.Black, store.state.value.frame())
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
        store.setInfoMode(true)
        store.setScaling(ScalingMode.Fit)

        assertEquals(start + 3, store.state.value.revision)
    }

    @Test
    fun `info mode projects to black without discarding the scene`() {
        val state = PresentationState(
            scene = Scene(
                mode = SceneMode.Info,
                visual = VisualPresentation(VisualSource.Image(jadeFox), ScalingMode.Fill),
            ),
        )

        assertEquals(Frame.Black, state.frame())
        // The scene is intact underneath, which is what makes leaving INFO free.
        assertEquals(VisualSource.Image(jadeFox), state.scene.visual.source)
        assertEquals(ScalingMode.Fill, state.scene.visual.scaling)
    }

    // ---- frame() projection ------------------------------------------------------

    @Test
    fun `an empty scene projects to black`() {
        assertEquals(Frame.Black, PresentationState().frame())
    }

    @Test
    fun `info mode projects to the panel`() {
        val panel = InfoPanel(title = "Renard de Jade")
        val state = PresentationState(scene = Scene(mode = SceneMode.Info, info = panel))

        assertEquals(Frame.Info(panel, null), state.frame())
    }

    /**
     * INFO doubles as the blank control, so this branch is not an error path — it is the
     * "nothing specific, I'm preparing" state, and the only way to reach black deliberately.
     */
    @Test
    fun `info mode with no panel projects to black rather than throwing`() {
        val state = PresentationState(scene = Scene(mode = SceneMode.Info, info = null))

        assertEquals(Frame.Black, state.frame())
    }
}
