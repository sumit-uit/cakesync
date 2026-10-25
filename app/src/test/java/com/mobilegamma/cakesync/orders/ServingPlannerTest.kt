package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.FlavourRow
import com.mobilegamma.cakesync.menu.MenuSettings
import com.mobilegamma.cakesync.menu.PriceTable
import com.mobilegamma.cakesync.menu.SizeCol
import org.junit.Assert.assertEquals
import org.junit.Test

class ServingPlannerTest {
    // Sonibakeart prices; serves from the default guide: 6" 8, 8" 14, 10" 24, 12" 35.
    private val table = PriceTable(
        "cake",
        listOf(SizeCol("6\""), SizeCol("8\""), SizeCol("10\""), SizeCol("12\"")),
        listOf(FlavourRow("Vanilla", listOf(60.0, 75.0, 95.0, 120.0))),
    )
    private val menu = MenuSettings()

    @Test
    fun fortyGuests() {
        val o = ServingPlanner.options(40, "Vanilla", table, menu)
        // No single size serves 40; 12"+6" serves 43 for $180; 10"+8"+6" serves 46 for $230.
        assertEquals(listOf(ServingOption(listOf("12\"", "6\""), 43, 180.0), ServingOption(listOf("10\"", "8\"", "6\""), 46, 230.0)), o)
    }

    @Test
    fun smallPartyAndTableOverride() {
        assertEquals(ServingOption(listOf("8\""), 14, 75.0), ServingPlanner.options(12, "Vanilla", table, menu).first())
        // The baker's own table says 8" serves 20.
        val own = table.copy(sizes = table.sizes.map { if (it.label == "8\"") it.copy(serves = "20–24") else it })
        assertEquals(listOf("8\""), ServingPlanner.options(18, "Vanilla", own, menu).first().sizes)
        assertEquals(emptyList<ServingOption>(), ServingPlanner.options(0, "Vanilla", table, menu))
    }

    @Test
    fun applyKeepsTierFlavours() {
        val order = Order("1", size = "6\"", flavour = "Rasmalai", moreTiers = listOf(Tier("4\"", "Mango")))
        val o = ServingPlanner.apply(order, ServingOption(listOf("12\"", "8\"", "6\""), 57, null))
        assertEquals(listOf(Tier("12\"", "Rasmalai"), Tier("8\"", "Mango"), Tier("6\"", "Rasmalai")), o.tiers)
    }
}
