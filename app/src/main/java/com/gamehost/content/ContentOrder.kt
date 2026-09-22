package com.gamehost.content

import java.text.Collator
import java.util.Locale

/**
 * Folders first, then names in the order a French-reading GM expects.
 *
 * Two things a naive `sortedBy { it.displayName }` gets wrong, both of which a GM
 * notices immediately:
 *
 *  - **Accents.** Raw string ordering compares code points, so `Élise` sorts after
 *    `Zorro`. §21.1 requires locale-aware collation.
 *  - **Numbers.** Raw ordering puts `carte10` before `carte2`, which scrambles exactly
 *    the numbered map sequences campaigns are full of.
 */
object ContentOrder {

    /** SECONDARY: accent-sensitive but case-insensitive, which is what a file list wants. */
    private val collator: Collator = Collator.getInstance(Locale.FRENCH).apply {
        strength = Collator.SECONDARY
    }

    val comparator: Comparator<ContentItem> =
        compareBy<ContentItem> { if (it.kind == ContentKind.Folder) 0 else 1 }
            .thenComparator { a, b -> compareNatural(a.displayName, b.displayName) }

    /**
     * Compares two names by alternating runs of digits and non-digits, so digit runs
     * compare by numeric value and everything else compares by French collation.
     */
    fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val startA = i
            val startB = j

            if (a[i].isDigit() && b[j].isDigit()) {
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++

                // Compare without leading zeros: shorter number is smaller, and equal
                // lengths compare lexicographically, which for digits is numeric order.
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                while (i < a.length && !a[i].isDigit()) i++
                while (j < b.length && !b[j].isDigit()) j++
                val cmp = collator.compare(a.substring(startA, i), b.substring(startB, j))
                if (cmp != 0) return cmp
            }

            // Guard against a pathological input where neither side advanced; without
            // it the loop above could spin forever on collator-ignorable characters.
            if (i == startA && j == startB) return a.compareTo(b)
        }

        // One name is a prefix of the other: the shorter one sorts first.
        return (a.length - i) - (b.length - j)
    }
}
