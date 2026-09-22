package com.gamehost.render

import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size
import com.gamehost.content.ContentId

/**
 * Builds the Coil request that BOTH render targets use for a given piece of content.
 *
 * ### Why this type exists
 *
 * Coil's memory cache is keyed by request size, and `AsyncImage` sizes its request to the
 * composable it finds itself in. Left alone, the GM preview (a few hundred dp) and the
 * player window (the full external display) therefore build *different* requests, hold
 * *different* cache entries, and reach `Success` at different moments.
 *
 * With a hard cut nobody notices. With a cross-dissolve it is immediately visible: the
 * transition reveals whatever is in the incoming layer *right now*, so for the first
 * frames of every cold change the two windows genuinely show different pixels. That is
 * the §5 guarantee — one presentation state, two render targets, same picture — failing
 * in the one place the whole architecture exists to protect.
 *
 * So the size is pinned here, once, and handed to both windows. One cache key, one
 * decode, one bitmap. It also makes prefetching meaningful: warming the cache is only
 * useful if the entry warmed is the entry both windows will ask for.
 *
 * ### Why it is a parameter and not a lookup
 *
 * `PresentationSurface` already accepts `model: (ContentId) -> Any` from its caller,
 * for exactly this reason — the same shape by which `PlayerPreviewPane` takes its aspect
 * ratio from the real display rather than measuring itself. Nothing reads a size inside
 * the composable, so its "never read any size" invariant is untouched.
 */
fun interface PlayerImageModel {
    operator fun invoke(id: ContentId): Any
}

/**
 * @param widthPx the player display's pixel width, or a sensible stand-in while none is
 * attached. Both windows must be given the SAME value or the point is lost.
 */
fun playerImageModel(
    context: PlatformContext,
    widthPx: Int,
    heightPx: Int,
): PlayerImageModel = PlayerImageModel { id ->
    ImageRequest.Builder(context)
        .data(id.value)
        // Explicit and identical in both windows. Without this Coil infers the size from
        // each composable's constraints, which is the whole problem.
        .size(Size(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1)))
        // Matches ScalingMode.Fit, the only mode v0.1 sets. Downscaling to the display's
        // own resolution also stops a 12000px battle map from decoding at native size.
        .scale(Scale.FIT)
        .build()
}
