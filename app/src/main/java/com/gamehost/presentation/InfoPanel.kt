package com.gamehost.presentation

/**
 * One row of the campaign info panel: a label and its value, both already formatted.
 *
 * The generic case. A field only stops being one of these when it either *computes*
 * something the GM would otherwise maintain by hand, or *formats* something this row
 * would render badly.
 */
data class InfoEntry(val label: String, val value: String)

/**
 * The campaign info panel, **fully resolved**.
 *
 * ### The contract, and why it matters
 *
 * Everything here is already what the renderer will draw. No ISO dates it has to parse,
 * no locale it has to consult, no [com.gamehost.content.ContentPath] it has to look up,
 * no font metric it has to measure.
 *
 * That is not tidiness. The renderer runs **twice**, in two windows, and any work left
 * inside it is work that could come out differently in each — which is exactly the §5
 * guarantee failing. A date is a formatted `String` here and a moon phase is a `Float`,
 * so the two windows cannot disagree about them even in principle.
 *
 * Derived from `Campagne.md` and re-read whenever INFO is toggled, so it is never
 * persisted — see [PresentationSnapshot], which deliberately drops it.
 */
data class InfoPanel(
    /** Heading for the panel, from the `title` field. */
    val title: String? = null,

    /** Promoted above the entries — the `location` field, the thing players look for first. */
    val headline: String? = null,

    /** The `date` field, already written out in French. Its own line, not a label/value row. */
    val dateLine: String? = null,

    /** Generic rows, in the order the GM wrote them. */
    val entries: List<InfoEntry> = emptyList(),

    /**
     * Moon phase in `[0,1)`: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter.
     * Null when the campaign did not ask for it, or asked without supplying a date.
     */
    val moonPhase: Float? = null,
) {
    val isEmpty: Boolean
        get() = title == null && headline == null && dateLine == null &&
            entries.isEmpty() && moonPhase == null
}
