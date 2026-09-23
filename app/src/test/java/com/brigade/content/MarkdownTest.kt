package com.brigade.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    private val note = """
        ---
        element: Métal
        faction: Secte du Lotus
        ---

        # Jade Fox

        ![[Jade-Fox.png]]

        Le magistrat sait qu'elle ment.

        ![Sa lame](../Objets/lame.jpg)
    """.trimIndent()

    // ---- Frontmatter ---------------------------------------------------------------

    @Test
    fun `frontmatter is the block between the fences`() {
        assertEquals("element: Métal\nfaction: Secte du Lotus\n", Markdown.frontmatter(note))
    }

    @Test
    fun `a note with no frontmatter has none`() {
        assertNull(Markdown.frontmatter("# Jade Fox\n\n![[x.png]]\n"))
    }

    @Test
    fun `body excludes the frontmatter`() {
        assertTrue(Markdown.body(note).startsWith("\n# Jade Fox"))
        assertTrue(!Markdown.body(note).contains("faction:"))
    }

    @Test
    fun `body is the whole document when there is no frontmatter`() {
        assertEquals("# Jade Fox\n", Markdown.body("# Jade Fox\n"))
    }

    // ---- Links ---------------------------------------------------------------------

    @Test
    fun `both syntaxes are found, in document order`() {
        assertEquals(listOf("Jade-Fox.png", "../Objets/lame.jpg"), Markdown.imageLinks(note))
    }

    @Test
    fun `a size hint after the pipe is dropped`() {
        assertEquals(listOf("Jade-Fox.png"), Markdown.imageLinks("![[Jade-Fox.png|300]]"))
    }

    @Test
    fun `a title after the target is dropped`() {
        assertEquals(listOf("lame.jpg"), Markdown.imageLinks("""![Sa lame](lame.jpg "La lame")"""))
    }

    @Test
    fun `angle brackets and percent-encoded spaces both survive`() {
        assertEquals(listOf("Mes Cartes/x.png"), Markdown.imageLinks("![](<Mes Cartes/x.png>)"))
        assertEquals(listOf("Mes Cartes/x.png"), Markdown.imageLinks("![](Mes%20Cartes/x.png)"))
    }

    /** Frontmatter can legitimately contain something link-shaped; it is not the body. */
    @Test
    fun `links inside frontmatter are ignored`() {
        val text = "---\nbanner: \"![[not-a-body-link.png]]\"\n---\n\n![[real.png]]\n"
        assertEquals(listOf("real.png"), Markdown.imageLinks(text))
    }

    @Test
    fun `a plain wikilink is not an image embed`() {
        // [[Autre note]] links, ![[image.png]] embeds. Only the embed is an image.
        assertEquals(listOf("image.png"), Markdown.imageLinks("[[Autre note]]\n![[image.png]]\n"))
    }

    @Test
    fun `a note with no images yields nothing`() {
        assertTrue(Markdown.imageLinks("# Jade Fox\n\nRien ici.\n").isEmpty())
    }

    /** Targets are returned raw — deciding what resolves is the repository's job. */
    @Test
    fun `remote targets are reported rather than filtered here`() {
        assertEquals(listOf("https://example.com/x.png"), Markdown.imageLinks("![](https://example.com/x.png)"))
    }
}
