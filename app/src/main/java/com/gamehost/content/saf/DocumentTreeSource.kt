package com.gamehost.content.saf

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.gamehost.content.ContentClassifier
import com.gamehost.content.ContentId
import com.gamehost.content.ContentItem
import com.gamehost.content.ContentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A [ContentSource] over a Storage Access Framework document tree.
 *
 * The only file in Gamehost that knows `DocumentsContract` exists.
 *
 * **Why a raw projected cursor and not `DocumentFile`:** `DocumentFile.listFiles()`
 * queries once for the ids, then `getName()`, `isDirectory()`, `length()` and
 * `lastModified()` each issue *another* `ContentResolver.query()` — `1 + 4N` binder
 * round trips for N children. A single projected cursor is one binder call filled
 * through a shared `CursorWindow`. On a 300-portrait folder that is the difference
 * between a multi-second stall and tens of milliseconds, and it also lets us drop the
 * `androidx.documentfile` dependency entirely.
 */
class DocumentTreeSource(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ContentSource {

    /**
     * **Invariant:** every [ContentId] in Gamehost is a *document* URI built using the
     * tree, never the raw tree URI.
     *
     * The raw tree URI has no document-id segment, so `getDocumentId()` throws on it
     * while `getTreeDocumentId()` is the only thing that works. Normalising the root
     * here removes that special case from every other call site. It is the single most
     * common SAF bug and it is worth one line to make it unrepresentable.
     */
    override val rootId: ContentId = ContentId(
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        ).toString(),
    )

    override suspend fun listChildren(parent: ContentId): List<ContentItem> = withContext(io) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(Uri.parse(parent.value)),
        )

        val items = ArrayList<ContentItem>(64)
        runCatching {
            // No sortOrder argument on purpose: ExternalStorageProvider ignores it and
            // the contract does not require providers to honour it. Ordering is applied
            // in ContentRepository, where it is also unit-testable.
            resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                val columns = Columns(cursor)
                while (cursor.moveToNext()) {
                    items += cursor.readItem(columns, parent, treeUri) ?: continue
                }
            }
        }
        items
    }

    override suspend fun itemOf(id: ContentId): ContentItem? = withContext(io) {
        runCatching {
            resolver.query(Uri.parse(id.value), PROJECTION, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.readItem(Columns(cursor), parent = null, treeUri = treeUri)
            }
        }.getOrNull()
    }

    override suspend fun isAvailable(): Boolean = withContext(io) {
        runCatching {
            resolver.query(
                Uri.parse(rootId.value),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { true } ?: false
        }.getOrDefault(false)
    }

    private companion object {

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/**
 * Column indices resolved once per cursor rather than once per row.
 *
 * Uses `getColumnIndex` rather than `getColumnIndexOrThrow`: providers are allowed to
 * omit SIZE and LAST_MODIFIED, and a missing optional column should not take down the
 * whole folder listing.
 */
private class Columns(cursor: Cursor) {
    val id = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
    val name = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val mime = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
    val size = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
    val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
}

private fun Cursor.readItem(columns: Columns, parent: ContentId?, treeUri: Uri): ContentItem? {
    val documentId = columns.id.takeIf { it >= 0 }?.let { getString(it) } ?: return null
    val displayName = columns.name.takeIf { it >= 0 }?.let { getString(it) } ?: return null
    val mimeType = columns.mime.takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }.orEmpty()

    return ContentItem(
        id = ContentId(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()),
        parentId = parent,
        displayName = displayName,
        mimeType = mimeType,
        kind = ContentClassifier.classify(mimeType, displayName),
        sizeBytes = columns.size.takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L,
        lastModified = columns.modified.takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L,
    )
}
