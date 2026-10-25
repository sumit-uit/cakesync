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

    /** One thing the photo showed, and the menu option that charges for it. */
    private data class Pick(val what: String, val extra: Extra?, val count: Int, val onTop: Boolean = false)

    /** [found] maps the labels above to counts; [colours] are the icing colours. Returns the new order and a note. */
    fun apply(order: Order, found: Map<String, Int>, colours: List<String>, extras: List<Extra>): Pair<Order, String> {
        fun extra(vararg words: String) = extras.firstOrNull { e -> words.all { e.name.contains(it, true) } }
        val tiers = found[TIERS] ?: 0
        // Toppers sit on the top tier; colours, stacking and prints belong to the whole cake.
        val picks = listOf(
            Pick("colour", extra("colour") ?: extra("color"), colours.size),
            Pick("stacking", extra("stack"), tiers - 1),
            Pick("simple topper", extra("topper", "simple"), found[SIMPLE] ?: 0, onTop = true),
            Pick("complex topper", extra("topper", "complex")?.takeUnless { it.name.contains("super", true) }, found[COMPLEX] ?: 0, onTop = true),
            Pick("3D character topper", extra("topper", "super"), found[SUPER] ?: 0, onTop = true),
            // Prints are priced per page: one page holds several.
            Pick("print", extra("print"), if ((found[PRINT] ?: 0) > 0) 1 else 0),
        )
        var o = order
        if (tiers >= 2) o = o.withTierCount(tiers)
        val missing = mutableListOf<String>()
        for (pick in picks) {
            if (pick.count <= 0) continue
            val e = pick.extra
            if (e == null) { missing += pick.what; continue }
            o = if (pick.onTop) o.withToppers(o.topTier, e.name, pick.count)
            else o.copy(options = o.options + e.name, optionCounts = o.optionCounts + (e.name to pick.count))
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
