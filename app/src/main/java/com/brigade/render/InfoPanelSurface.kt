package com.brigade.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.brigade.presentation.InfoPanel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * The layout is built at this fixed size and then uniformly scaled to whatever surface it
 * lands on.
 *
 * ### Why a virtual resolution, and why scaling the font is not enough
 *
 * This panel is drawn twice: once in a preview a few hundred pixels wide, once on a
 * 1920-pixel player display. Sizing text in `sp` would make the preview lie about what the
 * players see, and `PresentationSurface`'s invariants forbid branching on a size anyway.
 *
 * But scaling the font alone would *still* not be enough, and that is the subtle part.
 * **Line breaking is quantised.** A value that fits on three lines at 1920 px can break to
 * four at 700 px, and the preview would show a differently-shaped panel — the same §5
 * failure arriving through a third door. Measuring once, at one resolution, and scaling the
 * measured result means the breaks are computed a single time and the two windows are
 * identical by construction rather than by hope.
 */
private const val VIRTUAL_WIDTH = 1920f

private const val PADDING = 130f
private const val MOON_RADIUS = 78f

private val TITLE_SIZE = 76.sp
private val HEADLINE_SIZE = 58.sp
private val DATE_SIZE = 42.sp
private val LABEL_SIZE = 28.sp
private val VALUE_SIZE = 40.sp

private val INK = Color(0xFFF2EDE1)
private val INK_DIM = Color(0xFFA9A192)
private val MOON_LIT = Color(0xFFF4EBD2)
private val MOON_DARK = Color(0xFF23262E)

/** Keeps text legible over an arbitrary background image without hiding it. */
private val SCRIM = Color(0f, 0f, 0f, 0.55f)

/**
 * Draws [panel] over whatever this surface already contains.
 *
 * Expects to be called from inside a [DrawScope] whose size is the full player surface.
 */
@Composable
internal fun rememberPanelMeasurer(): TextMeasurer {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current

    // Density 1 on purpose: it makes one `sp` exactly one virtual pixel, so measurement no
    // longer depends on the density of whichever display this window happens to be on.
    // Two windows, one layout.
    return remember(fontFamilyResolver, layoutDirection) {
        TextMeasurer(fontFamilyResolver, Density(density = 1f, fontScale = 1f), layoutDirection)
    }
}

internal fun DrawScope.drawInfoPanel(panel: InfoPanel, measurer: TextMeasurer) {
    drawRect(SCRIM)

    // Driven by width alone, and by the SAME expression in both windows. The preview is
    // sized to the player display's real aspect ratio, so scaling by width keeps the two
    // layouts identical rather than merely similar.
    val uniformScale = size.width / VIRTUAL_WIDTH
    val virtualHeight = size.height / uniformScale

    withTransform({ scale(uniformScale, uniformScale, pivot = Offset.Zero) }) {
        panel.moonPhase?.let { phase ->
            drawMoon(
                phase = phase,
                centre = Offset(VIRTUAL_WIDTH - PADDING - MOON_RADIUS, PADDING + MOON_RADIUS),
                radius = MOON_RADIUS,
            )
        }

        // Text never runs under the moon.
        val textWidth = (VIRTUAL_WIDTH - 2 * PADDING - if (panel.moonPhase != null) 260f else 0f)
        val constraints = Constraints(maxWidth = textWidth.toInt().coerceAtLeast(1))

        val blocks = buildList {
            panel.title?.let {
                add(measurer.measure(it, TextStyle(color = INK, fontSize = TITLE_SIZE, fontWeight = FontWeight.Bold), constraints = constraints) to 28f)
            }
            panel.headline?.let {
                add(measurer.measure(it, TextStyle(color = INK, fontSize = HEADLINE_SIZE), constraints = constraints) to 14f)
            }
            panel.dateLine?.let {
                add(measurer.measure(it, TextStyle(color = INK_DIM, fontSize = DATE_SIZE), constraints = constraints) to 44f)
            }
            panel.entries.forEach { entry ->
                add(measurer.measure(entry.label.uppercase(), TextStyle(color = INK_DIM, fontSize = LABEL_SIZE, letterSpacing = 3.sp), constraints = constraints) to 4f)
                add(measurer.measure(entry.value, TextStyle(color = INK, fontSize = VALUE_SIZE), constraints = constraints) to 26f)
            }
        }

        // Centred vertically as one block, so a two-line panel and a ten-line one both sit
        // comfortably instead of clinging to the top.
        val totalHeight = blocks.sumOf { (layout, gap) -> (layout.size.height + gap).toDouble() }.toFloat()
        var y = ((virtualHeight - totalHeight) / 2f).coerceAtLeast(PADDING)

        blocks.forEach { (layout, gap) ->
            drawText(layout, topLeft = Offset(PADDING, y))
            y += layout.size.height + gap
        }
    }
}

/**
 * Draws the Moon at [phase], where 0 is new, 0.25 first quarter, 0.5 full, 0.75 last.
 *
 * The terminator — the line between lit and unlit — is the projection of a circle seen at
 * an angle, so it is an **ellipse**, not a straight line. Drawing a plain half-disc looks
 * right at the quarters and obviously wrong everywhere else.
 *
 * Construction: fill the disc dark, lay the lit half over it, then draw an ellipse of
 * horizontal radius `R·|cos 2πp|` across the middle — dark when the moon is a crescent,
 * lit when it is gibbous. That single sign flip is what carries the shape through all
 * eight phases.
 */
private fun DrawScope.drawMoon(phase: Float, centre: Offset, radius: Float) {
    val terminator = cos(2.0 * PI * phase).toFloat()
    val waxing = phase < 0.5f

    drawCircle(MOON_DARK, radius, centre)

    // 0° is at three o'clock and angles run clockwise, so -90° sweeping 180° is the right
    // half and 90° sweeping 180° is the left. Waxing lights the right limb.
    drawArc(
        color = MOON_LIT,
        startAngle = if (waxing) -90f else 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(centre.x - radius, centre.y - radius),
        size = Size(radius * 2, radius * 2),
    )

    val halfWidth = radius * abs(terminator)
    if (halfWidth > 0.5f) {
        drawOval(
            color = if (terminator > 0f) MOON_DARK else MOON_LIT,
            topLeft = Offset(centre.x - halfWidth, centre.y - radius),
            size = Size(halfWidth * 2, radius * 2),
        )
    }

    // A rim, so a new moon is still a moon rather than a hole in the panel.
    drawCircle(INK_DIM, radius, centre, style = Stroke(width = 2f))
}
