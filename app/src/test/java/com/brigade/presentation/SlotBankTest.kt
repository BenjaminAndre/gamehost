package com.brigade.presentation

import com.brigade.content.ContentId
import com.brigade.content.ContentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotBankTest {

    private val jadePath = ContentPath("Portraits/Jade-Fox.png")
    private val jadeId = ContentId("doc://jade-fox")
    private val tavernePath = ContentPath("Cartes/Taverne.jpg")
    private val taverneId = ContentId("doc://taverne")

    private val notePath = ContentPath("Personnages/Jade-Fox.md")
    private val noteBar = NoteBar(name = "Jade-Fox", element = WuXing.Metal, faction = "Lotus")

    private fun SlotBank.assignImage(slot: SlotId, path: ContentPath, id: ContentId, name: String) =
        assign(slot, path, name, imageId = id)

    @Test
    fun `a new bank has exactly six empty slots`() {
        val bank = SlotBank()
        assertEquals(SLOT_COUNT, bank.state.value.slots.size)
        assertTrue(bank.state.value.slots.all { it.content == SlotContent.Empty })
    }

    @Test
    fun `assign fills the slot`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(0), jadePath, jadeId, "Jade-Fox.png")

        assertEquals(
            SlotContent.Filled(jadePath, "Jade-Fox.png", jadeId),
            bank.contentOf(SlotId(0)),
        )
    }

    /** Overwriting is deliberate: mid-session a confirmation dialog is worse than a mistake. */
    @Test
    fun `assigning over an occupied slot overwrites it`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(3), jadePath, jadeId, "Jade-Fox.png")
        bank.assignImage(SlotId(3), tavernePath, taverneId, "Taverne.jpg")

        assertEquals(
            SlotContent.Filled(tavernePath, "Taverne.jpg", taverneId),
            bank.contentOf(SlotId(3)),
        )
    }

    @Test
    fun `assigning one slot leaves the others alone`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(1), jadePath, jadeId, "Jade-Fox.png")

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(0)))
        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(5)))
    }

    @Test
    fun `clear empties the slot`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(2), jadePath, jadeId, "Jade-Fox.png")
        bank.clear(SlotId(2))

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(2)))
    }

    @Test
    fun `paths omit empty slots and are keyed by index`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(0), jadePath, jadeId, "Jade-Fox.png")
        bank.assignImage(SlotId(4), tavernePath, taverneId, "Taverne.jpg")

        assertEquals(mapOf(0 to jadePath, 4 to tavernePath), bank.state.value.paths())
    }

    @Test
    fun `a bank loaded from disk starts unresolved`() {
        val state = SlotBankState.unresolved(mapOf(2 to jadePath))

        assertEquals(SlotContent.Missing(jadePath), state[SlotId(2)].content)
        assertEquals(SlotContent.Empty, state[SlotId(0)].content)
        assertEquals(SLOT_COUNT, state.slots.size)
    }

    @Test
    fun `resolving keeps the stored path`() {
        val bank = SlotBank(SlotBankState.unresolved(mapOf(1 to jadePath)))
        bank.markResolved(SlotId(1), "Jade-Fox.png", jadeId)

        assertEquals(
            SlotContent.Filled(jadePath, "Jade-Fox.png", jadeId),
            bank.contentOf(SlotId(1)),
        )
    }

    /** A renamed file must read as *missing*, not as an empty slot the GM never filled. */
    @Test
    fun `a resolved slot can go missing again`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(1), jadePath, jadeId, "Jade-Fox.png")
        bank.markMissing(SlotId(1))

        assertEquals(SlotContent.Missing(jadePath), bank.contentOf(SlotId(1)))
    }

    @Test
    fun `resolving an empty slot does nothing`() {
        val bank = SlotBank()
        bank.markResolved(SlotId(0), "Jade-Fox.png", jadeId)

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(0)))
    }

    /** Missing slots still round-trip to disk, so the path is not lost by being unreadable. */
    @Test
    fun `missing slots are still persisted`() {
        val bank = SlotBank(SlotBankState.unresolved(mapOf(3 to jadePath)))
        assertEquals(mapOf(3 to jadePath), bank.state.value.paths())
    }

    // ---- Notes ---------------------------------------------------------------------

    @Test
    fun `a slot can hold a note, carrying the image it links`() {
        val bank = SlotBank()
        bank.assign(SlotId(0), notePath, "Jade-Fox.md", imageId = jadeId, note = noteBar)

        val content = bank.contentOf(SlotId(0)) as SlotContent.Filled
        assertEquals(jadeId, content.imageId)
        assertEquals(noteBar, content.note)
        assertTrue(content.isNote)
    }

    /**
     * A note linking no image is a perfectly usable slot: the GM bar fills in and the players
     * get black. It must not be mistaken for a broken one.
     */
    @Test
    fun `a note with no linked image is filled, not missing`() {
        val bank = SlotBank()
        bank.assign(SlotId(1), notePath, "Jade-Fox.md", imageId = null, note = noteBar)

        val content = bank.contentOf(SlotId(1)) as SlotContent.Filled
        assertNull(content.imageId)
        assertTrue(content.isNote)
    }

    @Test
    fun `an image slot is not a note`() {
        val bank = SlotBank()
        bank.assignImage(SlotId(2), jadePath, jadeId, "Jade-Fox.png")

        assertFalse((bank.contentOf(SlotId(2)) as SlotContent.Filled).isNote)
    }

    /** Only the path is persisted, so a note slot survives a restart like any other. */
    @Test
    fun `a note slot persists as its own path, not its image's`() {
        val bank = SlotBank()
        bank.assign(SlotId(0), notePath, "Jade-Fox.md", imageId = jadeId, note = noteBar)

        assertEquals(mapOf(0 to notePath), bank.state.value.paths())
    }

    @Test
    fun `re-resolving a note slot replaces its image and bar in place`() {
        val bank = SlotBank(SlotBankState.unresolved(mapOf(0 to notePath)))
        bank.markResolved(SlotId(0), "Jade-Fox.md", jadeId, noteBar)

        val updated = NoteBar(name = "Jade-Fox", element = WuXing.Eau)
        bank.markResolved(SlotId(0), "Jade-Fox.md", taverneId, updated)

        val content = bank.contentOf(SlotId(0)) as SlotContent.Filled
        assertEquals(notePath, content.path)
        assertEquals(taverneId, content.imageId)
        assertEquals(updated, content.note)
    }
}
