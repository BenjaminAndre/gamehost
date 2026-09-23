package com.brigade.content

/**
 * A path relative to the campaign root, e.g. `Cartes/Donjon/Salle-4.png`.
 *
 * Slots are stored as relative paths and never as document URIs (§6.1). A document URI
 * is provider-specific: it survives neither a folder move, nor a reinstall, nor the
 * campaign being opened on another device. Storing one inside
 * `.brigade/slots.json` would defeat the entire reason for putting that file in the
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

    /** The folder containing this path. Empty for something at the campaign root. */
    val parent: ContentPath
        get() = of(segments.dropLast(1))

    companion object {

        fun of(segments: List<String>): ContentPath =
            ContentPath(segments.filter { it.isNotEmpty() }.joinToString("/"))

        /** Path of [fileName] inside the folder reached by [folderNames] from the root. */
        fun of(folderNames: List<String>, fileName: String): ContentPath =
            of(folderNames + fileName)

        /**
         * Resolves a possibly-relative link [target] against the folder it was written in.
         *
         * Pure string arithmetic, deliberately. Walking `..` through the real tree would need
         * a parent lookup on every step; because slots and notes already carry
         * campaign-relative paths, the same answer falls out of combining two strings — and
         * it is testable on the JVM, which walking the tree would not be.
         *
         * @return null when the target climbs above the campaign root, which is a link
         * pointing outside the campaign and cannot be resolved.
         */
        fun relativeTo(folder: ContentPath, target: String): ContentPath? {
            val result = folder.segments.toMutableList()

            target.split('/').forEach { segment ->
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (result.isEmpty()) return null else result.removeAt(result.lastIndex)
                    else -> result += segment
                }
            }

            return of(result).takeIf { !it.isEmpty }
        }
    }
}
