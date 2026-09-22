package com.gamehost.presentation

import com.gamehost.content.ContentId
import com.gamehost.content.ContentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotBankTest {

    private val jadePath = ContentPath("Portraits/Jade-Fox.png")
    private val jadeId = ContentId("doc://jade-fox")
    private val tavernePath = ContentPath("Cartes/Taverne.jpg")
    private val taverneId = ContentId("doc://taverne")

    @Test
    fun `a new bank has exactly six empty slots`() {
        val bank = SlotBank()
        assertEquals(SLOT_COUNT, bank.state.value.slots.size)
        assertTrue(bank.state.value.slots.all { it.content == SlotContent.Empty })
    }

    @Test
    fun `assign fills the slot`() {
        val bank = SlotBank()
        bank.assign(SlotId(0), jadePath, jadeId, "Jade-Fox.png")

        assertEquals(SlotContent.Filled(jadePath, jadeId, "Jade-Fox.png"), bank.contentOf(SlotId(0)))
    }

    /** Overwriting is deliberate: mid-session a confirmation dialog is worse than a mistake. */
    @Test
    fun `assigning over an occupied slot overwrites it`() {
        val bank = SlotBank()
        bank.assign(SlotId(3), jadePath, jadeId, "Jade-Fox.png")
        bank.assign(SlotId(3), tavernePath, taverneId, "Taverne.jpg")

        assertEquals(SlotContent.Filled(tavernePath, taverneId, "Taverne.jpg"), bank.contentOf(SlotId(3)))
    }

    @Test
    fun `assigning one slot leaves the others alone`() {
        val bank = SlotBank()
        bank.assign(SlotId(1), jadePath, jadeId, "Jade-Fox.png")

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(0)))
        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(5)))
    }

    @Test
    fun `clear empties the slot`() {
        val bank = SlotBank()
        bank.assign(SlotId(2), jadePath, jadeId, "Jade-Fox.png")
        bank.clear(SlotId(2))

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(2)))
    }

    @Test
    fun `paths omit empty slots and are keyed by index`() {
        val bank = SlotBank()
        bank.assign(SlotId(0), jadePath, jadeId, "Jade-Fox.png")
        bank.assign(SlotId(4), tavernePath, taverneId, "Taverne.jpg")

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
        bank.markResolved(SlotId(1), jadeId, "Jade-Fox.png")

        assertEquals(SlotContent.Filled(jadePath, jadeId, "Jade-Fox.png"), bank.contentOf(SlotId(1)))
    }

    /** A renamed file must read as *missing*, not as an empty slot the GM never filled. */
    @Test
    fun `a resolved slot can go missing again`() {
        val bank = SlotBank()
        bank.assign(SlotId(1), jadePath, jadeId, "Jade-Fox.png")
        bank.markMissing(SlotId(1))

        assertEquals(SlotContent.Missing(jadePath), bank.contentOf(SlotId(1)))
    }

    @Test
    fun `resolving an empty slot does nothing`() {
        val bank = SlotBank()
        bank.markResolved(SlotId(0), jadeId, "Jade-Fox.png")

        assertEquals(SlotContent.Empty, bank.contentOf(SlotId(0)))
    }

    /** Missing slots still round-trip to disk, so the path is not lost by being unreadable. */
    @Test
    fun `missing slots are still persisted`() {
        val bank = SlotBank(SlotBankState.unresolved(mapOf(3 to jadePath)))
        assertEquals(mapOf(3 to jadePath), bank.state.value.paths())
    }
}
