package com.brigade.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.brigade.R
import com.brigade.presentation.ActiveControl
import com.brigade.presentation.Slot
import com.brigade.presentation.SlotBankState
import com.brigade.presentation.SlotContent
import com.brigade.presentation.SlotId

private val BAR_HEIGHT = 84.dp
private val MODE_BUTTON_WIDTH = 88.dp

/**
 * The control bar: the permanent blank control plus the six recall slots (§6.1).
 *
 * Everything here is sized for use in dim light, mid-sentence, without looking down —
 * hence the large targets and the deliberately loud blank button.
 *
 * The highlight marks the slot that is *live*, which is a readout of presentation
 * state, not of whatever was tapped last.
 */
@Composable
fun SlotBar(
    bank: SlotBankState,
    active: ActiveControl,
    onRecall: (SlotId) -> Unit,
    onClear: (SlotId) -> Unit,
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The one mode button, visually separate from the six recall slots: it changes what
        // KIND of thing is shown, they choose which image.
        //
        // Always enabled, even with no campaign info configured — INFO is also the blank
        // control, and "nothing specific, I'm preparing" is exactly when a GM reaches for it.
        Button(
            onClick = onToggleInfo,
            shape = RoundedCornerShape(6.dp),
            colors = if (active is ActiveControl.Info) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
            modifier = Modifier
                .width(MODE_BUTTON_WIDTH)
                .fillMaxHeight(),
        ) {
            Text(
                text = stringResource(R.string.control_info),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        bank.slots.forEach { slot ->
            SlotCell(
                slot = slot,
                isLive = active is ActiveControl.Slot && active.id.index == slot.id.index,
                onRecall = { onRecall(slot.id) },
                onClear = { onClear(slot.id) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotCell(
    slot: Slot,
    isLive: Boolean,
    onRecall: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val number = slot.id.index + 1
    val content = slot.content

    val description = when (content) {
        SlotContent.Empty -> stringResource(R.string.slot_empty, number)
        is SlotContent.Missing -> stringResource(R.string.slot_missing)
        is SlotContent.Filled -> stringResource(R.string.slot_filled, number, content.displayName)
    }

    val borderColor = when {
        isLive -> MaterialTheme.colorScheme.primary
        content is SlotContent.Missing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (isLive) 3.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            // Long-press clears. Assigning overwrites without confirmation, so the only
            // destructive gesture is the one that is hard to trigger by accident.
            // The label is what makes an otherwise invisible gesture announceable.
            .combinedClickable(
                onLongClickLabel = stringResource(R.string.action_clear_slot),
                onLongClick = onClear,
                onClick = onRecall,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            SlotContent.Empty -> Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is SlotContent.Missing -> Text(
                text = stringResource(R.string.slot_missing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            is SlotContent.Filled -> {
                if (content.imageId != null) {
                    AsyncImage(
                        model = content.imageId.value,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    // A note that links no image. The slot is still perfectly usable — it
                    // fills the GM bar and blanks the players — so it shows its name rather
                    // than reading as broken.
                    Text(
                        text = content.displayName.substringBeforeLast('.'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )

                if (content.isNote) {
                    Text(
                        text = stringResource(R.string.browser_note_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
