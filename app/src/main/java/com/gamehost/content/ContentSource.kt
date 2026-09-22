package com.gamehost.content

/**
 * A readable tree of campaign content.
 *
 * The only implementation in v0.1 is `saf.DocumentTreeSource`, but the presentation
 * layer never names it — which is what preserves the option of other storage backends
 * later (§8) without touching anything above this interface.
 *
 * Deliberately returns lists, not `Flow`s: v0.1 does not observe the filesystem.
 * Watching for external edits is a `ContentObserver` added behind
 * [ContentRepository.invalidate] later, and does not change this interface.
 */
interface ContentSource {

    /** The campaign root. Always a folder. */
    val rootId: ContentId

    suspend fun listChildren(parent: ContentId): List<ContentItem>

    suspend fun itemOf(id: ContentId): ContentItem?

    /** False once the root has become unreadable — folder deleted, SD card pulled, grant revoked. */
    suspend fun isAvailable(): Boolean
}
