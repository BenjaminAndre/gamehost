package com.gamehost.content

/**
 * Decides what a document is from its mime type, falling back to its extension.
 *
 * The fallback is not defensive padding: document providers genuinely report
 * `application/octet-stream` for `.md`, and sometimes for `.webp`, so trusting the
 * mime type alone hides files the GM can plainly see in a file manager.
 */
object ContentClassifier {

    const val MIME_DIRECTORY: String = "vnd.android.document/directory"

    /** The formats §2.1 lists for v0.1. GIF, HEIC and SVG are Other until asked for. */
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    private val IMAGE_SUBTYPES = setOf("png", "jpeg", "jpg", "webp")
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")

    fun classify(mimeType: String, displayName: String): ContentKind = when {
        mimeType == MIME_DIRECTORY -> ContentKind.Folder
        mimeType.startsWith("image/") && mimeType.substringAfter('/') in IMAGE_SUBTYPES -> ContentKind.Image
        mimeType == "text/markdown" -> ContentKind.Markdown
        else -> fromExtension(displayName)
    }

    private fun fromExtension(displayName: String): ContentKind =
        when (displayName.substringAfterLast('.', "").lowercase()) {
            in IMAGE_EXTENSIONS -> ContentKind.Image
            in MARKDOWN_EXTENSIONS -> ContentKind.Markdown
            else -> ContentKind.Other
        }
}
