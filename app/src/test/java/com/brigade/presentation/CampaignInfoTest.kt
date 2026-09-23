package com.brigade.presentation

import com.brigade.content.CampaignInfoConfig
import com.brigade.content.CampaignInfoEntry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignInfoTest {

    private fun config(
        vararg entries: Pair<String, String>,
        lunar: Boolean = false,
    ) = CampaignInfoConfig(
        showLunarState = lunar,
        entries = entries.map { CampaignInfoEntry(it.first, it.second) },
    )

    // ---- Known fields --------------------------------------------------------------

    @Test
    fun `title becomes the heading and leaves the entry list`() {
        val panel = CampaignInfo.build(config("title" to "Le Renard de Jade"))

        assertEquals("Le Renard de Jade", panel.title)
        assertTrue(panel.entries.isEmpty())
    }

    @Test
    fun `location is promoted to the headline`() {
        val panel = CampaignInfo.build(config("location" to "Auberge du Héron Noir"))

        assertEquals("Auberge du Héron Noir", panel.headline)
        assertTrue(panel.entries.isEmpty())
    }

    @Test
    fun `known field names are matched case-insensitively`() {
        val panel = CampaignInfo.build(config("Title" to "Renard", "LOCATION" to "Taverne"))

        assertEquals("Renard", panel.title)
        assertEquals("Taverne", panel.headline)
    }

    @Test
    fun `anything else stays a plain label and value, in the order written`() {
        val panel = CampaignInfo.build(
            config("Saison" to "Printemps", "Météo" to "Pluie fine", "Réputation" to "Crainte"),
        )

        assertEquals(listOf("Saison", "Météo", "Réputation"), panel.entries.map { it.label })
        assertEquals("Pluie fine", panel.entries[1].value)
    }

    // ---- The date ------------------------------------------------------------------

    /**
     * Written in the calendar the GM's sources use, displayed the way they would say it out
     * loud. The numerals are the ones they wrote; only the weekday comes from the converted
     * date, because the weekday cycle ran unbroken through 1582.
     */
    @Test
    fun `a date is written out in French and keeps the day the GM wrote`() {
        val panel = CampaignInfo.build(config("date" to "1137-01-09"), locale = Locale.FRENCH)

        val line = panel.dateLine.orEmpty()
        assertTrue("was «$line»", line.contains("9 janvier 1137"))
        // Not a label/value row.
        assertTrue(panel.entries.isEmpty())
    }

    @Test
    fun `an unparseable date is shown verbatim rather than vanishing`() {
        val panel = CampaignInfo.build(config("date" to "le neuvième jour"))

        assertNull(panel.dateLine)
        assertEquals("le neuvième jour", panel.entries.single().value)
    }

    // ---- The moon ------------------------------------------------------------------

    @Test
    fun `the moon is computed from the converted date when asked for`() {
        val panel = CampaignInfo.build(config("date" to "1137-01-09", lunar = true))

        // The source calls this a full moon; the conversion and the phase agree.
        val phase = panel.moonPhase ?: error("no phase")
        assertEquals(0.5, phase.toDouble(), 1.0 / 29.530588853)
    }

    @Test
    fun `no moon unless the campaign asked for one`() {
        assertNull(CampaignInfo.build(config("date" to "1137-01-09")).moonPhase)
    }

    @Test
    fun `no moon when asked for without a date to compute it from`() {
        assertNull(CampaignInfo.build(config("Lieu" to "Taverne", lunar = true)).moonPhase)
    }

    // ---- Degenerate cases ----------------------------------------------------------

    @Test
    fun `an empty config builds an empty panel`() {
        assertTrue(CampaignInfo.build(CampaignInfoConfig()).isEmpty)
    }

    @Test
    fun `blank known fields are treated as absent`() {
        val panel = CampaignInfo.build(config("title" to "   ", "location" to ""))

        assertNull(panel.title)
        assertNull(panel.headline)
    }
}
