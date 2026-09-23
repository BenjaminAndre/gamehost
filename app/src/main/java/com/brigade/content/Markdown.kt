package com.brigade.content

/**
 * Just enough Markdown parsing to find a note's frontmatter and the images it links.
 *
 * **Not a renderer.** §15 says Brigade *interprets* Markdown and offers presentation actions;
 * it does not display the prose. Keeping that line means this file stays a hundred lines of
 * string handling instead of becoming a Markdown engine.
 */
object Markdown {

    private const val FENCE = "---"

    /** The YAML block between a leading `---` and the next `---`, or null when there is none. */
    fun frontmatter(text: String): String? = split(text).first

    /** Everything after the frontmatter, or the whole text when there is none. */
    fun body(text: String): String = split(text).second

    /**
     * Image link targets, in the order they appear in the **body**.
     *
     * Both syntaxes, because a campaign authored in Obsidian will contain both: `![[…]]` is
     * what Obsidian inserts when an image is dragged in, and `![](…)` is what arrives from
     * templates, pasted content, or a note written anywhere else.
     *
     * Targets are returned raw and unfiltered — including `http` URLs and links to files that
     * do not exist. Deciding what resolves is [ContentRepository]'s job, and it is the reason
     * "the first image" means the first one that actually *resolves* rather than the first
     * one written.
     */
    fun imageLinks(text: String): List<String> =
        IMAGE_LINK.findAll(body(text)).mapNotNull { match ->
            val wikilink = match.groupValues[1]
            val inline = match.groupValues[2]
            when {
                wikilink.isNotEmpty() -> cleanWikilink(wikilink)
                inline.isNotEmpty() -> cleanInline(inline)
                else -> null
            }
        }.filter { it.isNotEmpty() }.toList()

    /**
     * `![[target]]` or `![alt](target)`.
     *
     * Deliberately not multiline-aware: a link split across lines is not something Obsidian
     * produces, and matching across newlines would swallow whole paragraphs on malformed input.
     */
    private val IMAGE_LINK = Regex("""!\[\[([^\]\n]+)]]|!\[[^\]\n]*]\(([^)\n]+)\)""")

    /** `![[Jade-Fox.png|300]]` — everything after `|` is a display hint. */
    private fun cleanWikilink(raw: String): String = raw.substringBefore('|').trim()

    /** `![](path "title")`, `![](<path with spaces>)`, `![](path%20with%20spaces)`. */
    private fun cleanInline(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<")) {
            return trimmed.removePrefix("<").substringBefore('>').trim().decodeSpaces()
        }
        val titleAt = trimmed.indexOf(" \"")
        val target = if (titleAt >= 0) trimmed.substring(0, titleAt) else trimmed
        return target.trim().decodeSpaces()
    }

    /**
     * Obsidian percent-encodes spaces in inline links. Only `%20` is handled: a campaign
     * folder is not a web server, and a general URL decoder would mangle a literal `%` in a
     * filename.
     */
    private fun String.decodeSpaces(): String = replace("%20", " ")

    private fun split(text: String): Pair<String?, String> {
        val lines = text.lines()
        if (lines.isEmpty() || lines[0].trim() != FENCE) return null to text

        val closing = (1 until lines.size).firstOrNull { lines[it].trim() == FENCE }
        // Unterminated: treat the frontmatter as absent rather than guessing where it ended.
            ?: return null to text

        return lines.subList(1, closing).joinToString("\n", postfix = "\n") to
            lines.subList(closing + 1, lines.size).joinToString("\n")
    }
}
