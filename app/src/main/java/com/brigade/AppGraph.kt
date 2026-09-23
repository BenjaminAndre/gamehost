package com.brigade

import android.app.Application
import android.net.Uri
import com.brigade.content.CampaignConfig
import com.brigade.content.ContentItem
import com.brigade.content.ContentKind
import com.brigade.content.ContentPath
import com.brigade.content.ContentRepository
import com.brigade.content.RootStore
import com.brigade.content.saf.CampaignFile
import com.brigade.content.saf.DocumentTreeSource
import com.brigade.content.saf.NoteReader
import com.brigade.content.saf.SafSlotStore
import com.brigade.display.PlayerDisplayHost
import com.brigade.display.PlayerDisplayStatus
import com.brigade.display.PresentationPlayerDisplayHost
import com.brigade.render.PlayerImageModel
import com.brigade.render.playerImageModel
import com.brigade.presentation.CampaignInfo
import com.brigade.presentation.NoteBar
import com.brigade.presentation.PresentationStore
import com.brigade.presentation.SceneMode
import com.brigade.presentation.SlotBank
import com.brigade.presentation.SlotBankState
import com.brigade.presentation.SlotContent
import com.brigade.presentation.SlotId
import com.brigade.presentation.SnapshotStore
import com.brigade.presentation.TransitionSpec
import com.brigade.presentation.toSnapshot
import com.brigade.presentation.toState
import com.brigade.storage.PrefsRootStore
import com.brigade.storage.PrefsSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether a campaign is open — with [Restoring] distinct from [None].
 *
 * Restoring the saved root suspends on a disk read, so the first composition always runs
 * before it finishes. Collapsing these two into a single boolean made every launch flash
 * the full "no campaign" screen, complete with a live folder-picker button, before the
 * browser appeared.
 */
enum class CampaignState { Restoring, None, Open }

/**
 * Decode size used while no player display is attached.
 *
 * Matches [PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT], so the preview's letterboxing looks
 * the same before and after the cable goes in.
 */
private const val FALLBACK_PLAYER_WIDTH_PX = 1920
private const val FALLBACK_PLAYER_HEIGHT_PX = 1080

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

    /**
     * The campaign's configured transition.
     *
     * Read through a lambda by the store rather than passed by value, so opening a
     * different campaign folder changes it without rebuilding presentation state. The
     * `transition:` key in `Campagne.md` sets it; until that reader exists it holds the
     * default from [TransitionSpec.byName] for an absent config.
     */
    private val _transitionSpec = MutableStateFlow(TransitionSpec.byName(null))

    /** Last read of `Campagne.md`. Re-read when a campaign is opened. */
    private val _campaignConfig = MutableStateFlow(CampaignConfig.EMPTY)
    val campaignConfig: StateFlow<CampaignConfig> = _campaignConfig.asStateFlow()

    private var campaignFile: CampaignFile? = null
    private var noteReader: NoteReader? = null

    val presentation = PresentationStore(spec = { _transitionSpec.value })
    val slots = SlotBank()

    private val rootStore: RootStore = PrefsRootStore(app)
    private val snapshotStore: SnapshotStore = PrefsSnapshotStore(app)

    private val _repository = MutableStateFlow<ContentRepository?>(null)
    val repository: StateFlow<ContentRepository?> = _repository.asStateFlow()

    private val _campaign = MutableStateFlow(CampaignState.Restoring)
    val campaign: StateFlow<CampaignState> = _campaign.asStateFlow()

    /** True when the slot bank could not be written back to `.brigade/slots.json`. */
    private val _slotWriteFailed = MutableStateFlow(false)
    val slotWriteFailed: StateFlow<Boolean> = _slotWriteFailed.asStateFlow()

    private var slotStore: SafSlotStore? = null

    /**
     * The one image request builder both render targets use.
     *
     * Declared before [playerDisplay] and updated from its status in [init], rather than
     * derived from it with `map`/`stateIn` — the display host needs this flow at
     * construction and this flow needs the display's size, so one of the two has to start
     * with a stand-in. Doing it this way keeps the cycle broken without a lazy indirection.
     */
    private val _imageModel = MutableStateFlow(
        playerImageModel(app, FALLBACK_PLAYER_WIDTH_PX, FALLBACK_PLAYER_HEIGHT_PX),
    )
    val imageModel: StateFlow<PlayerImageModel> = _imageModel.asStateFlow()

    val playerDisplay: PlayerDisplayHost =
        PresentationPlayerDisplayHost(app, presentation.state, imageModel)

    init {
        // Re-pin the decode size whenever the player display changes, so both windows keep
        // sharing one Coil cache entry at the display's actual resolution.
        playerDisplay.status
            .onEach { status ->
                _imageModel.value = when (status) {
                    is PlayerDisplayStatus.Attached ->
                        playerImageModel(app, status.widthPx, status.heightPx)

                    PlayerDisplayStatus.Absent ->
                        playerImageModel(app, FALLBACK_PLAYER_WIDTH_PX, FALLBACK_PLAYER_HEIGHT_PX)
                }
            }
            .launchIn(appScope)

        appScope.launch {
            // The snapshot is restored BEFORE the campaign is opened, because `restore`
            // replaces the whole state — running it afterwards would discard the info
            // panel that openRoot had just loaded, leaving INFO mode showing black.
            val snapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
            if (snapshot != null) {
                // Cold start comes up on INFO. After a crash or a reboot, silently blasting
                // the last image onto the players' screen during relaunch is worse than the
                // info panel — or black, if the campaign configures none. The scene is
                // restored intact underneath, so one tap on the live slot resumes.
                //
                // A *reconnecting* display does not come through here: it re-renders live
                // state, because a display that could change state would be the source of
                // truth (§16 Trap 5).
                presentation.restore(snapshot.toState(startInInfo = true))
            }

            val saved = restoreRoot()
            if (saved != null) openRoot(saved) else _campaign.value = CampaignState.None

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
        _campaign.value = CampaignState.Open
        _slotWriteFailed.value = false

        // Read Campagne.md before anything can be presented, so the very first slot tap
        // already dissolves the way the campaign asked for.
        campaignFile = CampaignFile(app.contentResolver, treeUri)
        noteReader = NoteReader(app.contentResolver)
        reloadCampaignConfig()

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
            if (item == null) {
                slots.markMissing(slot.id)
                return@forEach
            }

            val presented = presentableFor(item, path.parent)
            slots.markResolved(slot.id, item.displayName, presented.imageId, presented.note)
        }
    }

    /**
     * What a browsed or slotted item actually presents.
     *
     * An image presents itself. A note presents the **first of its links that resolves** —
     * not the first one written, so an `http` URL or a link to a renamed file is skipped
     * rather than leaving the players looking at black.
     */
    private suspend fun presentableFor(item: ContentItem, folder: ContentPath): Presentable {
        if (item.kind != ContentKind.Markdown) return Presentable(item.id, null)

        val repository = _repository.value ?: return Presentable(null, null)
        val document = noteReader?.read(item) ?: return Presentable(null, null)

        val image = document.imageLinks
            .firstNotNullOfOrNull { repository.resolveLink(it, folder) }

        return Presentable(image?.id, NoteBar.from(document))
    }

    /**
     * The campaign's in-world date, as written. Never per-note — see §23.
     *
     * Derived live from the config rather than stored, so a change to `Campagne.md` reaches
     * every note bar at once, including slots resolved long before.
     */
    val campaignDateIso: StateFlow<String?> = _campaignConfig
        .map { config ->
            config.info?.entries?.firstOrNull { it.key.equals("date", ignoreCase = true) }?.value
        }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    private data class Presentable(val imageId: ContentId?, val note: NoteBar?)

    // ---- Presentation actions ---------------------------------------------------

    /**
     * *Afficher*: transient, and deliberately does not touch the bank.
     *
     * A note shown this way fills the GM bar exactly as a slotted one does — the bar reflects
     * what is presented, not how it got there.
     */
    fun showNow(item: ContentItem, folder: ContentPath) {
        appScope.launch {
            val presented = presentableFor(item, folder)
            present(presented, fromSlot = null)
        }
    }

    fun recall(slot: SlotId) {
        when (val content = slots.contentOf(slot)) {
            is SlotContent.Filled -> present(Presentable(content.imageId, content.note), slot)
            // Nothing to present; the bar already shows the slot as missing or empty.
            is SlotContent.Missing, SlotContent.Empty -> Unit
        }
    }

    private fun present(presented: Presentable, fromSlot: SlotId?) {
        val note = presented.note
        if (note != null) {
            presentation.showNote(presented.imageId, note, fromSlot)
        } else if (presented.imageId != null) {
            presentation.show(presented.imageId, fromSlot)
        }
    }

    /**
     * Re-reads `Campagne.md`.
     *
     * Called from *Actualiser*, and whenever the GM surface comes back to the foreground —
     * which is what makes editing the file in Obsidian, in the DeX split view, show up in
     * Brigade without touching anything. Cheap: one small file.
     *
     * Not a filesystem watch. A `ContentObserver` over the Storage Access Framework is
     * unreliable across document providers, and re-reading on resume catches every case that
     * actually happens at a table.
     */
    fun refreshCampaign() {
        appScope.launch { reloadCampaignConfig() }
    }

    fun toggleInfo() {
        // Toggled immediately, then the file is re-read in the background. A button that
        // waits on disk before doing anything is the wrong trade at a table; if the file did
        // change, the panel dissolves to the new content a moment later, which is correct.
        presentation.setInfoMode(presentation.state.value.scene.mode != SceneMode.Info)
        appScope.launch { reloadCampaignConfig() }
    }

    /**
     * Re-reads `Campagne.md` and rebuilds the info panel.
     *
     * Cheap enough to do on every INFO toggle, which is how an edit made in Obsidian — in
     * the DeX split view, mid-session — shows up without Brigade watching the filesystem.
     */
    private suspend fun reloadCampaignConfig() {
        val config = campaignFile?.read() ?: CampaignConfig.EMPTY
        _campaignConfig.value = config
        _transitionSpec.value = TransitionSpec.byName(config.transition)

        val info = config.info
        if (info == null || info.isEmpty) {
            presentation.setInfo(null, null)
            return
        }

        // Resolved here, not in the renderer: a Frame must arrive fully determined, and a
        // path lookup is exactly the kind of work that could come out differently in the
        // two windows.
        val background = info.background
            ?.let { path -> _repository.value?.resolve(path)?.id }

        presentation.setInfo(CampaignInfo.build(info), background)
    }

    // ---- Slot actions -----------------------------------------------------------

    /** @param folderNames folders between the campaign root and [item], root excluded. */
    fun assignSlot(slot: SlotId, item: ContentItem, folderNames: List<String>) {
        val folder = ContentPath.of(folderNames)
        val path = ContentPath.of(folderNames, item.displayName)

        // Assigned optimistically so the bar fills the instant it is tapped; a note's image
        // and properties are read straight after and the slot updates in place. Only the
        // path is persisted, so the slow half never blocks the write.
        slots.assign(slot, path, item.displayName, imageId = item.id.takeIf { item.kind != ContentKind.Markdown })
        persistSlots()

        if (item.kind == ContentKind.Markdown) {
            appScope.launch {
                val presented = presentableFor(item, folder)
                slots.markResolved(slot.id, item.displayName, presented.imageId, presented.note)
            }
        }
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
