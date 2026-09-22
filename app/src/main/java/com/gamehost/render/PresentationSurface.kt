package com.gamehost.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.gamehost.content.ContentId
import com.gamehost.presentation.PresentationState
import com.gamehost.presentation.ScalingMode
import com.gamehost.presentation.VisualSource
import com.gamehost.presentation.effectiveVisual

/**
 * **The** renderer. Drawn identically by the GM preview and by the player window.
 *
 * ### Invariants
 *
 * Violating any of these breaks the "one state, two renderers" guarantee (§5, §10):
 *
 *  - never read `LocalConfiguration`, `LocalDensity`, `BoxWithConstraints` or any other
 *    size in here;
 *  - never branch on which window it is being drawn in;
 *  - never take an aspect-ratio parameter — the **caller** sizes the box.
 *
 * ### Why fit/letterbox needs no code
 *
 * The black background paints the whole target surface; `ContentScale.Fit` scales the
 * bitmap by `min(boxW/imgW, boxH/imgH)` and centres it. The uncovered remainder *is*
 * the letterbox, and it is the same black (§13).
 *
 * `ContentScale.Fit` is scale-invariant: the resulting geometry is a pure function of
 * `imageAspect / boxAspect`. So a 480 px preview and a 3840 px player window are
 * geometrically identical as long as both boxes share an aspect ratio — which is
 * exactly why the invariants above forbid any size-dependent branching here.
 *
 * ### When crop, pan and zoom arrive
 *
 * Express them in **normalised [0,1] coordinates** on `VisualPresentation`, never in
 * pixels. A pixel offset means different things in a 480 px preview and a 3840 px
 * window, and the preview would quietly start lying about what the players see.
 *
 * Likewise transitions belong in `PresentationState` (keyed off `revision`), not in
 * Coil's `crossfade`, whose timing is per-request and would desynchronise the two
 * windows.
 */
@Composable
fun PresentationSurface(
    state: PresentationState,
    modifier: Modifier = Modifier,
    model: (ContentId) -> Any = { it.value },
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val visual = state.effectiveVisual()
        when (val source = visual.source) {
            // Nothing drawn: the background is the blank state. The player surface
            // renders no text, ever (§21.2).
            VisualSource.None -> Unit

            is VisualSource.Image -> AsyncImage(
                model = model(source.id),
                contentDescription = null,
                contentScale = visual.scaling.toContentScale(),
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun ScalingMode.toContentScale(): ContentScale = when (this) {
    ScalingMode.Fit -> ContentScale.Fit
    ScalingMode.Fill -> ContentScale.Crop
    ScalingMode.Native -> ContentScale.None
}
