package com.gamehost.presentation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The only check this arithmetic will ever get. It cannot be eyeballed, there is no local
 * toolchain to run it against, and a wrong moon looks plausible.
 */
class MoonPhaseTest {

    /**
     * One day of phase. The tolerance on the historical assertions below is deliberately
     * this and not tighter: a mean-synodic model ignores the Moon's orbital eccentricity,
     * so a true new or full moon drifts up to about half a day either side of the mean. A
     * tighter bound would be asserting an accuracy this model does not claim.
     */
    private val ONE_DAY = 1.0 / 29.530588853

    /** Phase is circular, so 0.98 and 0.02 are close. */
    private fun assertPhaseNear(expected: Double, actual: Double, tolerance: Double) {
        val raw = Math.abs(expected - actual)
        val distance = minOf(raw, 1.0 - raw)
        assertTrue("expected ~$expected, was $actual (off by $distance)", distance <= tolerance)
    }

    // ---- Calendar conversion -------------------------------------------------------

    /**
     * The case that started all this. A lunar table gives "full moon, 9 January 1137" —
     * a Julian date, per Wikipedia's MOS:JG — and that is the same physical day as
     * proleptic Gregorian 16 January 1137. Seven days, the twelfth-century offset.
     */
    @Test
    fun `a twelfth-century Julian date converts to proleptic Gregorian`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(1137, 1, 9))

        assertTrue(date.wasJulian)
        assertEquals(LocalDate.of(1137, 1, 9), date.written)
        assertEquals(LocalDate.of(1137, 1, 16), date.gregorian)
    }

    /** 4 October 1582 Julian was the last pre-reform day; the next day was 15 October. */
    @Test
    fun `the day before the reform converts to the day before it starts`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(1582, 10, 4))

        assertEquals(LocalDate.of(1582, 10, 14), date.gregorian)
    }

    @Test
    fun `the first Gregorian day is taken at face value`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(1582, 10, 15))

        assertFalse(date.wasJulian)
        assertEquals(LocalDate.of(1582, 10, 15), date.gregorian)
    }

    /** The ten days nobody lived through are valid *Julian* dates and convert cleanly. */
    @Test
    fun `a date in the skipped range converts rather than failing`() {
        assertEquals(
            LocalDate.of(1582, 10, 15),
            HistoricalCalendar.interpret(LocalDate.of(1582, 10, 5)).gregorian,
        )
    }

    @Test
    fun `a modern date is left alone`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(2026, 9, 22))

        assertFalse(date.wasJulian)
        assertEquals(LocalDate.of(2026, 9, 22), date.gregorian)
    }

    // ---- Phase ---------------------------------------------------------------------

    /**
     * The payoff. Written as the GM's sources give it, converted, and the moon comes out
     * full — which is what the source said it was.
     */
    @Test
    fun `the first full moon of 1137 reads as full`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(1137, 1, 9))
        val phase = MoonPhase.at(date.gregorian)

        assertPhaseNear(0.5, phase, ONE_DAY)
        assertTrue("illumination was ${MoonPhase.illumination(phase)}", MoonPhase.illumination(phase) > 0.98)
    }

    /** Same source, same month: new moon on 23 January Julian. */
    @Test
    fun `the new moon of January 1137 reads as new`() {
        val date = HistoricalCalendar.interpret(LocalDate.of(1137, 1, 23))
        val phase = MoonPhase.at(date.gregorian)

        assertPhaseNear(0.0, phase, ONE_DAY)
        assertTrue("illumination was ${MoonPhase.illumination(phase)}", MoonPhase.illumination(phase) < 0.02)
    }

    /**
     * Regression for the sign bug. `LocalDate.of(1137,1,16).toEpochDay()` is about
     * −304,232; Kotlin's `%` would return a negative remainder and a phase outside `[0,1)`.
     */
    @Test
    fun `a deeply negative epoch day still yields a phase in range`() {
        listOf(
            LocalDate.of(1137, 1, 16),
            LocalDate.of(1, 1, 1),
            LocalDate.of(1969, 12, 31),
        ).forEach { date ->
            val phase = MoonPhase.at(date)
            assertTrue("$date gave $phase", phase >= 0.0 && phase < 1.0)
        }
    }

    /**
     * What the sign bug would actually have broken. The illuminated fraction survives it,
     * because cos is even — the visible damage is the lit limb flipping sides.
     */
    @Test
    fun `waxing and waning are distinguished correctly before 1970`() {
        val newMoon = HistoricalCalendar.interpret(LocalDate.of(1137, 1, 23)).gregorian

        // A week after new is waxing; a week before it, waning.
        assertTrue(MoonPhase.isWaxing(MoonPhase.at(newMoon.plusDays(7))))
        assertFalse(MoonPhase.isWaxing(MoonPhase.at(newMoon.minusDays(7))))
    }

    @Test
    fun `the cycle closes on itself`() {
        val date = LocalDate.of(1137, 1, 16)
        val phase = MoonPhase.at(date)

        // 29.53 days later, rounded to whole days, lands within a day's worth of phase.
        assertPhaseNear(phase, MoonPhase.at(date.plusDays(30)), 0.02)
    }

    @Test
    fun `illumination runs from new to full and back`() {
        assertEquals(0.0, MoonPhase.illumination(0.0), 0.0001)
        assertEquals(0.5, MoonPhase.illumination(0.25), 0.0001)
        assertEquals(1.0, MoonPhase.illumination(0.5), 0.0001)
        assertEquals(0.5, MoonPhase.illumination(0.75), 0.0001)
    }
}
