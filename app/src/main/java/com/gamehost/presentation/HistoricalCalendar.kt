package com.gamehost.presentation

import java.time.LocalDate

/**
 * A campaign date, as written and as it falls in real time.
 *
 * @param written exactly what the GM put in `Campagne.md`, for display.
 * @param gregorian the same physical day in proleptic Gregorian, for astronomy and weekday.
 */
data class CampaignDate(
    val written: LocalDate,
    val gregorian: LocalDate,
    val wasJulian: Boolean,
)

/**
 * Reconciles the calendar historical sources use with the one `java.time` uses.
 *
 * ### Why this exists
 *
 * `LocalDate` is proleptic ISO — it extends the Gregorian calendar backwards forever,
 * including into centuries that never used it. Historical sources do the opposite: they
 * give pre-reform dates in the **Julian** calendar. Wikipedia's MOS:JG states the
 * convention plainly — Julian before 15 October 1582, Gregorian from then on — and lunar
 * tables follow it too.
 *
 * The gap is not small. In the twelfth century it is **seven days**: a GM copying
 * "full moon, 9 January 1137" out of a reference and writing it verbatim would, without
 * this conversion, get the phase for a day a week earlier, and Gamehost would draw a
 * waning crescent on the night their notes call full.
 *
 * So `date:` means what the GM's sources mean, and the conversion happens here.
 */
object HistoricalCalendar {

    /**
     * The first Gregorian day. 4 October 1582 (Julian) was followed directly by this date;
     * the ten days between were never lived through.
     */
    val GREGORIAN_START: LocalDate = LocalDate.of(1582, 10, 15)

    /** Julian Day Number of 1970-01-01, the epoch `LocalDate.toEpochDay` counts from. */
    private const val JDN_AT_UNIX_EPOCH = 2_440_588L

    /**
     * Interprets a date written in `Campagne.md`.
     *
     * Anything before [GREGORIAN_START] is read as Julian, per the convention above.
     * Dates in the never-lived range 5–14 October 1582 are perfectly valid *Julian* dates
     * and convert cleanly, so they need no special case.
     */
    fun interpret(written: LocalDate): CampaignDate {
        if (!written.isBefore(GREGORIAN_START)) {
            return CampaignDate(written = written, gregorian = written, wasJulian = false)
        }

        val gregorian = LocalDate.ofEpochDay(
            julianToEpochDay(written.year, written.monthValue, written.dayOfMonth),
        )
        return CampaignDate(written = written, gregorian = gregorian, wasJulian = true)
    }

    /**
     * Epoch day of a date in the **Julian** calendar.
     *
     * The standard Julian-Day-Number formula. Integer division throughout is intentional
     * and is what makes it exact; it assumes a positive (AD) year, which every campaign
     * date is.
     */
    fun julianToEpochDay(year: Int, month: Int, day: Int): Long {
        val a = (14 - month) / 12
        val y = year.toLong() + 4800L - a
        val m = month + 12 * a - 3

        val julianDayNumber = day + (153L * m + 2L) / 5L + 365L * y + y / 4L - 32_083L
        return julianDayNumber - JDN_AT_UNIX_EPOCH
    }
}
