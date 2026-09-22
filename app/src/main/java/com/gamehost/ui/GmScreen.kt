package com.gamehost.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.gamehost.CampaignState
import com.gamehost.R
import com.gamehost.content.ContentItem
import com.gamehost.display.PlayerDisplayStatus
import com.gamehost.presentation.PresentationState
import com.gamehost.presentation.SlotBankState
import com.gamehost.presentation.SlotId
import com.gamehost.presentation.activeControl
import com.gamehost.render.PlayerImageModel

/**
 * Most of the stacked layout's height belongs to the browser; the preview gets at most
 * this share of it, whatever ratio the player display reports.
 */
private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.42f

@Composable
fun GmScreen(
    browsing: BrowsingState,
    presentation: PresentationState,
    bank: SlotBankState,
    displayStatus: PlayerDisplayStatus,
    imageModel: PlayerImageModel,
    campaign: CampaignState,
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
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (campaign) {
        // Deliberately blank, not a spinner. The saved root usually resolves within a
        // frame or two, and a spinner that appears and vanishes that fast is more
        // jarring than a brief empty background.
        CampaignState.Restoring -> {
            Box(modifier = modifier.fillMaxSize())
            return
        }

        CampaignState.None -> {
            NoCampaignState(onChooseFolder = onChooseFolder, modifier = modifier)
            return
        }

        CampaignState.Open -> Unit
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
            // Read maxHeight HERE, not inside the Column below. BoxWithConstraintsScope
            // and ColumnScope both carry @LayoutScopeMarker, so once ColumnScope is the
            // innermost receiver the DslMarker rules make maxHeight unreachable without
            // an explicit qualifier.
            //
            // The cap itself: in a Column the UNWEIGHTED child is measured first and
            // takes what it asks for, so without this the preview's height is dictated
            // entirely by the attached display's aspect ratio — and a tall one silently
            // starves the weighted browser above it to zero height.
            val previewMaxHeight = maxHeight * PREVIEW_MAX_HEIGHT_FRACTION

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
                        model = imageModel,
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
                        model = imageModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = previewMaxHeight),
                    )
                }
            }
        }

        SlotBar(
            bank = bank,
            active = presentation.activeControl(),
            infoAvailable = presentation.scene.info != null,
            onRecall = onRecall,
            onClear = onClearSlot,
            onToggleBlackout = onToggleBlackout,
            onToggleInfo = onToggleInfo,
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
