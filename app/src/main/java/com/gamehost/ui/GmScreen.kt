package com.gamehost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamehost.R
import com.gamehost.content.ContentItem
import com.gamehost.display.PlayerDisplayStatus
import com.gamehost.presentation.PresentationState
import com.gamehost.presentation.SlotBankState
import com.gamehost.presentation.SlotId

@Composable
fun GmScreen(
    browsing: BrowsingState,
    presentation: PresentationState,
    bank: SlotBankState,
    displayStatus: PlayerDisplayStatus,
    hasRoot: Boolean,
    slotWriteFailed: Boolean,
    onChooseFolder: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRefresh: () -> Unit,
    onEnterFolder: (ContentItem) -> Unit,
    onShowNow: (ContentItem) -> Unit,
    onAssign: (SlotId, ContentItem) -> Unit,
    onRecall: (SlotId) -> Unit,
    onClearSlot: (SlotId) -> Unit,
    onToggleBlackout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasRoot) {
        NoCampaignState(onChooseFolder = onChooseFolder, modifier = modifier)
        return
    }

    var pendingItem by remember { mutableStateOf<ContentItem?>(null) }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        FolderBar(
            stack = browsing.stack,
            onJumpTo = onJumpTo,
            onChangeFolder = onChooseFolder,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        )

        if (browsing.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (browsing.error == BrowsingError.Unavailable) {
            Text(
                text = stringResource(R.string.browser_folder_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (slotWriteFailed) {
            Text(
                text = stringResource(R.string.error_slots_save),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Two-pane once there is room for it — the Tab S7+ in landscape, or a wide DeX
        // window. BoxWithConstraints is fine *here*; the place it is forbidden is inside
        // PresentationSurface, where a size-dependent branch would break the
        // one-state-two-renderers guarantee.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 840.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContentGrid(
                        browsing = browsing,
                        bank = bank,
                        onEnterFolder = onEnterFolder,
                        onImageTapped = { pendingItem = it },
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                    )
                    PlayerPreviewPane(
                        state = presentation,
                        status = displayStatus,
                        modifier = Modifier
                            .weight(0.38f)
                            .padding(top = 8.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentGrid(
                        browsing = browsing,
                        bank = bank,
                        onEnterFolder = onEnterFolder,
                        onImageTapped = { pendingItem = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    PlayerPreviewPane(
                        state = presentation,
                        status = displayStatus,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SlotBar(
            bank = bank,
            blackout = presentation.blackout,
            liveSlot = presentation.liveSlot,
            onRecall = onRecall,
            onClear = onClearSlot,
            onToggleBlackout = onToggleBlackout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }

    pendingItem?.let { item ->
        ImageActionDialog(
            item = item,
            bank = bank,
            onShowNow = {
                onShowNow(item)
                pendingItem = null
            },
            onAssign = { slot ->
                onAssign(slot, item)
                pendingItem = null
            },
            onDismiss = { pendingItem = null },
        )
    }
}

@Composable
private fun NoCampaignState(onChooseFolder: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.browser_no_folder_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.browser_no_folder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Button(onClick = onChooseFolder) {
            Text(stringResource(R.string.browser_choose_folder))
        }
    }
}
