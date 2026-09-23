package com.brigade.presentation

import com.brigade.content.NoteDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteBarTest {

    // ---- Elements ------------------------------------------------------------------

    @Test
    fun `the five phases parse from their French names`() {
        assertEquals(WuXing.Terre, WuXing.parse("Terre"))
        assertEquals(WuXing.Feu, WuXing.parse("Feu"))
        assertEquals(WuXing.Eau, WuXing.parse("Eau"))
        assertEquals(WuXing.Metal, WuXing.parse("Métal"))
        assertEquals(WuXing.Bois, WuXing.parse("Bois"))
    }

    /**
     * The accent is the point. A note typed in a hurry without it would otherwise drop the
     * element from the bar with nothing on screen saying why.
     */
    @Test
    fun `matching ignores case, accents and surrounding space`() {
        listOf("métal", "Metal", "METAL", "  MÉTAL  ", "metal").forEach {
            assertEquals("«$it»", WuXing.Metal, WuXing.parse(it))
        }
    }

    @Test
    fun `an unknown or absent value is not an element`() {
        assertNull(WuXing.parse("Foudre"))
        assertNull(WuXing.parse(""))
        assertNull(WuXing.parse(null))
    }

    @Test
    fun `each phase has a distinct emoji`() {
        val emojis = WuXing.entries.map { it.emoji }
        assertEquals(emojis.size, emojis.toSet().size)
    }

    // ---- Building the bar ----------------------------------------------------------

    @Test
    fun `a full note fills every segment`() {
        val bar = NoteBar.from(
            NoteDocument(name = "Jade-Fox", element = "Métal", faction = "Secte du Lotus"),
        )

        assertEquals("Jade-Fox", bar.name)
        assertEquals(WuXing.Metal, bar.element)
        assertEquals("Secte du Lotus", bar.faction)
        assertNull(bar.elementRaw)
    }

    /** A typo must be visible, not silently dropped — same rule as an unknown transition. */
    @Test
    fun `an unrecognised element is kept as text`() {
        val bar = NoteBar.from(NoteDocument(name = "Jade-Fox", element = "Foudre"))

        assertNull(bar.element)
        assertEquals("Foudre", bar.elementRaw)
    }

    @Test
    fun `a bare note still has a name`() {
        val bar = NoteBar.from(NoteDocument(name = "Jade-Fox"))

        assertEquals("Jade-Fox", bar.name)
        assertNull(bar.element)
        assertNull(bar.elementRaw)
        assertNull(bar.faction)
    }

    @Test
    fun `blank properties are treated as absent`() {
        val bar = NoteBar.from(NoteDocument(name = "x", element = "  ", faction = "  "))

        assertNull(bar.elementRaw)
        assertNull(bar.faction)
    }

    /**
     * The date is campaign state, composed at render time. Baking it in would leave every
     * slot showing the in-world date it was resolved on.
     */
    @Test
    fun `the bar carries no date of its own`() {
        val fields = NoteBar::class.java.declaredFields.map { it.name }
        assertEquals(emptyList<String>(), fields.filter { it.contains("date", ignoreCase = true) })
    }

    // ---- The guarantee -------------------------------------------------------------

    /**
     * The note bar is GM-facing. `frame()` must never consult it, or it could reach the
     * player display — which is the whole reason it lives on Scene rather than in Frame.
     */
    @Test
    fun `a note on the scene does not change what the players see`() {
        val withoutNote = PresentationState(
            scene = Scene(visual = VisualPresentation(VisualSource.None)),
        )
        val withNote = withoutNote.copy(
            scene = withoutNote.scene.copy(note = NoteBar(name = "Jade-Fox", faction = "Lotus")),
        )

        assertEquals(withoutNote.frame(), withNote.frame())
    }
}
