package com.gamehost.presentation

import com.gamehost.content.ContentId
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

    @Test
    fun `blanking wins over a live slot`() {
        assertEquals(ActiveControl.Blackout, showing(SlotId(2)).copy(blackout = true).activeControl())
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

    @Test
    fun `blanking wins over info mode, matching frame precedence`() {
        val state = showing(SlotId(3)).let {
            it.copy(
                scene = it.scene.copy(mode = SceneMode.Info, info = InfoPanel(title = "x")),
                blackout = true,
            )
        }

        assertEquals(ActiveControl.Blackout, state.activeControl())
    }
}
