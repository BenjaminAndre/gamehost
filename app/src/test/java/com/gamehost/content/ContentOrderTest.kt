package com.gamehost.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentOrderTest {

    @Test
    fun `folders sort before files`() {
        val sorted = listOf(
            image("2", "aaa.png"),
            folder("1", "zzz"),
        ).sortedWith(ContentOrder.comparator)

        assertEquals(listOf("zzz", "aaa.png"), sorted.map { it.displayName })
    }

    /** Numbered map sequences are the whole reason this is not a plain string sort. */
    @Test
    fun `digit runs compare numerically`() {
        assertTrue(ContentOrder.compareNatural("carte2.png", "carte10.png") < 0)
        assertTrue(ContentOrder.compareNatural("carte10.png", "carte9.png") > 0)
        assertEquals(0, ContentOrder.compareNatural("carte7.png", "carte7.png"))
    }

    @Test
    fun `leading zeros do not change the order`() {
        assertTrue(ContentOrder.compareNatural("salle007", "salle8") < 0)
    }

    /** Raw code-point ordering would put Élise after Zorro (§21.1). */
    @Test
    fun `accents collate as French expects`() {
        assertTrue(ContentOrder.compareNatural("Élise.png", "Zorro.png") < 0)
        // SECONDARY strength: the accent is a real difference, and the unaccented form
        // sorts first — rather than É landing somewhere past Z.
        assertTrue(ContentOrder.compareNatural("Elise", "Élise") < 0)
    }

    @Test
    fun `comparison is case insensitive`() {
        assertEquals(0, ContentOrder.compareNatural("Taverne.png", "taverne.png"))
    }

    @Test
    fun `a prefix sorts before the longer name`() {
        assertTrue(ContentOrder.compareNatural("carte", "carte-nord") < 0)
    }

    @Test
    fun `sorting a whole folder is stable and complete`() {
        val sorted = listOf(
            image("a", "carte10.png"),
            image("b", "carte2.png"),
            folder("c", "Élise"),
            folder("d", "Zorro"),
            image("e", "carte1.png"),
        ).sortedWith(ContentOrder.comparator)

        assertEquals(
            listOf("Élise", "Zorro", "carte1.png", "carte2.png", "carte10.png"),
            sorted.map { it.displayName },
        )
    }
}
