package com.brigade.presentation

import com.brigade.content.ContentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentationSnapshotTest {

    private val jadeFox = ContentId("doc://jade-fox")

    @Test
    fun `a full state round-trips`() {
        val original = PresentationState(
            scene = Scene(visual = VisualPresentation(VisualSource.Image(jadeFox), ScalingMode.Fill)),
            liveSlot = SlotId(4),
        )

        val restored = original.toSnapshot().toState(startInInfo = false)

        assertEquals(VisualSource.Image(jadeFox), restored.scene.visual.source)
        assertEquals(ScalingMode.Fill, restored.scene.visual.scaling)
        assertEquals(SlotId(4), restored.liveSlot)
        assertEquals(SceneMode.Visual, restored.scene.mode)
    }

    @Test
    fun `an empty scene round-trips`() {
        val restored = PresentationState().toSnapshot().toState(startInInfo = false)

        assertEquals(VisualSource.None, restored.scene.visual.source)
        assertNull(restored.liveSlot)
    }

    /**
     * Cold start after a crash comes up on INFO: better the info panel — or black, if the
     * campaign configures none — than the last image reappearing in front of the players
     * during relaunch. The scene survives underneath, so one tap resumes.
     */
    @Test
    fun `startInInfo covers a restored scene without losing it`() {
        val snapshot = PresentationState(
            scene = Scene(visual = VisualPresentation(VisualSource.Image(jadeFox))),
            liveSlot = SlotId(2),
        ).toSnapshot()

        val restored = snapshot.toState(startInInfo = true)

        assertEquals(SceneMode.Info, restored.scene.mode)
        assertEquals(VisualSource.Image(jadeFox), restored.scene.visual.source)
        assertEquals(SlotId(2), restored.liveSlot)
        // No panel yet — the campaign file has not been read — so black, deliberately.
        assertEquals(Frame.Black, restored.frame())
    }

    /**
     * The panel is derived from Campagne.md and never persisted. The application graph
     * loads it separately, *after* this restore, which is why the ordering in `AppGraph`
     * matters: `restore` replaces the whole state.
     */
    @Test
    fun `the info panel is never persisted`() {
        val snapshot = PresentationState(
            scene = Scene(mode = SceneMode.Info, info = InfoPanel(title = "Renard de Jade")),
        ).toSnapshot()

        assertNull(snapshot.toState(startInInfo = true).scene.info)
    }

    @Test
    fun `an unknown scaling mode degrades to Fit rather than throwing`() {
        val snapshot = PresentationSnapshot(
            imageId = jadeFox.value,
            scaling = "SomethingFromAFutureVersion",
            liveSlot = null,
        )

        assertEquals(ScalingMode.Fit, snapshot.toState(startInInfo = false).scene.visual.scaling)
    }
}
