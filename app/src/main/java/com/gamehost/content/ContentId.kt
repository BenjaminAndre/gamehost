package com.gamehost.content

/**
 * Opaque identifier for one piece of campaign content.
 *
 * Deliberately a [String] and not an `android.net.Uri`. That single choice keeps this
 * package and all of `presentation/` free of `android.*`, which means:
 *
 *  - the interesting logic unit-tests on the JVM with no Robolectric;
 *  - it persists to `SavedStateHandle` and to JSON with no serialization code;
 *  - no `Parcelable`/`@Parcelize` ceremony anywhere.
 *
 * Conversion to a real `Uri` happens in `content/saf/` and nowhere else.
 */
@JvmInline
value class ContentId(val value: String)

/**
 * What a [ContentItem] is, as far as Gamehost cares.
 *
 * This is a flat enum rather than a type hierarchy on purpose (§16 Trap 7). Formats
 * that arrive later — audio, video, PDF — become new entries here and new branches in
 * the renderer, never new subclasses of an asset base class.
 */
enum class ContentKind {
    Folder,
    Image,
    Markdown,

    /** Recognised, listed, but not something v0.1 can present. */
    Other,
}

/**
 * The spec's `ContentLocation` (§8): document id, display name, mime type and parent.
 *
 * The presentation layer never sees this type — only [ContentId] crosses that boundary.
 */
data class ContentItem(
    val id: ContentId,
    val parentId: ContentId?,
    val displayName: String,
    val mimeType: String,
    val kind: ContentKind,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
)
