package com.gamehost.content.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.gamehost.content.ContentPath
import com.gamehost.presentation.SLOT_COUNT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and writes `<campaign root>/.gamehost/slots.json`.
 *
 * §2.3 sanctions a small optional `.gamehost/` directory for application state, which
 * is where the slot bank belongs: it points at campaign content, so it should travel
 * with the campaign rather than live in app-private storage that a reinstall wipes.
 *
 * Serialised with `org.json`, which ships in the framework — no `kotlinx.serialization`
 * dependency for one flat array. Indented and slot-ordered so the file produces a
 * readable Git diff.
 */
class SafSlotStore(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Cached once resolved: avoids re-walking the tree on every debounced save. */
    @Volatile
    private var fileUri: Uri? = null

    suspend fun load(): Map<Int, ContentPath> = withContext(io) {
        runCatching {
            val file = findFile() ?: return@runCatching emptyMap()
            val text = resolver.openInputStream(file)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@runCatching emptyMap()
            parse(text)
        }.getOrDefault(emptyMap())
    }

    /** @return false when the bank could not be written; callers surface this, never crash. */
    suspend fun save(paths: Map<Int, ContentPath>): Boolean = withContext(io) {
        runCatching {
            val file = findFile() ?: createFile() ?: return@runCatching false
            // Mode "wt" truncates. Without it, writing a shorter document leaves the
            // tail of the previous, longer one behind and the JSON no longer parses.
            resolver.openOutputStream(file, "wt")?.use { output ->
                output.write(serialise(paths).toByteArray(Charsets.UTF_8))
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun rootDocumentUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    private fun findFile(): Uri? {
        fileUri?.let { return it }
        val directory = findChild(rootDocumentUri(), DIRECTORY_NAME) ?: return null
        return findChild(directory, FILE_NAME)?.also { fileUri = it }
    }

    private fun createFile(): Uri? {
        val root = rootDocumentUri()
        val directory = findChild(root, DIRECTORY_NAME)
            ?: DocumentsContract.createDocument(
                resolver,
                root,
                DocumentsContract.Document.MIME_TYPE_DIR,
                DIRECTORY_NAME,
            )
            ?: return null

        return DocumentsContract.createDocument(resolver, directory, MIME_JSON, FILE_NAME)
            ?.also { fileUri = it }
    }

    /**
     * Finds a child by display name.
     *
     * Falls back to a prefix match because some providers derive the extension from the
     * mime type and will happily create `slots.json.json`. Without the fallback we would
     * fail to find our own file and create a new one on every launch.
     */
    private fun findChild(parent: Uri, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(parent),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        var prefixMatch: String? = null
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idColumn < 0 || nameColumn < 0) return null

            while (cursor.moveToNext()) {
                val childName = cursor.getString(nameColumn) ?: continue
                val childId = cursor.getString(idColumn) ?: continue
                if (childName == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                }
                if (prefixMatch == null && childName.startsWith(name)) prefixMatch = childId
            }
        }
        return prefixMatch?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }
    }

    private fun parse(text: String): Map<Int, ContentPath> {
        val entries = JSONObject(text).optJSONArray("slots") ?: return emptyMap()
        val result = LinkedHashMap<Int, ContentPath>()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            // The file is 1-based because a human reads it; SlotId is 0-based.
            val slotNumber = entry.optInt(KEY_SLOT, -1)
            val path = entry.optString(KEY_PATH, "")
            if (slotNumber in 1..SLOT_COUNT && path.isNotEmpty()) {
                result[slotNumber - 1] = ContentPath(path)
            }
        }
        return result
    }

    private fun serialise(paths: Map<Int, ContentPath>): String {
        val entries = JSONArray()
        paths.entries.sortedBy { it.key }.forEach { (index, path) ->
            entries.put(
                JSONObject()
                    .put(KEY_SLOT, index + 1)
                    .put(KEY_PATH, path.value),
            )
        }
        return JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_SLOTS, entries)
            .toString(2)
    }

    private companion object {
        const val DIRECTORY_NAME = ".gamehost"
        const val FILE_NAME = "slots.json"
        const val MIME_JSON = "application/json"

        const val FORMAT_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_SLOTS = "slots"
        const val KEY_SLOT = "slot"
        const val KEY_PATH = "path"
    }
}
