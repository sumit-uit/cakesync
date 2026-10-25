package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.Extra

/**
 * Turns what was seen in a customer's photo into order changes the baker can still edit:
 * tiers, and counts on the menu extras whose names mention colour, stacking, toppers or print.
 */
object PhotoSuggest {
    const val TIERS = "tiers"
    const val SIMPLE = "topper_simple"
    const val COMPLEX = "topper_complex"
    /** 3D character figures (Mario, Bluey…): the most fondant and work. */
    const val SUPER = "topper_super"
    const val PRINT = "edible_print"

    /** [found] maps the labels above to counts; [colours] are the icing colours. Returns the new order and a note. */
    fun apply(order: Order, found: Map<String, Int>, colours: List<String>, extras: List<Extra>): Pair<Order, String> {
        fun extra(vararg words: String) = extras.firstOrNull { e -> words.all { e.name.contains(it, true) } }
        val tiers = found[TIERS] ?: 0
        val wanted = listOf(
            "colour" to ((extra("colour") ?: extra("color")) to colours.size),
            "stacking" to (extra("stack") to (tiers - 1)),
            "simple topper" to (extra("topper", "simple") to (found[SIMPLE] ?: 0)),
            "complex topper" to (extra("topper", "complex")?.takeUnless { it.name.contains("super", true) } to (found[COMPLEX] ?: 0)),
            "3D character topper" to (extra("topper", "super") to (found[SUPER] ?: 0)),
            // Prints are priced per page: one page holds several.
            "print" to (extra("print") to if ((found[PRINT] ?: 0) > 0) 1 else 0),
        )
        var o = order
        if (tiers >= 2) {
            val more = o.moreTiers.take(tiers - 1)
            o = o.copy(moreTiers = more + List(tiers - 1 - more.size) { Tier(flavour = o.flavour) })
        }
        val missing = mutableListOf<String>()
        for ((what, pair) in wanted) {
            val (e, n) = pair
            if (n <= 0) continue
            if (e == null) { missing += what; continue }
            o = o.copy(options = o.options + e.name, optionCounts = o.optionCounts + (e.name to n))
        }
        val seen = listOfNotNull(
            tiers.takeIf { it > 0 }?.let { if (it == 1) "1 tier" else "$it tiers" },
            colours.takeIf { it.isNotEmpty() }?.joinToString(" + "),
            found[SUPER]?.takeIf { it > 0 }?.let { "$it 3D character" + if (it > 1) "s" else "" },
            found[COMPLEX]?.takeIf { it > 0 }?.let { "$it complex topper" + if (it > 1) "s" else "" },
            found[SIMPLE]?.takeIf { it > 0 }?.let { "$it simple topper" + if (it > 1) "s" else "" },
            found[PRINT]?.takeIf { it > 0 }?.let { "edible print" },
        )
        val note = if (seen.isEmpty()) "Couldn't make out the cake in that photo." else buildString {
            append("Photo: ${seen.joinToString(", ")}. Check the counts and adjust with − / +.")
            if (missing.isNotEmpty()) append(" Add menu extras for: ${missing.joinToString()}.")
        }
        return o to note
    }
}
