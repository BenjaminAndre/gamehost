package com.brigade.display

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDisplayStatusTest {

    @Test
    fun `aspect ratio comes from the real display`() {
        val status = PlayerDisplayStatus.Attached(1, "HDMI", widthPx = 1920, heightPx = 1080)
        assertEquals(16f / 9f, status.aspectRatio, 0.0001f)
    }

    /**
     * §16 Trap 6: 16:9 is the initial target shape, not a property of anything. A 16:10
     * projector must preview as 16:10, or the preview quietly lies about the letterbox.
     */
    @Test
    fun `a non 16-9 display previews at its own ratio`() {
        val status = PlayerDisplayStatus.Attached(1, "Projector", widthPx = 1920, heightPx = 1200)
        assertEquals(1.6f, status.aspectRatio, 0.0001f)
    }

    @Test
    fun `a nonsense size falls back rather than dividing by zero`() {
        val status = PlayerDisplayStatus.Attached(1, "Broken", widthPx = 0, heightPx = 0)
        assertEquals(PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT, status.aspectRatio, 0.0001f)
    }
}
