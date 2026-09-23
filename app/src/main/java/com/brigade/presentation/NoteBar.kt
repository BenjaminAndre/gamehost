package com.brigade.presentation

import com.brigade.content.NoteDocument
import java.text.Normalizer
import java.util.Locale

/**
 * The five phases of Wu Xing, as an emoji small enough to survive a crowded bar.
 *
 * Pictographic rather than the traditional five colours: coloured discs are authentic and
 * more compact, but they are only legible to someone who already knows the code, and they
 * share a silhouette. A sword for Métal also suits a wuxia campaign rather better than a cog.
 */
enum class WuXing(val emoji: String) {
    Terre("🪨"),
    Feu("🔥"),
    Eau("💧"),
    Metal("⚔️"),
    Bois("🌳"),
    ;

    companion object {
        /**
         * Matches the French value, ignoring case and accents — «Métal», «metal» and «MÉTAL»
         * all land on [Metal].
         *
         * Accent-insensitivity is not politeness. A missing accent while typing notes at
         * speed would otherwise silently drop the element from the bar, and nothing on screen
         * would say why.
         */
        fun parse(value: String?): WuXing? {
            val normalised = value?.fold() ?: return null
            return entries.firstOrNull { it.name.fold() == normalised }
        }

        private fun String.fold(): String =
            Normalizer.normalize(trim(), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(Locale.ROOT)

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
    }
}

/**
 * The GM-facing strip above the preview: `1137-01-09 · Jade-Fox · ⚔️ · Secte du Lotus`.
 *
 * **GM-facing only.** It lives on [Scene], but `frame()` does not consult it and [Frame]
 * carries no document field, so there is no path by which this can reach the player display.
 * That is a structural guarantee rather than a rule someone has to remember.
 */
data class NoteBar(
    /** The note's filename, without extension. */
    val name: String,
    val element: WuXing? = null,
    /**
     * Kept when [element] failed to match, so an unrecognised value shows as text instead of
     * vanishing. Same principle as an unknown `transition:` falling back to `cut`: a typo
     * must be visible.
     */
    val elementRaw: String? = null,
    val faction: String? = null,
) {
    companion object {
        /**
         * Note the **date is not here**. It is campaign state, not a property of the note,
         * and baking it in would make every slot's bar stale the moment `Campagne.md`
         * changed — a slot resolved on Tuesday would still be showing Tuesday's in-world
         * date on Thursday. The bar composes it at render time from the live config instead.
         */
        fun from(document: NoteDocument): NoteBar {
            val element = WuXing.parse(document.element)
            return NoteBar(
                name = document.name,
                element = element,
                elementRaw = document.element?.trim()?.takeIf { element == null && it.isNotEmpty() },
                faction = document.faction?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
