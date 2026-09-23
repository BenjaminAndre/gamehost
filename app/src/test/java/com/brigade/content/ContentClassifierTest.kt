package com.brigade.content

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentClassifierTest {

    @Test
    fun `directory mime is a folder`() {
        assertEquals(
            ContentKind.Folder,
            ContentClassifier.classify(ContentClassifier.MIME_DIRECTORY, "Portraits"),
        )
    }

    @Test
    fun `supported image mimes are images`() {
        assertEquals(ContentKind.Image, ContentClassifier.classify("image/png", "a.png"))
        assertEquals(ContentKind.Image, ContentClassifier.classify("image/jpeg", "a.jpg"))
        assertEquals(ContentKind.Image, ContentClassifier.classify("image/webp", "a.webp"))
    }

    /**
     * The case the extension fallback exists for: providers genuinely serve `.md` and
     * sometimes `.webp` as octet-stream, and trusting the mime alone would hide files
     * the GM can plainly see in a file manager.
     */
    @Test
    fun `octet-stream falls back to the extension`() {
        assertEquals(
            ContentKind.Markdown,
            ContentClassifier.classify("application/octet-stream", "Jade-Fox.md"),
        )
        assertEquals(
            ContentKind.Image,
            ContentClassifier.classify("application/octet-stream", "Taverne.WEBP"),
        )
    }

    @Test
    fun `text markdown mime is markdown`() {
        assertEquals(ContentKind.Markdown, ContentClassifier.classify("text/markdown", "notes.md"))
    }

    @Test
    fun `formats outside v0 1 are Other, not Image`() {
        assertEquals(ContentKind.Other, ContentClassifier.classify("image/gif", "anim.gif"))
        assertEquals(ContentKind.Other, ContentClassifier.classify("image/svg+xml", "map.svg"))
        assertEquals(ContentKind.Other, ContentClassifier.classify("audio/mpeg", "theme.mp3"))
    }

    @Test
    fun `an extensionless unknown file is Other`() {
        assertEquals(ContentKind.Other, ContentClassifier.classify("", "LICENSE"))
    }
}
