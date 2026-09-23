package com.brigade.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPathTest {

    @Test
    fun `segments split on slashes`() {
        assertEquals(
            listOf("Cartes", "Donjon", "Salle-4.png"),
            ContentPath("Cartes/Donjon/Salle-4.png").segments,
        )
    }

    @Test
    fun `an image at the campaign root is a single segment`() {
        val path = ContentPath.of(emptyList(), "Taverne.jpg")
        assertEquals("Taverne.jpg", path.value)
        assertEquals("Taverne.jpg", path.fileName)
    }

    @Test
    fun `a nested image joins its folders`() {
        val path = ContentPath.of(listOf("Cartes", "Donjon"), "Salle-4.png")
        assertEquals("Cartes/Donjon/Salle-4.png", path.value)
        assertEquals("Salle-4.png", path.fileName)
    }

    @Test
    fun `empty segments are dropped rather than producing double slashes`() {
        assertEquals("Cartes/Salle.png", ContentPath.of(listOf("Cartes", ""), "Salle.png").value)
    }

    @Test
    fun `an empty path is empty`() {
        assertTrue(ContentPath("").isEmpty)
        assertEquals("", ContentPath("").fileName)
    }

    @Test
    fun `round trips through segments`() {
        val original = ContentPath("Portraits/PNJ/Jade-Fox.png")
        assertEquals(original.value, ContentPath.of(original.segments).value)
    }
}
