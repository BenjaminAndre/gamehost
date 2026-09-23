package com.brigade.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.brigade.presentation.TimerOverlay
import kotlinx.coroutines.delay

/**
 * The players' timer: a stick of incense burning down one edge of the player surface.
 *
 * ### No numerals, deliberately
 *
 * §21.2 says the player surface renders no *application* text, and this keeps that intact —
 * a stick is a graphic, so there is nothing to localise and no rule to amend. It also reads
 * faster than digits across a table, and it belongs to the fiction rather than sitting on
 * top of it. The GM gets the exact time, on the button, where text is free.
 *
 * ### Drawn above the transition
 *
 * [PresentationSurface] composes this *after* the dissolve layers, so changing scene does not
 * wash the stick away with the image. The timer is orthogonal to what is presented.
 *
 * ### Scale invariance
 *
 * Every dimension is a fraction of the surface, so the preview and the player window show an
 * identical stick at different sizes — the same rule the info panel follows.
 */
@Composable
fun IncenseTimer(overlay: TimerOverlay, modifier: Modifier = Modifier) {
    var burned by remember(overlay) { mutableFloatStateOf(overlay.burnedAt(System.nanoTime())) }

    LaunchedEffect(overlay) {
        while (true) {
            burned = overlay.burnedAt(System.nanoTime())
            if (burned >= 1f) break
            // A stick five minutes long moves a fraction of a pixel per frame, so ticking at
            // 60 Hz for five minutes would redraw thousands of times to no visible end. Five
            // times a second is smooth and costs almost nothing. The value is still computed
            // from the clock each tick, so it cannot drift.
            delay(TICK_MILLIS)
        }
    }

    // `burned` is read inside the draw lambda, so a tick invalidates drawing only — never
    // recomposition, and never of the image underneath.
    Canvas(modifier) { drawIncense(burned) }
}

private const val TICK_MILLIS = 200L

private val SCRIM = Color(0f, 0f, 0f, 0.32f)
private val STICK = Color(0xFFCBA36B)
private val EMBER = Color(0xFFFF7A2F)
private val EMBER_GLOW = Color(0x4DFF7A2F)
private val ASH = Color(0xFF34302B)
private val HOLDER = Color(0xFF6B5A47)
private val SMOKE = Color(0x2EE8E4DA)

private fun DrawScope.drawIncense(burned: Float) {
    val stickX = size.width * 0.945f
    val baseY = size.height * 0.86f
    val topY = size.height * 0.13f

    val fullLength = baseY - topY
    val remaining = fullLength * (1f - burned.coerceIn(0f, 1f))
    val tipY = baseY - remaining

    val stickWidth = (size.width * 0.0055f).coerceAtLeast(2f)
    val columnHalf = size.width * 0.018f

    // A faint column behind it. Without this the stick vanishes over a pale sky or a lantern,
    // and a timer the players cannot find is worse than no timer.
    drawRoundRect(
        color = SCRIM,
        topLeft = Offset(stickX - columnHalf, topY - size.height * 0.04f),
        size = Size(columnHalf * 2f, fullLength + size.height * 0.10f),
        cornerRadius = CornerRadius(columnHalf),
    )

    drawRoundRect(
        color = HOLDER,
        topLeft = Offset(stickX - size.width * 0.011f, baseY),
        size = Size(size.width * 0.022f, size.height * 0.028f),
        cornerRadius = CornerRadius(size.width * 0.004f),
    )

    val burntOut = remaining <= stickWidth * 2f
    if (burntOut) {
        // Burnt out and left standing: a blackened stub and a last thread of smoke, so time
        // being up is visible without the GM having to announce it.
        drawLine(
            color = ASH,
            start = Offset(stickX, baseY),
            end = Offset(stickX, baseY - size.height * 0.026f),
            strokeWidth = stickWidth,
            cap = StrokeCap.Round,
        )
        drawSmoke(stickX, baseY - size.height * 0.03f)
        return
    }

    drawLine(
        color = STICK,
        start = Offset(stickX, baseY),
        end = Offset(stickX, tipY),
        strokeWidth = stickWidth,
        cap = StrokeCap.Round,
    )

    drawCircle(EMBER_GLOW, radius = stickWidth * 3.2f, center = Offset(stickX, tipY))
    drawCircle(EMBER, radius = stickWidth * 1.3f, center = Offset(stickX, tipY))
}

/** Three fading rings. Static: movement at the edge of a scene pulls eyes off it. */
private fun DrawScope.drawSmoke(x: Float, fromY: Float) {
    val step = size.height * 0.022f
    repeat(3) { index ->
        drawCircle(
            color = SMOKE,
            radius = size.width * (0.004f + index * 0.002f),
            center = Offset(x + step * 0.25f * index, fromY - step * (index + 1)),
            style = Stroke(width = size.width * 0.0015f),
        )
    }
}
