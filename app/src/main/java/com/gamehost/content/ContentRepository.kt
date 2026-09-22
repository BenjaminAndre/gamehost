package com.gamehost.content

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caching, filtering and ordering over a [ContentSource].
 *
 * Lives on the application graph, not on a ViewModel, so the cache survives Activity
 * recreation — which on a DeX tablet happens every time the window is dragged or the
 * device is rotated.
 */
class ContentRepository(private val source: ContentSource) {

    private val mutex = Mutex()
    private val cache = LinkedHashMap<ContentId, List<ContentItem>>()

    val rootId: ContentId get() = source.rootId

    suspend fun children(folder: ContentId, refresh: Boolean = false): List<ContentItem> =
        mutex.withLock {
            if (!refresh) cache[folder]?.let { return@withLock it }
            val listed = source.listChildren(folder)
                // Hides .gamehost/ and .git/ from the browser. §2.3 keeps application
                // state out of the way of campaign content, and this is the other half
                // of that bargain: the GM never has to look at it.
                .filterNot { it.displayName.startsWith(".") }
                .sortedWith(ContentOrder.comparator)
            cache[folder] = listed
            listed
        }

    suspend fun images(folder: ContentId): List<ContentItem> =
        children(folder).filter { it.kind == ContentKind.Image }

    suspend fun folders(folder: ContentId): List<ContentItem> =
        children(folder).filter { it.kind == ContentKind.Folder }

    /**
     * Walks [path] from the campaign root by display name.
     *
     * Returns null when any segment is missing or is not the kind it needs to be —
     * a slot whose file was renamed resolves to null and is rendered as *missing*
     * rather than silently vanishing (§6.1).
     */
    suspend fun resolve(path: ContentPath): ContentItem? {
        val segments = path.segments
        if (segments.isEmpty()) return null

        var folder = source.rootId
        segments.forEachIndexed { index, segment ->
            val match = children(folder).firstOrNull { it.displayName == segment } ?: return null
            if (index == segments.lastIndex) return match
            if (match.kind != ContentKind.Folder) return null
            folder = match.id
        }
        return null
    }

    suspend fun isAvailable(): Boolean = source.isAvailable()

    suspend fun invalidate() = mutex.withLock { cache.clear() }
}
