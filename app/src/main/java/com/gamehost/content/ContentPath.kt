package com.gamehost.content

/**
 * A path relative to the campaign root, e.g. `Cartes/Donjon/Salle-4.png`.
 *
 * Slots are stored as relative paths and never as document URIs (§6.1). A document URI
 * is provider-specific: it survives neither a folder move, nor a reinstall, nor the
 * campaign being opened on another device. Storing one inside
 * `.gamehost/slots.json` would defeat the entire reason for putting that file in the
 * campaign folder rather than in app-private storage.
 *
 * Separator is always `/`, matching both POSIX and the SAF document-id convention, and
 * making the JSON readable and diffable in Git.
 */
@JvmInline
value class ContentPath(val value: String) {

    val segments: List<String>
        get() = value.split('/').filter { it.isNotEmpty() }

    val fileName: String
        get() = segments.lastOrNull().orEmpty()

    val isEmpty: Boolean
        get() = segments.isEmpty()

    companion object {

        fun of(segments: List<String>): ContentPath =
            ContentPath(segments.filter { it.isNotEmpty() }.joinToString("/"))

        /** Path of [fileName] inside the folder reached by [folderNames] from the root. */
        fun of(folderNames: List<String>, fileName: String): ContentPath =
            of(folderNames + fileName)
    }
}
