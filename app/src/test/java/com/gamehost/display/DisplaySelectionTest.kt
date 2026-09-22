package com.gamehost.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionTest {

    @Test
    fun `a tablet on its own has no player display`() {
        assertNull(pickPlayerDisplay(listOf(info(0)), selfDisplayId = 0))
    }

    @Test
    fun `an empty list has no player display`() {
        assertNull(pickPlayerDisplay(emptyList(), selfDisplayId = 0))
    }

    @Test
    fun `the display the GM UI is on is never chosen`() {
        val picked = pickPlayerDisplay(listOf(info(0), info(1, presentationFlag = true)), selfDisplayId = 0)
        assertEquals(1, picked?.id)
    }

    /**
     * The DeX case. In standard DeX the GM Activity launches onto the external display,
     * so "external" and "player" are not the same thing — the only safe rule is "not the
     * display I am on".
     */
    @Test
    fun `when the GM UI is on the external display the tablet panel is the player surface`() {
        val picked = pickPlayerDisplay(listOf(info(0), info(1, presentationFlag = true)), selfDisplayId = 1)
        assertEquals(0, picked?.id)
    }

    @Test
    fun `private displays are skipped`() {
        assertNull(pickPlayerDisplay(listOf(info(0), info(2, isPrivate = true)), selfDisplayId = 0))
    }

    @Test
    fun `a presentation-capable display wins over one that is not`() {
        val picked = pickPlayerDisplay(
            listOf(info(0), info(1), info(2, presentationFlag = true)),
            selfDisplayId = 0,
        )
        assertEquals(2, picked?.id)
    }

    /** An HDMI display that does not advertise the flag is still usable. */
    @Test
    fun `a display without the presentation flag is used when it is all there is`() {
        val picked = pickPlayerDisplay(listOf(info(0), info(1)), selfDisplayId = 0)
        assertEquals(1, picked?.id)
    }

    @Test
    fun `ties keep the platform's preference order`() {
        val picked = pickPlayerDisplay(
            listOf(info(0), info(1, presentationFlag = true), info(2, presentationFlag = true)),
            selfDisplayId = 0,
        )
        assertEquals(1, picked?.id)
    }

    private fun info(
        id: Int,
        presentationFlag: Boolean = false,
        isPrivate: Boolean = false,
    ) = DisplayInfo(
        id = id,
        name = "display-$id",
        widthPx = 1920,
        heightPx = 1080,
        presentationFlag = presentationFlag,
        isPrivate = isPrivate,
    )
}
