package com.brigade.presentation

import com.brigade.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveControlTest {

    private val jadeFox = ContentId("doc://jade-fox")

    private fun showing(slot: SlotId?) = PresentationState(
        scene = Scene(visual = VisualPresentation(VisualSource.Image(jadeFox))),
        liveSlot = slot,
    )

    @Test
    fun `a recalled slot is the live control`() {
        assertEquals(ActiveControl.Slot(SlotId(2)), showing(SlotId(2)).activeControl())
    }

    @Test
    fun `showing straight from the browser lights nothing`() {
        assertEquals(ActiveControl.None, showing(null).activeControl())
    }

    /**
     * The failure this function exists to prevent: tapping INFO while slot 3 is live must
     * not leave slot 3 lit while the players are looking at the info panel.
     */
    @Test
    fun `info mode wins over a live slot, which is remembered underneath`() {
        val state = showing(SlotId(3)).let {
            it.copy(scene = it.scene.copy(mode = SceneMode.Info, info = InfoPanel(title = "x")))
        }

        assertEquals(ActiveControl.Info, state.activeControl())
        // Still remembered, so leaving INFO returns to the same slot.
        assertEquals(SlotId(3), state.liveSlot)
    }

    /** INFO with nothing configured still lights INFO, even though the screen is black. */
    @Test
    fun `info mode with no panel is still the live control`() {
        val state = showing(SlotId(3)).let {
            it.copy(scene = it.scene.copy(mode = SceneMode.Info, info = null))
        }

        assertEquals(ActiveControl.Info, state.activeControl())
    }
}
