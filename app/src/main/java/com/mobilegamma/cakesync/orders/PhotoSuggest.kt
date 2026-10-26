package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.Extra
import com.mobilegamma.cakesync.menu.Pricing

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

    /**
     * [found] maps the labels above to counts; [colours] are the icing colours. [currency] adds
     * what each detected thing costs, so the baker can read the photo back as a price.
     * Returns the new order and a note.
     */
    fun apply(order: Order, found: Map<String, Int>, colours: List<String>, extras: List<Extra>, currency: String = ""): Pair<Order, String> {
        fun extra(vararg words: String) = extras.firstOrNull { e -> words.all { e.name.contains(it, true) } }
        fun plural(n: Int) = if (n > 1) "s" else ""
        /** "3 simple toppers · $24", or just the label when the option has no per-unit price. */
        fun priced(label: String, count: Int, e: Extra?): String {
            val each = e?.eachValue ?: return label
            if (currency.isBlank()) return label
            return "$label · ${Pricing.format(each * count, currency)}"
        }
        val tiers = found[TIERS] ?: 0
        // "Super complex" in the menu, or a plain "3D topper" — both mean the character.
        val superTopper = extra("topper", "super") ?: extra("topper", "3d")
        val complexTopper = extra("topper", "complex")?.takeUnless { it.name.contains("super", true) }
        // Toppers sit on the top tier; colours, stacking and prints belong to the whole cake.
        val picks = listOf(
            Pick("colour", extra("colour") ?: extra("color"), colours.size),
            Pick("stacking", extra("stack"), tiers - 1),
            Pick("simple topper", extra("topper", "simple"), found[SIMPLE] ?: 0, onTop = true),
            Pick("complex topper", complexTopper, found[COMPLEX] ?: 0, onTop = true),
            Pick("3D character topper", superTopper, found[SUPER] ?: 0, onTop = true),
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
            found[SUPER]?.takeIf { it > 0 }?.let { priced("$it 3D character" + plural(it), it, superTopper) },
            found[COMPLEX]?.takeIf { it > 0 }?.let { priced("$it complex topper" + plural(it), it, complexTopper) },
            found[SIMPLE]?.takeIf { it > 0 }?.let { priced("$it simple topper" + plural(it), it, extra("topper", "simple")) },
            found[PRINT]?.takeIf { it > 0 }?.let { priced("edible print", 1, extra("print")) },
        )
        val note = if (seen.isEmpty()) "Couldn't make out the cake in that photo." else buildString {
            append("Photo: ${seen.joinToString(", ")}.")
            append(" Check the counts and adjust with − / +.")
            if (missing.isNotEmpty()) append(" Add menu extras for: ${missing.joinToString()}.")
        }
        return o to note
    }
}
