package com.brigade.presentation

import com.brigade.content.ContentId
import com.brigade.content.ContentPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The control bar holds exactly this many slots, plus the permanent blank control. */
const val SLOT_COUNT: Int = 6

@JvmInline
value class SlotId(val index: Int)

sealed interface SlotContent {

    data object Empty : SlotContent

    /**
     * The slot holds a path that no longer resolves — the file was renamed, moved or
     * deleted. Rendered distinctly from [Empty] on purpose: a silent gap looks like the
     * GM never assigned anything, which is the wrong thing to believe mid-session.
     */
    data class Missing(val path: ContentPath) : SlotContent

    data class Filled(
        val path: ContentPath,
        val displayName: String,

        /**
         * What the players see. Null for a note that links no image, which is a legitimate
         * slot — the GM bar still fills in and the players get black.
         */
        val imageId: ContentId?,

        /** Present only when the slot holds a Markdown note rather than an image. */
        val note: NoteBar? = null,
    ) : SlotContent {
        val isNote: Boolean get() = note != null
    }
}

data class Slot(val id: SlotId, val content: SlotContent)

data class SlotBankState(val slots: List<Slot>) {

    operator fun get(id: SlotId): Slot = slots[id.index]

    /** The paths to persist, keyed by slot index. Empty slots are omitted. */
    fun paths(): Map<Int, ContentPath> = slots.mapNotNull { slot ->
        when (val content = slot.content) {
            is SlotContent.Filled -> slot.id.index to content.path
            is SlotContent.Missing -> slot.id.index to content.path
            SlotContent.Empty -> null
        }
    }.toMap()

    companion object {
        val EMPTY: SlotBankState =
            SlotBankState((0 until SLOT_COUNT).map { Slot(SlotId(it), SlotContent.Empty) })

        /** Builds a bank from persisted paths, before any of them have been resolved. */
        fun unresolved(paths: Map<Int, ContentPath>): SlotBankState = SlotBankState(
            (0 until SLOT_COUNT).map { index ->
                val path = paths[index]
                Slot(SlotId(index), if (path == null) SlotContent.Empty else SlotContent.Missing(path))
            },
        )
    }
}

/**
 * The six recall slots, shared across the whole campaign tree.
 *
 * Pure Kotlin. Persistence (JSON, Storage Access Framework) lives in
 * `content/saf/SafSlotStore`, and resolution of paths to [ContentId]s lives on the
 * application graph — neither belongs to the bank itself, which is what keeps this
 * unit-testable without a device.
 */
class SlotBank(initial: SlotBankState = SlotBankState.EMPTY) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<SlotBankState> = _state.asStateFlow()

    /** Assigning to an occupied slot overwrites it, by design — no confirmation mid-session. */
    fun assign(
        slot: SlotId,
        path: ContentPath,
        displayName: String,
        imageId: ContentId?,
        note: NoteBar? = null,
    ) = replace(slot, SlotContent.Filled(path, displayName, imageId, note))

    fun clear(slot: SlotId) = replace(slot, SlotContent.Empty)

    /** Marks a slot whose path resolved, once the folder tree has been read. */
    fun markResolved(
        slot: SlotId,
        displayName: String,
        imageId: ContentId?,
        note: NoteBar? = null,
    ) {
        val path = when (val content = _state.value[slot].content) {
            is SlotContent.Missing -> content.path
            is SlotContent.Filled -> content.path
            SlotContent.Empty -> return
        }
        replace(slot, SlotContent.Filled(path, displayName, imageId, note))
    }

    fun markMissing(slot: SlotId) {
        val content = _state.value[slot].content
        val path = when (content) {
            is SlotContent.Missing -> return
            is SlotContent.Filled -> content.path
            SlotContent.Empty -> return
        }
        replace(slot, SlotContent.Missing(path))
    }

    fun replaceAll(state: SlotBankState) {
        _state.value = state
    }

    fun contentOf(slot: SlotId): SlotContent = _state.value[slot].content

    private fun replace(slot: SlotId, content: SlotContent) {
        _state.update { current ->
            SlotBankState(current.slots.map { if (it.id.index == slot.index) Slot(it.id, content) else it })
        }
    }
}
