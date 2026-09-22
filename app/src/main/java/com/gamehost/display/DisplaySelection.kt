package com.gamehost.display

/**
 * A display, reduced to the facts the choice depends on.
 *
 * Exists so the selection rule below is a pure function over plain data rather than
 * over `android.view.Display`, which cannot be constructed in a JVM unit test.
 */
data class DisplayInfo(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val presentationFlag: Boolean,
    val isPrivate: Boolean,
)

/**
 * Picks the display to present to, or null when there is none.
 *
 * The [selfDisplayId] exclusion is the rule that makes this DeX-correct: **do not
 * assume "external means player"**. In standard DeX the external display *is* the
 * desktop and the GM Activity is launched onto it, so the player surface is defined as
 * "a display that is not the one the GM UI is on", never as "the non-default display".
 */
fun pickPlayerDisplay(candidates: List<DisplayInfo>, selfDisplayId: Int): DisplayInfo? =
    candidates
        .filter { it.id != selfDisplayId }
        .filterNot { it.isPrivate }
        .sortedByDescending { it.presentationFlag }
        .firstOrNull()
