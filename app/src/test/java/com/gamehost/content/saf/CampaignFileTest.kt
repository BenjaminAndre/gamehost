package com.gamehost.content.saf

import com.gamehost.content.CampaignConfig
import com.gamehost.content.ContentPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignFileTest {

    private val full = """
        ---
        gamehost:
          transition: watercolor
          info:
            background: Fonds/parchemin.jpg
            show_lunar_state: true
            entries:
              - title: Le Renard de Jade
              - location: Auberge du Héron Noir
              - date: 1127-03-12
              - Saison: Printemps
        ---

        # Notes de campagne

        Tout ce qui suit est à moi.
    """.trimIndent()

    // ---- Frontmatter extraction ----------------------------------------------------

    @Test
    fun `frontmatter is the block between the fences`() {
        val block = CampaignFile.frontmatter("---\nkey: value\n---\nbody\n")
        assertEquals("key: value\n", block)
    }

    @Test
    fun `a document that does not open with a fence has no frontmatter`() {
        assertNull(CampaignFile.frontmatter("# Just a note\n\n---\nnot frontmatter\n---\n"))
    }

    @Test
    fun `an unterminated block is treated as absent rather than guessed at`() {
        assertNull(CampaignFile.frontmatter("---\nkey: value\nno closing fence\n"))
    }

    @Test
    fun `an empty document has no frontmatter`() {
        assertNull(CampaignFile.frontmatter(""))
    }

    // ---- Parsing -------------------------------------------------------------------

    @Test
    fun `a full file parses`() {
        val config = CampaignFile.parse(full)

        assertEquals("watercolor", config.transition)
        assertEquals(ContentPath("Fonds/parchemin.jpg"), config.info?.background)
        assertTrue(config.info?.showLunarState == true)
    }

    @Test
    fun `entries keep the order they were written in`() {
        val entries = CampaignFile.parse(full).info?.entries.orEmpty()

        assertEquals(listOf("title", "location", "date", "Saison"), entries.map { it.key })
    }

    @Test
    fun `accented values survive intact`() {
        val entries = CampaignFile.parse(full).info?.entries.orEmpty()

        assertEquals("Auberge du Héron Noir", entries.first { it.key == "location" }.value)
    }

    /**
     * The trap. YAML resolves an unquoted `1127-03-12` to a timestamp, so the value never
     * arrives as a string, and `Date.toString()` would hand back a locale-formatted mess.
     * A silent shift here would misdate the panel and the moon by days, for a date four
     * centuries before the Gregorian reform.
     */
    @Test
    fun `an unquoted twelfth-century date round-trips to ISO`() {
        val entries = CampaignFile.parse(full).info?.entries.orEmpty()

        assertEquals("1127-03-12", entries.first { it.key == "date" }.value)
    }

    @Test
    fun `a quoted date is left exactly alone`() {
        val config = CampaignFile.parse(
            "---\ngamehost:\n  info:\n    entries:\n      - date: \"1127-03-12\"\n---\n",
        )

        assertEquals("1127-03-12", config.info?.entries?.first()?.value)
    }

    // ---- Degrading gracefully ------------------------------------------------------

    @Test
    fun `a note with no frontmatter yields an empty config`() {
        assertEquals(CampaignConfig.EMPTY, CampaignFile.parse("# Ma campagne\n\nDes notes.\n"))
    }

    @Test
    fun `frontmatter without a gamehost key yields an empty config`() {
        assertEquals(
            CampaignConfig.EMPTY,
            CampaignFile.parse("---\ntags: [campagne]\naliases: [Renard]\n---\n"),
        )
    }

    @Test
    fun `a transition on its own is fine, with no info block`() {
        val config = CampaignFile.parse("---\ngamehost:\n  transition: fade\n---\n")

        assertEquals("fade", config.transition)
        assertNull(config.info)
    }

    @Test
    fun `an info block on its own is fine, with no transition`() {
        val config = CampaignFile.parse(
            "---\ngamehost:\n  info:\n    entries:\n      - Lieu: Taverne\n---\n",
        )

        assertNull(config.transition)
        assertEquals("Taverne", config.info?.entries?.first()?.value)
    }

    @Test
    fun `show_lunar_state defaults to off`() {
        val config = CampaignFile.parse(
            "---\ngamehost:\n  info:\n    entries:\n      - Lieu: Taverne\n---\n",
        )

        assertFalse(config.info?.showLunarState == true)
    }

    @Test
    fun `a blank transition is treated as absent`() {
        assertNull(CampaignFile.parse("---\ngamehost:\n  transition: \"  \"\n---\n").transition)
    }

    /** A broken file must not take the app down in the middle of a session. */
    @Test
    fun `malformed yaml does not throw`() {
        val config = runCatching {
            CampaignFile.parse("---\ngamehost:\n  info:\n   - this: [is, broken\n---\n")
        }
        assertTrue("parse threw: ${config.exceptionOrNull()}", config.isSuccess)
    }

    @Test
    fun `a scalar where a map was expected yields an empty config`() {
        assertEquals(CampaignConfig.EMPTY, CampaignFile.parse("---\ngamehost: watercolor\n---\n"))
    }

    @Test
    fun `entries that are not single-key maps are skipped, not fatal`() {
        val config = CampaignFile.parse(
            "---\ngamehost:\n  info:\n    entries:\n      - just a string\n      - Lieu: Taverne\n---\n",
        )

        assertEquals(1, config.info?.entries?.size)
        assertEquals("Lieu", config.info?.entries?.first()?.key)
    }
}
