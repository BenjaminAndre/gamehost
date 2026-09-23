package com.brigade.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Link paths are resolved by string arithmetic rather than by walking the tree, so the whole
 * rule is testable on the JVM — where CI is the only check there is.
 */
class ContentPathRelativeTest {

    private val personnages = ContentPath("Personnages")
    private val deep = ContentPath("Cartes/Donjon/Etage2")

    @Test
    fun `a sibling resolves inside the note's own folder`() {
        assertEquals(
            ContentPath("Personnages/Jade-Fox.png"),
            ContentPath.relativeTo(personnages, "Jade-Fox.png"),
        )
    }

    @Test
    fun `a subfolder is appended`() {
        assertEquals(
            ContentPath("Personnages/Portraits/Jade-Fox.png"),
            ContentPath.relativeTo(personnages, "Portraits/Jade-Fox.png"),
        )
    }

    @Test
    fun `dot-dot climbs one level`() {
        assertEquals(
            ContentPath("Portraits/Jade-Fox.png"),
            ContentPath.relativeTo(personnages, "../Portraits/Jade-Fox.png"),
        )
    }

    @Test
    fun `several dot-dots climb several levels`() {
        assertEquals(
            ContentPath("Cartes/x.png"),
            ContentPath.relativeTo(deep, "../../x.png"),
        )
    }

    @Test
    fun `a single dot is a no-op`() {
        assertEquals(
            ContentPath("Personnages/x.png"),
            ContentPath.relativeTo(personnages, "./x.png"),
        )
    }

    @Test
    fun `a link from the campaign root resolves against the root`() {
        assertEquals(ContentPath("x.png"), ContentPath.relativeTo(ContentPath(""), "x.png"))
    }

    /** A link pointing outside the campaign cannot be resolved, and must not wrap around. */
    @Test
    fun `climbing above the campaign root fails rather than wrapping`() {
        assertNull(ContentPath.relativeTo(personnages, "../../x.png"))
        assertNull(ContentPath.relativeTo(ContentPath(""), "../x.png"))
    }

    @Test
    fun `a target that resolves to nothing is null`() {
        assertNull(ContentPath.relativeTo(personnages, ".."))
    }

    @Test
    fun `parent drops the last segment`() {
        assertEquals(ContentPath("Cartes/Donjon"), ContentPath("Cartes/Donjon/x.png").parent)
        assertEquals(ContentPath(""), ContentPath("x.png").parent)
    }
}
