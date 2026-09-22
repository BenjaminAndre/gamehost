package com.gamehost

import android.app.Application
import android.net.Uri
import com.gamehost.content.ContentItem
import com.gamehost.content.ContentPath
import com.gamehost.content.ContentRepository
import com.gamehost.content.RootStore
import com.gamehost.content.saf.DocumentTreeSource
import com.gamehost.content.saf.SafSlotStore
import com.gamehost.display.PlayerDisplayHost
import com.gamehost.display.PresentationPlayerDisplayHost
import com.gamehost.presentation.PresentationStore
import com.gamehost.presentation.SlotBank
import com.gamehost.presentation.SlotBankState
import com.gamehost.presentation.SlotContent
import com.gamehost.presentation.SlotId
import com.gamehost.presentation.SnapshotStore
import com.gamehost.presentation.toSnapshot
import com.gamehost.presentation.toState
import com.gamehost.storage.PrefsRootStore
import com.gamehost.storage.PrefsSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hand-rolled dependency graph, scoped to the process.
 *
 * Roughly thirty lines instead of Hilt or Koin, for four singletons.
 *
 * **Why these live here and not on a ViewModel:** the player window is an
 * `android.app.Presentation` with a lifecycle of its own, and both it and the GM
 * Activity must observe *the same* `StateFlow` instance. State scoped to the Activity's
 * `ViewModelStore` would be cleared underneath the player window, and a `viewModel()`
 * call inside the player composition would quietly create a second, independent state
 * holder — exactly the "two independent pieces of business logic" §5 forbids.
 *
 * Only genuinely-UI state (the browsing stack) belongs to a ViewModel, where dying with
 * the UI is the correct behaviour.
 */
class AppGraph(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val presentation = PresentationStore()
    val slots = SlotBank()

    private val rootStore: RootStore = PrefsRootStore(app)
    private val snapshotStore: SnapshotStore = PrefsSnapshotStore(app)

    private val _repository = MutableStateFlow<ContentRepository?>(null)
    val repository: StateFlow<ContentRepository?> = _repository.asStateFlow()

    /** True when the slot bank could not be written back to `.gamehost/slots.json`. */
    private val _slotWriteFailed = MutableStateFlow(false)
    val slotWriteFailed: StateFlow<Boolean> = _slotWriteFailed.asStateFlow()

    private var slotStore: SafSlotStore? = null

    val playerDisplay: PlayerDisplayHost = PresentationPlayerDisplayHost(app, presentation.state)

    init {
        appScope.launch {
            restoreRoot()?.let { openRoot(it) }

            val snapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
            if (snapshot != null) {
                // Cold start comes up blanked. After a crash or a reboot, silently
                // blasting the last image onto the players' screen during relaunch is a
                // worse failure than a black screen plus one tap. A *reconnecting*
                // display does not come through here — it re-renders live state,
                // because a display that could change state would be the source of
                // truth (§16 Trap 5).
                presentation.restore(snapshot.toState(forceBlackout = true))
            }

            // Started only after the restore, so the default state cannot overwrite
            // what we were about to load.
            presentation.state
                .onEach { state ->
                    withContext(Dispatchers.IO) { snapshotStore.save(state.toSnapshot()) }
                }
                .launchIn(appScope)
        }
    }

    // ---- Campaign root ----------------------------------------------------------

    fun setRoot(treeUri: Uri) {
        rootStore.save(treeUri.toString())
        appScope.launch { openRoot(treeUri) }
    }

    private suspend fun openRoot(treeUri: Uri) {
        val repository = ContentRepository(DocumentTreeSource(app.contentResolver, treeUri))
        val store = SafSlotStore(app.contentResolver, treeUri)

        slotStore = store
        _repository.value = repository
        _slotWriteFailed.value = false

        // The bank is loaded as paths and only then resolved against the tree, so a
        // renamed file surfaces as a *missing* slot rather than vanishing (§6.1).
        slots.replaceAll(SlotBankState.unresolved(store.load()))
        resolveSlots(repository)
    }

    private suspend fun restoreRoot(): Uri? = withContext(Dispatchers.IO) {
        val saved = rootStore.load()?.let(Uri::parse) ?: return@withContext null

        // The grant survives reboots, but the user can revoke it in Settings and the
        // folder can be gone. Checking is cheaper than failing mysteriously later.
        val stillGranted = app.contentResolver.persistedUriPermissions
            .any { it.uri == saved && it.isReadPermission }

        if (!stillGranted) {
            rootStore.save(null)
            return@withContext null
        }
        saved
    }

    private suspend fun resolveSlots(repository: ContentRepository) {
        slots.state.value.slots.forEach { slot ->
            val path = when (val content = slot.content) {
                is SlotContent.Missing -> content.path
                is SlotContent.Filled -> content.path
                SlotContent.Empty -> return@forEach
            }
            val item = repository.resolve(path)
            if (item != null) slots.markResolved(slot.id, item.id, item.displayName) else slots.markMissing(slot.id)
        }
    }

    // ---- Presentation actions ---------------------------------------------------

    /** *Afficher*: transient, and deliberately does not touch the bank. */
    fun showNow(item: ContentItem) = presentation.show(item.id, fromSlot = null)

    fun recall(slot: SlotId) {
        when (val content = slots.contentOf(slot)) {
            is SlotContent.Filled -> presentation.show(content.id, fromSlot = slot)
            // Nothing to present; the bar already shows the slot as missing or empty.
            is SlotContent.Missing, SlotContent.Empty -> Unit
        }
    }

    fun toggleBlackout() = presentation.toggleBlackout()

    // ---- Slot actions -----------------------------------------------------------

    /** @param folderNames folders between the campaign root and [item], root excluded. */
    fun assignSlot(slot: SlotId, item: ContentItem, folderNames: List<String>) {
        slots.assign(slot, ContentPath.of(folderNames, item.displayName), item.id, item.displayName)
        persistSlots()
    }

    fun clearSlot(slot: SlotId) {
        slots.clear(slot)
        persistSlots()
    }

    /**
     * Only assignment and clearing change the stored paths, so persistence is explicit
     * here rather than a debounced observer over the bank — resolution does not need to
     * write anything back.
     */
    private fun persistSlots() {
        val store = slotStore ?: return
        val paths = slots.state.value.paths()
        appScope.launch {
            _slotWriteFailed.value = !store.save(paths)
        }
    }
}
