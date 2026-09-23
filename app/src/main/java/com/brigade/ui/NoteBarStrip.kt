package com.brigade.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brigade.presentation.NoteBar

/**
 * The GM's readout for the note currently being presented:
 *
 * ```
 * 1137-01-09 · Jade-Fox · ⚔️ · Secte du Lotus
 * ```
 *
 * **GM surface only.** It reads [NoteBar] off the scene; the player renderer has no access to
 * it, because [com.brigade.presentation.Frame] carries no document field.
 *
 * Horizontally scrollable rather than truncating: a long faction name would otherwise eat the
 * character name, and the name is the part the GM is looking for. Sliding is a worse
 * interaction than reading, but a far better one than guessing.
 */
@Composable
fun NoteBarStrip(
    bar: NoteBar,
    /**
     * The campaign's in-world date, read live rather than carried on [bar] — so editing
     * `Campagne.md` updates every bar at once instead of leaving already-resolved slots
     * showing the date they were resolved on.
     */
    dateIso: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val segments = buildList<@Composable () -> Unit> {
            dateIso?.let { date ->
                add {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            add {
                Text(
                    text = bar.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            // An unrecognised element shows as text rather than vanishing, so a typo in the
            // note is visible — the same rule an unknown `transition:` follows.
            (bar.element?.emoji ?: bar.elementRaw)?.let { element ->
                add {
                    Text(
                        text = element,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }

            bar.faction?.let { faction ->
                add {
                    Text(
                        text = faction,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        // Its own token, NOT colorScheme.error — that red already marks a
                        // missing slot in the bar just below, and two reds side by side, one
                        // of them meaning "broken", would be confusable in dim light.
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            segment()
        }
    }
}
