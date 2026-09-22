package com.gamehost.content.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.gamehost.content.CampaignConfig
import com.gamehost.content.CampaignInfoConfig
import com.gamehost.content.CampaignInfoEntry
import com.gamehost.content.ContentPath
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Reads `<campaign root>/Campagne.md`.
 *
 * The only file in Gamehost that knows YAML exists — `ArchitectureTest` forbids `org.yaml`
 * everywhere else, so the rest of the app can only see the plain [CampaignConfig] this
 * returns.
 *
 * Gamehost **reads** this file and never writes to it. It is the GM's note, authored in
 * Obsidian; the only file Gamehost creates is `.gamehost/slots.json`.
 */
class CampaignFile(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * @return the parsed config, or [CampaignConfig.EMPTY] when the file is absent,
     * unreadable, or malformed. A campaign without one is completely normal, and a broken
     * one must not take the app down mid-session.
     */
    suspend fun read(): CampaignConfig = withContext(io) {
        runCatching {
            val file = findFile() ?: return@runCatching CampaignConfig.EMPTY
            val text = resolver.openInputStream(file)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@runCatching CampaignConfig.EMPTY
            parse(text)
        }.getOrDefault(CampaignConfig.EMPTY)
    }

    private fun findFile(): Uri? {
        val root = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(root),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idColumn < 0 || nameColumn < 0) return null

            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn).equals(FILE_NAME, ignoreCase = true)) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(idColumn) ?: continue,
                    )
                }
            }
        }
        return null
    }

    companion object {

        const val FILE_NAME = "Campagne.md"

        /**
         * The YAML block between a leading `---` and the next `---`.
         *
         * Null when the document does not open with one, which is the ordinary case for a
         * campaign note that simply has no Gamehost config.
         */
        fun frontmatter(markdown: String): String? {
            val lines = markdown.lineSequence().iterator()
            if (!lines.hasNext() || !lines.next().trim().isFence()) return null

            val block = StringBuilder()
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.trim().isFence()) return block.toString()
                block.appendLine(line)
            }
            // Unterminated: treat as absent rather than guessing where it ended.
            return null
        }

        /**
         * Pure, so the whole format is testable on the JVM — where CI is the only check.
         *
         * **Total.** A malformed file yields [CampaignConfig.EMPTY] rather than throwing:
         * a stray bracket in a note must not take the app down between two scenes, and the
         * GM's recourse is the same either way — the config is ignored until they fix it.
         */
        fun parse(markdown: String): CampaignConfig = runCatching {
            val block = frontmatter(markdown) ?: return@runCatching CampaignConfig.EMPTY

            // SafeConstructor: the default one can instantiate arbitrary classes named by
            // YAML tags. The file is the GM's own, so the risk is slight, but there is no
            // reason whatsoever to accept it.
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val root = yaml.load<Any?>(block) as? Map<*, *> ?: return@runCatching CampaignConfig.EMPTY
            val gamehost = root[KEY_GAMEHOST] as? Map<*, *> ?: return@runCatching CampaignConfig.EMPTY

            CampaignConfig(
                transition = (gamehost[KEY_TRANSITION] as? String)?.takeIf { it.isNotBlank() },
                info = (gamehost[KEY_INFO] as? Map<*, *>)?.let(::parseInfo),
            )
        }.getOrDefault(CampaignConfig.EMPTY)

        private fun parseInfo(info: Map<*, *>): CampaignInfoConfig {
            val entries = (info[KEY_ENTRIES] as? List<*>).orEmpty().mapNotNull { item ->
                // Each entry is a single-key map — `- Lieu: Auberge` — which is what keeps
                // the file pleasant to write AND preserves the GM's ordering, where a plain
                // map would not.
                val pair = (item as? Map<*, *>)?.entries?.firstOrNull() ?: return@mapNotNull null
                val key = pair.key?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CampaignInfoEntry(key, scalarToString(pair.value))
            }

            return CampaignInfoConfig(
                background = (info[KEY_BACKGROUND] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::ContentPath),
                showLunarState = info[KEY_LUNAR] == true,
                entries = entries,
            )
        }

        /** Renders a YAML scalar back to the text the GM wrote. */
        private fun scalarToString(value: Any?): String = when (value) {
            null -> ""
            is Date -> isoDate(value)
            else -> value.toString()
        }

        /**
         * Returns a YAML timestamp to the `yyyy-MM-dd` the GM typed.
         *
         * YAML resolves an unquoted `1127-03-12` to a timestamp, so the value never reaches
         * us as a string, and `Date.toString()` would hand back a locale-formatted mess with
         * a timezone in it.
         *
         * The subtlety, which cost a red build: snakeyaml constructs that [Date] through
         * [GregorianCalendar], and **`GregorianCalendar` applies the Julian calendar before
         * the 1582 cutover.** So the instant it produces for `1127-03-12` is Julian 12 March
         * — which proleptic ISO, the calendar `java.time` speaks, calls 19 March. Reading it
         * back with `java.time` therefore shifted every twelfth-century date by seven days.
         *
         * Reading it back through the *same* hybrid calendar round-trips exactly, for dates
         * either side of the reform. Which is all this function should ever do: the meaning
         * of a historical date is decided once, in `HistoricalCalendar`, not here.
         */
        private fun isoDate(value: Date): String {
            val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            calendar.time = value
            return String.format(
                Locale.ROOT,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
            )
        }

        private fun String.isFence(): Boolean = this == "---"

        private const val KEY_GAMEHOST = "gamehost"
        private const val KEY_TRANSITION = "transition"
        private const val KEY_INFO = "info"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_LUNAR = "show_lunar_state"
    }
}
