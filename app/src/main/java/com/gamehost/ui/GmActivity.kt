package com.gamehost.ui

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
import com.gamehost.appGraph
import com.gamehost.ui.theme.GamehostTheme

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
            GamehostTheme {
                val gmViewModel: GmViewModel = viewModel(factory = GmViewModel.Factory)

                val browsing by gmViewModel.browsing.collectAsStateWithLifecycle()
                // Note both this composition and the player window collect the SAME
                // StateFlow instance from the application graph. That is the whole of
                // "one presentation state, two render targets" (§5).
                val presentation by graph.presentation.state.collectAsStateWithLifecycle()
                val bank by graph.slots.state.collectAsStateWithLifecycle()
                val displayStatus by graph.playerDisplay.status.collectAsStateWithLifecycle()
                val repository by graph.repository.collectAsStateWithLifecycle()
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
                        hasRoot = repository != null,
                        slotWriteFailed = slotWriteFailed,
                        onChooseFolder = { pickCampaignFolder.launch(null) },
                        onJumpTo = gmViewModel::jumpTo,
                        onRefresh = gmViewModel::refresh,
                        onEnterFolder = gmViewModel::enter,
                        onShowNow = graph::showNow,
                        onAssign = { slot, item ->
                            graph.assignSlot(slot, item, browsing.folderNames)
                        },
                        onRecall = graph::recall,
                        onClearSlot = graph::clearSlot,
                        onToggleBlackout = graph::toggleBlackout,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    )
                }
            }
        }
    }

    /**
     * Takes read **and** write on the tree.
     *
     * Write is needed only for `.gamehost/slots.json` (§6.1). If a provider grants read
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
