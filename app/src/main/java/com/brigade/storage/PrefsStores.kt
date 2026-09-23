package com.brigade.storage

import android.content.Context
import com.brigade.content.RootStore
import com.brigade.presentation.PresentationSnapshot
import com.brigade.presentation.SnapshotStore

/**
 * `SharedPreferences` implementations of the two tiny app-private stores.
 *
 * Not DataStore: one string and four scalars do not justify its artifact, its
 * coroutine machinery and its okio/protobuf surface, and the interfaces they implement
 * live in `content/` and `presentation/` — so swapping the backing store later is one
 * file, not a refactor.
 *
 * Note what is *not* here: the slot bank. That points at campaign content, so it lives
 * with the campaign in `.brigade/slots.json` (§6.1), not in app-private storage.
 */
private const val PREFS_NAME = "brigade"

class PrefsRootStore(context: Context) : RootStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_ROOT, null)

    override fun save(treeUri: String?) {
        prefs.edit().putString(KEY_ROOT, treeUri).apply()
    }

    private companion object {
        const val KEY_ROOT = "root_tree_uri"
    }
}

class PrefsSnapshotStore(context: Context) : SnapshotStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): PresentationSnapshot? {
        val imageId = prefs.getString(KEY_IMAGE, null)
        val scaling = prefs.getString(KEY_SCALING, null) ?: return null
        val slot = prefs.getInt(KEY_SLOT, -1)
        return PresentationSnapshot(
            imageId = imageId,
            scaling = scaling,
            liveSlot = slot.takeIf { it >= 0 },
        )
    }

    override fun save(snapshot: PresentationSnapshot) {
        prefs.edit()
            .putString(KEY_IMAGE, snapshot.imageId)
            .putString(KEY_SCALING, snapshot.scaling)
            .putInt(KEY_SLOT, snapshot.liveSlot ?: -1)
            .apply()
    }

    private companion object {
        const val KEY_IMAGE = "presentation_image_id"
        const val KEY_SCALING = "presentation_scaling"
        const val KEY_SLOT = "presentation_live_slot"
    }
}
