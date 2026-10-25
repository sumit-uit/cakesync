package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.MenuSettings
import com.mobilegamma.cakesync.menu.PriceTable

/** A cake that feeds the guests: sizes bottom tier first, how many it serves, and its price. */
data class ServingOption(val sizes: List<String>, val serves: Int, val price: Double?)

/** Picks cake sizes for a number of guests from how many each size serves. */
object ServingPlanner {

    /** The people a size serves: the low end of "8–10", so the cake is never too small. */
    fun serves(text: String): Int? = Regex("""\d+""").find(text)?.value?.toIntOrNull()

    /** size label → people, from the price table where set, else the onboarding guide. */
    fun servings(table: PriceTable?, menu: MenuSettings): Map<String, Int> {
        val guide = menu.servings.mapNotNull { s -> serves(s.serves)?.let { s.label to it } }.toMap()
        val labels = table?.sizes?.map { it.label }?.ifEmpty { null } ?: menu.servings.map { it.label }
        return labels.mapNotNull { label ->
            val own = table?.sizes?.firstOrNull { it.label == label }?.serves?.let(::serves)
            (own ?: guide[label])?.let { label to it }
        }.toMap()
    }

    /**
     * The smallest single cake, 2-tier and 3-tier cake that serve at least [guests]
     * (each tier smaller than the one under it), cheapest first when they serve the same.
     */
    fun options(guests: Int, flavour: String, table: PriceTable?, menu: MenuSettings): List<ServingOption> {
        if (guests <= 0) return emptyList()
        // Biggest first, so combinations come out bottom tier first.
        val sizes = servings(table, menu).entries.sortedByDescending { it.value }.map { it.key to it.value }
        fun combos(n: Int, from: Int): List<List<Pair<String, Int>>> =
            if (n == 0) listOf(emptyList()) else (from until sizes.size).flatMap { i -> combos(n - 1, i + 1).map { listOf(sizes[i]) + it } }
        return (1..3).mapNotNull { tiers ->
            combos(tiers, 0).filter { c -> c.sumOf { it.second } >= guests }.map { c ->
                val prices = c.map { Quote.tierPrice(Tier(it.first, flavour), table) }
                ServingOption(c.map { it.first }, c.sumOf { it.second }, if (prices.any { it == null }) null else prices.sumOf { it!! })
            }.minWithOrNull(compareBy<ServingOption> { it.serves }.thenBy { it.price ?: Double.MAX_VALUE })
        }
    }

    /** Puts [option] on the order: the bottom tier is the order's size, the rest become extra tiers. */
    fun apply(order: Order, option: ServingOption): Order {
        val flavours = order.tiers.map { it.flavour }
        val tiers = option.sizes.mapIndexed { i, s -> Tier(s, flavours.getOrNull(i)?.ifBlank { null } ?: order.flavour) }
        return order.copy(size = tiers.first().size, flavour = tiers.first().flavour, moreTiers = tiers.drop(1))
    }
}
