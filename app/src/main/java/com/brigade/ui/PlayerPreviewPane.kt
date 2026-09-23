package com.brigade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brigade.R
import com.brigade.display.PlayerDisplayStatus
import com.brigade.presentation.PresentationState
import com.brigade.render.PlayerImageModel
import com.brigade.render.PresentationSurface

/**
 * The GM's live view of the player surface.
 *
 * ### The preview never stops rendering (§5.1)
 *
 * [PresentationSurface] is drawn **unconditionally**, in every display state. When no
 * player display is attached, a compact banner is overlaid *on top of* the still-live
 * preview rather than replacing it.
 *
 * That is not cosmetic. It means the GM can keep composing with the cable out — recall
 * slots, blank, watch the preview change — and when the cable is fixed the player
 * display comes up already showing the composed state. It also leaves no display state
 * in which this pane stops being a renderer of presentation state, which is what §5
 * actually asks for.
 *
 * A full scrim would defeat the point: the image underneath is the information, so the
 * banner stays small and translucent.
 *
 * ### Aspect ratio
 *
 * Taken from the *real* attached display, not hardcoded (§16 Trap 6). The 16:9 fallback
 * applies only while there is no display to ask, and snaps to the true ratio on attach.
 *
 * ### It fits, it never overflows
 *
 * The preview is sized against **both** available dimensions, and whichever binds wins. It
 * is therefore always smaller than the space it is given, in a pane of any shape.
 *
 * This pane sits directly above the slot bar, so overflow here is not a cosmetic problem —
 * it paints over the controls and over the browser beside it. That is worth the explicit
 * arithmetic rather than leaning on a modifier that silently gives up.
 */
@Composable
fun PlayerPreviewPane(
    state: PresentationState,
    status: PlayerDisplayStatus,
    model: PlayerImageModel,
    modifier: Modifier = Modifier,
) {
    val aspect = when (status) {
        is PlayerDisplayStatus.Attached -> status.aspectRatio
        PlayerDisplayStatus.Absent -> PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT
    }

    // Centred because the preview is not always full width: in a wide, short pane it is the
    // height that binds and the box comes out narrower than the column.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // fill = false: take the space the caption leaves as a *maximum*, and be
                // shorter when the aspect ratio asks for less.
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center,
        ) {
            // Fitted against BOTH dimensions, explicitly.
            //
            // `fillMaxWidth().aspectRatio(a)` cannot do this and was the bug: fillMaxWidth
            // fixes the width (min == max), so every size aspectRatio tries other than the
            // width-driven one is rejected for having the wrong width. When `width / a`
            // exceeds the available height nothing satisfies the constraints, the modifier
            // falls through to an unconstrained size, and — because a Box does not clip —
            // the preview paints over the slot bar and the browser beside it.
            //
            // Choosing the binding dimension here makes overflow unrepresentable.
            val heightIsBinding = maxHeight.value.isFinite() && maxWidth / aspect > maxHeight
            val fitted = if (heightIsBinding) {
                Modifier.height(maxHeight).width(maxHeight * aspect)
            } else {
                Modifier.width(maxWidth).height(maxWidth / aspect)
            }

            Box(
                modifier = fitted
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            ) {
                PresentationSurface(
                    state = state,
                    modifier = Modifier.matchParentSize(),
                    model = model,
                )

                if (status is PlayerDisplayStatus.Absent) {
                    Text(
                        text = stringResource(R.string.preview_no_display),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (status is PlayerDisplayStatus.Attached) {
            Text(
                text = stringResource(
                    R.string.preview_display,
                    status.widthPx,
                    status.heightPx,
                    status.name,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // One line: an unbounded caption wraps to three on a phone and eats
                // height that belongs to the browser above it.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
