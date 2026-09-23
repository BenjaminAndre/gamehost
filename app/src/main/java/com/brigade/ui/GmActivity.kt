package com.brigade.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brigade.appGraph
import com.brigade.ui.theme.BrigadeTheme

class GmActivity : ComponentActivity() {

    private val graph by lazy { appGraph }

    private val pickCampaignFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                takeGrant(uri)
                graph.setRoot(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Attached here and detached on ON_DESTROY by the host itself, so the player
        // window survives the GM backgrounding the app — which in DeX happens whenever
        // they bring another window forward.
        graph.playerDisplay.attach(this)

        setContent {
            BrigadeTheme {
                val gmViewModel: GmViewModel = viewModel(factory = GmViewModel.Factory)

                val browsing by gmViewModel.browsing.collectAsStateWithLifecycle()
                // Note both this composition and the player window collect the SAME
                // StateFlow instance from the application graph. That is the whole of
                // "one presentation state, two render targets" (§5).
                val presentation by graph.presentation.state.collectAsStateWithLifecycle()
                val bank by graph.slots.state.collectAsStateWithLifecycle()
                val displayStatus by graph.playerDisplay.status.collectAsStateWithLifecycle()
                // Same instance the player window collects — see PlayerImageModel.
                val imageModel by graph.imageModel.collectAsStateWithLifecycle()
                val campaign by graph.campaign.collectAsStateWithLifecycle()
                val campaignDateIso by graph.campaignDateIso.collectAsStateWithLifecycle()
                val slotWriteFailed by graph.slotWriteFailed.collectAsStateWithLifecycle()

                BackHandler(enabled = browsing.canGoUp) { gmViewModel.up() }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GmScreen(
                        browsing = browsing,
                        presentation = presentation,
                        bank = bank,
                        displayStatus = displayStatus,
                        imageModel = imageModel,
                        campaignDateIso = campaignDateIso,
                        campaign = campaign,
                        slotWriteFailed = slotWriteFailed,
                        onChooseFolder = { pickCampaignFolder.launch(null) },
                        onJumpTo = gmViewModel::jumpTo,
                        // Actualiser re-reads Campagne.md as well as the folder listing:
                        // the GM edits both in Obsidian and expects one button to catch up.
                        onRefresh = {
                            gmViewModel.refresh()
                            graph.refreshCampaign()
                        },
                        onEnterFolder = gmViewModel::enter,
                        onShowNow = { item -> graph.showNow(item, browsing.folderPath) },
                        onAssign = { slot, item ->
                            graph.assignSlot(slot, item, browsing.folderNames)
                        },
                        onRecall = graph::recall,
                        onClearSlot = graph::clearSlot,
                        onToggleInfo = graph::toggleInfo,
                        onStartTimer = graph::startTimer,
                        onExtendTimer = graph::extendTimer,
                        onClearTimer = graph::clearTimer,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }

    /**
     * Re-reads `Campagne.md` whenever the GM surface comes forward.
     *
     * This is what makes an edit in Obsidian appear by itself: in DeX the GM switches to
     * Obsidian, changes the in-world date, switches back, and Brigade has already caught up.
     * A filesystem watch would be the "proper" answer, but a `ContentObserver` over the
     * Storage Access Framework is unreliable across document providers, and this catches
     * every case that actually happens at a table for one small file read.
     */
    override fun onStart() {
        super.onStart()
        graph.refreshCampaign()
    }

    /**
     * Takes read **and** write on the tree.
     *
     * Write is needed only for `.brigade/slots.json` (§6.1). If a provider grants read
     * alone, fall back to that: the bank then cannot be persisted, which the UI reports,
     * but everything else keeps working rather than the folder being rejected outright.
     */
    private fun takeGrant(uri: Uri) {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, readWrite) }
            .onFailure {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
    }
}
