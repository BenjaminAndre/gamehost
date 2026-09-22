package com.gamehost.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ramp is pure maths over a [androidx.compose.ui.graphics.ColorMatrix], so it tests on
 * the JVM. Field *generation* does not — it builds an `android.graphics.Bitmap` — so the
 * only check of the wash's appearance is a real device.
 */
class DissolveMaskRampTest {

    /** `alpha = slope·noise + offset`, with the matrix's fifth column in 0..255 units. */
    private fun alphaFor(noise: Float, progress: Float): Float {
        val matrix = DissolveMask.rampMatrix(progress)
        val slope = matrix[3, 0]
        val offset = matrix[3, 4] / 255f
        return (slope * noise + offset).coerceIn(0f, 1f)
    }

    @Test
    fun `at zero progress nothing is revealed, whatever the noise value`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { noise ->
            assertEquals("noise=$noise", 0f, alphaFor(noise, 0f), 0.0001f)
        }
    }

    @Test
    fun `at full progress everything is revealed, whatever the noise value`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { noise ->
            assertEquals("noise=$noise", 1f, alphaFor(noise, 1f), 0.0001f)
        }
    }

    /** The wash front: low-noise regions bleed in before high-noise ones. */
    @Test
    fun `at any midpoint, lower noise is revealed before higher noise`() {
        listOf(0.25f, 0.5f, 0.75f).forEach { progress ->
            val low = alphaFor(0.2f, progress)
            val high = alphaFor(0.8f, progress)
            assertTrue("progress=$progress: $low should be >= $high", low >= high)
        }
    }

    /** The soft edge. A hard threshold would only ever produce 0 or 1 here. */
    @Test
    fun `the front has partially revealed pixels, not just on and off`() {
        val partials = (0..100)
            .map { alphaFor(it / 100f, 0.5f) }
            .count { it > 0.01f && it < 0.99f }

        assertTrue("expected a soft band, got $partials partial values", partials >= 5)
    }

    @Test
    fun `revealed area grows monotonically with progress`() {
        val noises = (0..100).map { it / 100f }
        var previous = -1f

        listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { progress ->
            val revealed = noises.sumOf { alphaFor(it, progress).toDouble() }.toFloat()
            assertTrue("progress=$progress went backwards", revealed >= previous)
            previous = revealed
        }
    }

    @Test
    fun `colour channels are zeroed, since DstIn reads only alpha`() {
        val matrix = DissolveMask.rampMatrix(0.5f)
        for (row in 0..2) {
            for (column in 0..4) {
                assertEquals("row $row column $column", 0f, matrix[row, column], 0.0001f)
            }
        }
    }
}
