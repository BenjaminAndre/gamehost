package com.gamehost.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamehost.GamehostApp
import com.gamehost.content.ContentId
import com.gamehost.content.ContentItem
import com.gamehost.content.ContentKind
import com.gamehost.content.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One level of the campaign tree. [name] is null for the campaign root, whose label is
 * a string resource — keeping French out of Kotlin (§21.1).
 */
data class FolderCrumb(val id: ContentId, val name: String?)

enum class BrowsingError { Unavailable }

/**
 * What the GM is *looking at*.
 *
 * Deliberately separate from `PresentationState`, which is what the players see
 * (§16 Trap 4). With the slot bank this is more than a modelling nicety: browsing
 * genuinely never touches the presentation, so the GM can wander the whole campaign
 * mid-scene without revealing anything.
 */
data class BrowsingState(
    val stack: List<FolderCrumb> = emptyList(),
    val entries: List<ContentItem> = emptyList(),
    val loading: Boolean = false,
    val error: BrowsingError? = null,
) {
    val currentFolder: ContentId? get() = stack.lastOrNull()?.id
    val canGoUp: Boolean get() = stack.size > 1

    /** Folders between the campaign root and here, root excluded. Builds slot paths. */
    val folderNames: List<String> get() = stack.drop(1).mapNotNull { it.name }

    val folders: List<ContentItem> get() = entries.filter { it.kind == ContentKind.Folder }
    val images: List<ContentItem> get() = entries.filter { it.kind == ContentKind.Image }
    val isEmpty: Boolean get() = folders.isEmpty() && images.isEmpty()
}

class GmViewModel(
    private val repositoryFlow: StateFlow<ContentRepository?>,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _browsing = MutableStateFlow(BrowsingState())
    val browsing: StateFlow<BrowsingState> = _browsing.asStateFlow()

    init {
        viewModelScope.launch {
            repositoryFlow.collectLatest { repository ->
                if (repository == null) {
                    _browsing.value = BrowsingState()
                } else {
                    restoreLocation(repository)
                }
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------
    //
    // The back stack IS BrowsingState.stack, so every operation is a list operation and
    // no navigation library is needed.

    fun enter(folder: ContentItem) {
        if (folder.kind != ContentKind.Folder) return
        _browsing.update { it.copy(stack = it.stack + FolderCrumb(folder.id, folder.displayName)) }
        reload()
    }

    fun up() {
        if (!_browsing.value.canGoUp) return
        _browsing.update { it.copy(stack = it.stack.dropLast(1)) }
        reload()
    }

    /** Jumps to the crumb at [index]; index 0 is the campaign root. */
    fun jumpTo(index: Int) {
        val stack = _browsing.value.stack
        if (index !in stack.indices || index == stack.lastIndex) return
        _browsing.update { it.copy(stack = it.stack.take(index + 1)) }
        reload()
    }

    fun refresh() {
        viewModelScope.launch {
            repositoryFlow.value?.invalidate()
            reload(force = true)
        }
    }

    // ---- Loading ----------------------------------------------------------------

    private suspend fun restoreLocation(repository: ContentRepository) {
        // The location is saved as folder *names*, not ids: names survive a process
        // death and a reinstall, which document ids under a fresh grant may not.
        val savedSegments = savedState.get<ArrayList<String>>(KEY_LOCATION).orEmpty()

        var stack = listOf(FolderCrumb(repository.rootId, null))
        var folder = repository.rootId
        for (segment in savedSegments) {
            val match = runCatching { repository.children(folder) }.getOrNull()
                ?.firstOrNull { it.displayName == segment && it.kind == ContentKind.Folder }
                ?: break
            stack = stack + FolderCrumb(match.id, match.displayName)
            folder = match.id
        }

        _browsing.value = BrowsingState(stack = stack)
        reload()
    }

    private fun reload(force: Boolean = false) {
        val repository = repositoryFlow.value ?: return
        val folder = _browsing.value.currentFolder ?: return

        viewModelScope.launch {
            _browsing.update { it.copy(loading = true, error = null) }
            val entries = runCatching { repository.children(folder, refresh = force) }
            _browsing.update { state ->
                entries.fold(
                    onSuccess = { state.copy(entries = it, loading = false, error = null) },
                    onFailure = {
                        state.copy(entries = emptyList(), loading = false, error = BrowsingError.Unavailable)
                    },
                )
            }
            savedState[KEY_LOCATION] = ArrayList(_browsing.value.folderNames)
        }
    }

    companion object {

        private const val KEY_LOCATION = "browsing_location"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GamehostApp
                GmViewModel(app.graph.repository, createSavedStateHandle())
            }
        }
    }
}
