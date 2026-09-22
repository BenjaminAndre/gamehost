package com.gamehost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.gamehost.R
import com.gamehost.display.PlayerDisplayStatus
import com.gamehost.presentation.PresentationState
import com.gamehost.render.PresentationSurface

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
 */
@Composable
fun PlayerPreviewPane(
    state: PresentationState,
    status: PlayerDisplayStatus,
    modifier: Modifier = Modifier,
) {
    val aspect = when (status) {
        is PlayerDisplayStatus.Attached -> status.aspectRatio
        PlayerDisplayStatus.Absent -> PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT
    }

    // Centred because the box below is not always full width: when the caller bounds the
    // height, aspectRatio satisfies that constraint instead and returns a narrower box.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        ) {
            PresentationSurface(
                state = state,
                modifier = Modifier.matchParentSize(),
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
