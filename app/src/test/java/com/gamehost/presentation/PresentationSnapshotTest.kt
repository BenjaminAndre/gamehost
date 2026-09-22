package com.gamehost.presentation

import com.gamehost.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationSnapshotTest {

    private val jadeFox = ContentId("doc://jade-fox")

    @Test
    fun `a full state round-trips`() {
        val original = PresentationState(
            scene = Scene(VisualPresentation(VisualSource.Image(jadeFox), ScalingMode.Fill)),
            blackout = false,
            liveSlot = SlotId(4),
        )

        val restored = original.toSnapshot().toState(forceBlackout = false)

        assertEquals(VisualSource.Image(jadeFox), restored.scene.visual.source)
        assertEquals(ScalingMode.Fill, restored.scene.visual.scaling)
        assertEquals(SlotId(4), restored.liveSlot)
        assertFalse(restored.blackout)
    }

    @Test
    fun `an empty scene round-trips`() {
        val restored = PresentationState().toSnapshot().toState(forceBlackout = false)

        assertEquals(VisualSource.None, restored.scene.visual.source)
        assertNull(restored.liveSlot)
    }

    /**
     * Cold start after a crash comes up blanked: better a black screen and one tap than
     * the last image reappearing in front of the players during relaunch.
     */
    @Test
    fun `forceBlackout blanks a restored scene without losing it`() {
        val snapshot = PresentationState(
            scene = Scene(VisualPresentation(VisualSource.Image(jadeFox))),
            blackout = false,
        ).toSnapshot()

        val restored = snapshot.toState(forceBlackout = true)

        assertTrue(restored.blackout)
        assertEquals(VisualSource.Image(jadeFox), restored.scene.visual.source)
        assertEquals(VisualSource.None, restored.effectiveVisual().source)
    }

    @Test
    fun `an unknown scaling mode degrades to Fit rather than throwing`() {
        val snapshot = PresentationSnapshot(
            imageId = jadeFox.value,
            scaling = "SomethingFromAFutureVersion",
            blackout = false,
            liveSlot = null,
        )

        assertEquals(ScalingMode.Fit, snapshot.toState(forceBlackout = false).scene.visual.scaling)
    }
}
