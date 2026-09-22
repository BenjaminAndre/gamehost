package com.gamehost.presentation

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos

/**
 * The Moon's phase for a given day.
 *
 * A mean-synodic model: it assumes a perfectly uniform cycle. The real orbit is elliptical,
 * so a true new or full moon can fall up to roughly half a day either side of the mean.
 * For a glyph on a panel at a table that is invisible, and the honest alternative — the
 * equation of the centre — is a great deal of arithmetic for something nobody present can
 * check against the sky.
 *
 * Feed it [CampaignDate.gregorian], never the written date: pre-1582 sources are Julian,
 * and in the twelfth century that is a seven-day error. See [HistoricalCalendar].
 */
object MoonPhase {

    private const val SYNODIC_DAYS = 29.530588853

    /** A reference new moon: 2000-01-06T18:14Z, as a fractional epoch day. */
    private const val EPOCH_NEW_MOON = 10_962.7597

    /**
     * @return phase in `[0,1)`: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
     *
     * **`.mod()`, never `%`.** Every campaign date of interest here is before 1970, so the
     * epoch day is negative — around −304,000 for the twelfth century — and Kotlin's `%`
     * returns a remainder carrying the sign of the dividend. The illuminated fraction would
     * survive that, since `cos` is even, but [isWaxing] would flip: the moon would be drawn
     * the right shape lit on the wrong limb, for every waning night in the campaign.
     */
    fun at(date: LocalDate): Double =
        (date.toEpochDay() - EPOCH_NEW_MOON).mod(SYNODIC_DAYS) / SYNODIC_DAYS

    /** Fraction of the disc lit: 0 at new, 1 at full. */
    fun illumination(phase: Double): Double = (1.0 - cos(2.0 * PI * phase)) / 2.0

    /** True while the lit limb is growing — the right-hand side in the northern hemisphere. */
    fun isWaxing(phase: Double): Boolean = phase < 0.5
}
