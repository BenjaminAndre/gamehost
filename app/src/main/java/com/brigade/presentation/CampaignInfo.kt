package com.brigade.presentation

import com.brigade.content.CampaignInfoConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns what was written in `Campagne.md` into a fully resolved [InfoPanel].
 *
 * ### Known fields, and when something earns being one
 *
 * Almost everything stays a plain label/value row. A field is only promoted when it either
 * **computes** something the GM would otherwise maintain by hand, or **formats** something
 * the generic row would render badly:
 *
 * | field      | why |
 * |------------|-----|
 * | `title`    | a heading; a bare list has nothing to name it |
 * | `location` | promoted, because it is what players look for first |
 * | `date`     | formatted, because `1137-01-09` is not what you say out loud |
 * | moon       | computed, because tracking it by hand across a campaign is miserable |
 *
 * Adding one later is an entry in the `when` below. Anything that is neither computed nor
 * reformatted should stay generic — that is what keeps this a small feature rather than a
 * schema for campaigns.
 */
object CampaignInfo {

    private const val KEY_TITLE = "title"
    private const val KEY_LOCATION = "location"
    private const val KEY_DATE = "date"

    fun build(config: CampaignInfoConfig, locale: Locale = Locale.FRENCH): InfoPanel {
        var title: String? = null
        var headline: String? = null
        var date: CampaignDate? = null
        val entries = mutableListOf<InfoEntry>()

        config.entries.forEach { entry ->
            when (entry.key.lowercase(Locale.ROOT)) {
                KEY_TITLE -> title = entry.value.takeIf { it.isNotBlank() }
                KEY_LOCATION -> headline = entry.value.takeIf { it.isNotBlank() }

                KEY_DATE -> {
                    val parsed = parseDate(entry.value)
                    if (parsed != null) {
                        date = parsed
                    } else {
                        // Unparseable: show it verbatim rather than dropping it silently,
                        // so a typo is visible instead of a line going missing.
                        entries += InfoEntry(entry.key, entry.value)
                    }
                }

                else -> entries += InfoEntry(entry.key, entry.value)
            }
        }

        return InfoPanel(
            title = title,
            headline = headline,
            dateLine = date?.let { format(it, locale) },
            entries = entries,
            moonPhase = if (config.showLunarState && date != null) {
                MoonPhase.at(date!!.gregorian).toFloat()
            } else {
                null
            },
        )
    }

    private fun parseDate(value: String): CampaignDate? = try {
        HistoricalCalendar.interpret(LocalDate.parse(value.trim()))
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * «samedi 9 janvier 1137».
     *
     * The day, month and year are the ones the GM **wrote** — their sources' calendar, so
     * the panel agrees with their notes. The weekday comes from the **converted** date,
     * because the weekday cycle ran unbroken through the 1582 reform and it is the physical
     * day that has a name. For the twelfth century the offset is seven days and the two
     * agree anyway; for the fourteenth, where it is eight, they would not.
     *
     * Formatted through `java.time` and a [Locale] rather than a string resource — a
     * locale-formatted date is not a string literal, so §21 is satisfied without one.
     */
    private fun format(date: CampaignDate, locale: Locale): String {
        val weekday = date.gregorian.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val rest = DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date.written)
        return "$weekday $rest"
    }
}
