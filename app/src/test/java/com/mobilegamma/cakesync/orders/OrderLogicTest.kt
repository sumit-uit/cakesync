package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.DesignLevel
import com.mobilegamma.cakesync.menu.Extra
import com.mobilegamma.cakesync.menu.FlavourRow
import com.mobilegamma.cakesync.menu.MenuSettings
import com.mobilegamma.cakesync.menu.PriceTable
import com.mobilegamma.cakesync.menu.SizeCol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OrderLogicTest {
    private val table = PriceTable(
        "cake",
        listOf(SizeCol("6\""), SizeCol("8\""), SizeCol("10\"")),
        listOf(FlavourRow("Vanilla", listOf(60.0, 75.0, 95.0)), FlavourRow("Rasmalai", listOf(75.0, 100.0, 130.0))),
    )
    private val menu = MenuSettings(
        currency = "$",
        levels = listOf(DesignLevel("simple", "Simple"), DesignLevel("fondant", "Fondant", 80.0)),
        extras = listOf(Extra("Eggless", "+20%"), Extra("Edible image", "$15 per page"), Extra("Fruit chunks", "$5–10")),
    )
    private val settings = OrderSettings(configured = true, advancePercent = 50, paymentNote = "Interac e-Transfer", cancelDays = 4)

    @Test
    fun pricesFromTableLevelAndOptions() {
        val order = Order("1", size = "8\"", flavour = "Rasmalai", levelId = "simple")
        assertEquals(100.0, Quote.calculate(order, table, menu)!!, 1e-9)
        // Eggless +20% on the base, edible image +$15.
        assertEquals(135.0, Quote.calculate(order.copy(options = setOf("Eggless", "Edible image")), table, menu)!!, 1e-9)
        // Fondant starts at $80, so a $60 vanilla 6" becomes $80.
        assertEquals(80.0, Quote.calculate(Order("2", size = "6\"", flavour = "Vanilla", levelId = "fondant"), table, menu)!!, 1e-9)
        // Unknown size: no price.
        assertNull(Quote.calculate(Order("3", size = "14\"", flavour = "Vanilla"), table, menu))
        // An agreed price wins.
        assertEquals(150.0, Quote.total(order.copy(price = 150.0), table, menu)!!, 1e-9)
        assertEquals(50.0, Quote.advance(100.0, settings), 1e-9)
        assertEquals(5.0, Quote.amountOf(menu.extras[2])!!, 1e-9)
    }

    @Test
    fun countsPerUnitOptionsAndBreaksThePriceDown() {
        val costing = menu.copy(extras = listOf(Extra("Topper simple", each = 8.0), Extra("Eggless", percent = 20.0)))
        // 10" + 6" Rasmalai: 130 + 75. No text parsing: the price really is 8.0 per topper.
        var order = Order("t", size = "10\"", flavour = "Rasmalai", moreTiers = listOf(Tier("6\"", "Rasmalai")))
        assertEquals(205.0, Quote.calculate(order, table, costing)!!, 1e-9)
        val split = Quote.breakdown(order.copy(options = setOf("Eggless")), table, costing)!!
        assertEquals(205.0, split.cake, 1e-9)
        assertEquals(41.0, split.options, 1e-9)
        assertEquals(246.0, split.total, 1e-9)
        // Itemised: each tier, then the option with its unit price.
        assertEquals(listOf(130.0, 75.0), split.cakeLines.map { it.amount })
        assertEquals(listOf("Eggless"), split.optionLines.map { it.name })
        assertEquals("+20% of the cake", split.optionLines[0].detail("$"))
        // 6 toppers on the top tier: 205 + 48.
        order = order.withToppers(order.topTier, "Topper simple", 6)
        assertEquals(listOf(0, 6), order.topperPlan["Topper simple"])
        assertEquals(6, order.count("Topper simple"))
        assertEquals(253.0, Quote.calculate(order, table, costing)!!, 1e-9)
        // 2 more on the bottom tier add up: 8 toppers.
        order = order.withToppers(0, "Topper simple", 2)
        assertEquals(8, order.count("Topper simple"))
        assertEquals(269.0, Quote.calculate(order, table, costing)!!, 1e-9)
        assertEquals("simple: 2 on tier 1, 6 on tier 2", order.topperPlanText(costing.extras))
        // Percentage extras still never count.
        assertEquals(1, order.copy(options = order.options + "Eggless").count("Eggless"))
        // Clearing every topper drops the option and its count.
        order = order.withToppers(0, "Topper simple", 0).withToppers(order.topTier, "Topper simple", 0)
        assertTrue(order.options.isEmpty())
        assertTrue(order.topperPlan.isEmpty())
        assertTrue(order.optionCounts.isEmpty())
        assertEquals(205.0, Quote.calculate(order, table, costing)!!, 1e-9)
    }

    @Test
    fun topperTotalAddsToTheTopTierAndRemovesFromItFirst() {
        // "The customer wants 2 more simple toppers": they go on the top tier.
        var order = Order("t", size = "8\"", flavour = "Vanilla", moreTiers = listOf(Tier("6\"", "Vanilla")))
        order = order.withTopperTotal("Topper simple", 4)
        assertEquals(listOf(0, 4), order.topperPlan["Topper simple"])
        assertEquals(4, order.count("Topper simple"))
        // Two more: top tier again.
        order = order.withTopperTotal("Topper simple", 6)
        assertEquals(listOf(0, 6), order.topperPlan["Topper simple"])
        // Moving one down by hand, then a total change takes it off the top tier first.
        order = order.withToppers(0, "Topper simple", 1)
        assertEquals(listOf(1, 6), order.topperPlan["Topper simple"])
        order = order.withTopperTotal("Topper simple", 5)
        assertEquals(listOf(1, 4), order.topperPlan["Topper simple"])
        // All the way to zero drops the option.
        order = order.withTopperTotal("Topper simple", 0)
        assertTrue(order.topperPlan.isEmpty())
        assertTrue(order.options.isEmpty())
        assertTrue(order.optionCounts.isEmpty())
        // Asking for more than there is changes nothing.
        assertEquals(order, order.withTopperTotal("Topper simple", -2))
    }

    @Test
    fun pricesToppersDetectedInAPhotoOneLineEach() {
        val costing = menu.copy(extras = listOf(Extra("Topper simple", each = 8.0), Extra("Colour", each = 5.0)))
        val order = Order("t", size = "8\"", flavour = "Vanilla")
            .withTopperTotal("Topper simple", 3)
            .let { it.copy(options = it.options + "Colour", optionCounts = it.optionCounts + ("Colour" to 2)) }
        val split = Quote.breakdown(order, table, costing)!!
        assertEquals(listOf("Topper simple", "Colour"), split.optionLines.map { it.name })
        assertEquals(listOf(24.0, 10.0), split.optionLines.map { it.amount })
        assertEquals("3 × \$8", split.optionLines[0].detail("$"))
        assertEquals(109.0, split.total, 1e-9)
    }

    @Test
    fun seedsTierPlansForCountsSetBeforeTieredToppers() {
        // An order read from the web form: a topper count but no per-tier plan yet.
        val order = Order(
            "t", size = "8\"", flavour = "Vanilla", moreTiers = listOf(Tier("6\"", "Vanilla")),
            options = setOf("Topper simple"), optionCounts = mapOf("Topper simple" to 3),
        )
        val seeded = order.seedingToppers(listOf("Topper simple"))
        assertEquals(listOf(0, 3), seeded.topperPlan["Topper simple"])
        assertEquals(3, seeded.count("Topper simple"))
        // Already planned: seeding again changes nothing.
        assertEquals(seeded, seeded.seedingToppers(listOf("Topper simple")))
    }

    @Test
    fun tierCountKeepsTiersAndToppersInStep() {
        // 1 → 3 tiers, toppers on the top tier, then 5 tiers and back down to 1.
        var order = Order("t", size = "10\"", flavour = "Rasmalai").withTierCount(3)
        assertEquals(3, order.tierCount)
        assertEquals(listOf(Tier(flavour = "Rasmalai"), Tier(flavour = "Rasmalai")), order.moreTiers)
        order = order.withToppers(2, "Topper simple", 4).withToppers(1, "Topper simple", 1)
        assertEquals(5, order.count("Topper simple"))
        assertEquals(listOf(0, 1, 4), order.topperPlan["Topper simple"])
        assertEquals(listOf(0, 1, 4, 0, 0), order.withTierCount(5).topperPlan["Topper simple"])
        assertEquals(5, order.withTierCount(5).count("Topper simple"))
        // The bottom tier had no toppers, so a single-tier cake loses the option entirely.
        order = order.withTierCount(1)
        assertEquals(1, order.tierCount)
        assertTrue(order.topperPlan.isEmpty())
        assertFalse("Topper simple" in order.options)
        assertTrue(order.optionCounts.isEmpty())
    }

    @Test
    fun tiersAndCountedExtras() {
        val costing = menu.copy(
            extras = listOf(
                Extra("Eggless", "+20%"), Extra("Stacking", "$10"), Extra("Board + box", "$20"), Extra("Colour", "$5"),
                Extra("Edible print + cut", "$30"), Extra("Topper complex", "$15"), Extra("Topper simple", "$10"),
            ),
        )
        // The Mario cake: 8" + 6" Vanilla, 2 icing colours, 1 complex + 7 simple toppers.
        val mario = Order(
            "m", size = "8\"", flavour = "Vanilla", moreTiers = listOf(Tier("6\"", "Vanilla")),
            options = setOf("Stacking", "Board + box", "Colour", "Edible print + cut", "Topper complex", "Topper simple"),
            optionCounts = mapOf("Colour" to 2, "Topper simple" to 7),
        )
        assertEquals(290.0, Quote.calculate(mario, table, costing)!!, 1e-9)
        // Different flavour per tier: 6" Rasmalai is $75 instead of $60.
        assertEquals(305.0, Quote.calculate(mario.copy(moreTiers = listOf(Tier("6\"", "Rasmalai"))), table, costing)!!, 1e-9)
        // Eggless +20% applies once, to both tiers: 135 * 0.2 = 27.
        assertEquals(317.0, Quote.calculate(mario.copy(options = mario.options + "Eggless", optionCounts = mario.optionCounts + ("Eggless" to 3)), table, costing)!!, 1e-9)
        // A tier without a size isn't priced yet.
        assertNull(Quote.calculate(mario.copy(moreTiers = listOf(Tier())), table, costing))
        assertEquals("8\" Vanilla + 6\" Vanilla", mario.cakeSummary("Simple"))
        assertTrue(mario.optionsText().contains("Colour ×2"))
    }

    @Test
    fun parsesDatesAndTimes() {
        val today = LocalDate.of(2026, 9, 30)
        assertEquals(LocalDate.of(2026, 10, 12), OrderDates.parse("12 Oct", today))
        assertEquals(LocalDate.of(2026, 10, 12), OrderDates.parse("October 12th", today))
        assertEquals(LocalDate.of(2027, 3, 5), OrderDates.parse("Mar 5", today)) // already passed this year
        assertEquals(LocalDate.of(2026, 10, 1), OrderDates.parse("tomorrow", today))
        assertEquals(LocalDate.of(2026, 11, 20), OrderDates.parse("2026-11-20", today))
        assertEquals(LocalDate.of(2026, 10, 25), OrderDates.parse("25/10", today))
        assertNull(OrderDates.parse("soon", today))
        assertEquals(LocalTime.of(17, 30), OrderDates.parseTime("5:30 pm"))
        assertEquals(LocalTime.of(10, 0), OrderDates.parseTime("10am"))
    }

    @Test
    fun templateRoundTrip() {
        val template = OrderMessage.template(settings.copy(customQuestions = listOf("Number of guests")), "Soni Bakes", design = "Pink Floral Cake")
        assertTrue(template.contains("Design: Pink Floral Cake"))
        assertTrue(template.contains("Number of guests:"))
        val reply = template
            .replace("Name: ", "Name: Priya")
            .replace("Phone: ", "Phone: +1 416 555 0100")
            .replace("Size: ", "Size: 8\"")
            .replace("Flavour: ", "Flavour: Rasmalai")
            .replace("Eggless: ", "Eggless: Yes")
            .replace("Message on cake: ", "Message on cake: Happy 5th Birthday Aarav")
            .replace("Date needed: ", "Date needed: Oct 12")
            .replace("Pickup / delivery time: ", "Pickup / delivery time: 4 pm")
            .replace("Pickup or delivery (address): ", "Pickup or delivery (address): Delivery to 12 King St")
            .replace("Number of guests: ", "Number of guests: 20")
        val order = OrderMessage.toOrder(reply, settings.copy(customQuestions = listOf("Number of guests")), "o1", menu.extras, LocalDate.of(2026, 9, 30))
        assertEquals("Priya", order.customer)
        assertEquals("+1 416 555 0100", order.contact)
        assertEquals("Pink Floral Cake", order.designTitle)
        assertEquals("8\"", order.size)
        assertEquals("Rasmalai", order.flavour)
        assertEquals(setOf("Eggless"), order.options)
        assertEquals("Happy 5th Birthday Aarav", order.message)
        assertEquals(LocalDate.of(2026, 10, 12), order.due)
        assertEquals(LocalTime.of(16, 0), order.time)
        assertTrue(order.delivery)
        assertEquals("12 King St", order.address)
        assertEquals("20", order.answers["Number of guests"])
    }

    @Test
    fun quoteMessageHasTotalAdvanceAndPolicy() {
        val order = Order("1", customer = "Priya", size = "8\"", flavour = "Rasmalai", due = LocalDate.of(2026, 10, 12), options = setOf("Eggless"))
        val text = OrderMessage.quote(order, 120.0, null, menu, settings, "Soni Bakes")
        assertTrue(text, text.contains("Hi Priya"))
        assertTrue(text, text.contains("Total: $120"))
        assertTrue(text, text.contains("50% advance: $60 (Interac e-Transfer)"))
        assertTrue(text, text.contains("Cancel 4 days before"))
        val title = OrderMessage.eventTitle(order, null)
        assertEquals("💬 Priya · 8\" Rasmalai (Pickup)", title)
        assertFalse(OrderStatus.DONE.active)
    }

    @Test
    fun readsTheWebFormMessage() {
        // What docs/order/index.html sends.
        val text = """
            Hi Soni Bakes! I'd like to order a cake:
            🙋 Name: Priya Test
            📞 Phone: 4165550100
            🎂 Design: Pink Floral Cake
            📏 Size: 8"
            🍰 Flavour: Rasmalai
            🎉 Occasion:
            ✍️ Message on cake: Happy 5th Birthday Aarav
            🥚 Eggless: Yes
            ⏰ Pickup / delivery time: 5:30 PM
            🚗 Pickup or delivery (address): Delivery to 12 King St
            📅 Date needed: 2026-10-10 (Sat, 10 Oct)
            ❓ Number of guests: 20
            🎨 Style: Fondant
            ➕ Extras: Edible image
            📋 Cake type: Cakes
            💰 Estimate: $135
            📷 (I'll attach a reference photo)
        """.trimIndent()
        val s = settings.copy(customQuestions = listOf("Number of guests"))
        val order = OrderMessage.toOrder(text, s, "o1", menu.extras, LocalDate.of(2026, 9, 30), menu.levels)
        assertEquals(LocalDate.of(2026, 10, 10), order.due)
        assertEquals(LocalTime.of(17, 30), order.time)
        assertEquals("fondant", order.levelId)
        assertEquals(setOf("Eggless", "Edible image"), order.options)
        assertEquals("12 King St", order.address)
        assertEquals("20", order.answers["Number of guests"])
        // The same total the form showed.
        assertEquals(135.0, Quote.calculate(order, table, menu)!!, 1e-9)
    }

    @Test
    fun formLinkCarriesTheMenuAndContacts() {
        val s = settings.copy(whatsapp = "+1 (416) 555-0100", instagram = "@sonibakes", bookingLink = "calendar.app.google/abc", menuLink = "sonibakeart.ca/menu")
        val json = OrderForm.json(s, "Soni Bakes", "$", listOf(FormTable("Cakes", table)), menu.levels, menu.extras, design = "Pink Floral Cake")
        val link = OrderForm.link(json)
        assertTrue(link, link.startsWith(OrderForm.BASE_URL + "#z"))
        val back = org.json.JSONObject(OrderForm.decode(link.substringAfter("#z")))
        assertEquals("14165550100", back.getString("wa"))
        assertEquals("sonibakes", back.getString("ig"))
        assertEquals("Pink Floral Cake", back.getString("d"))
        val cakes = back.getJSONArray("t").getJSONObject(0)
        assertEquals("Cakes", cakes.getString("n"))
        assertEquals(100.0, cakes.getJSONArray("f").getJSONArray(1).getDouble(2), 1e-9)
        val msg = OrderForm.shareText(s, "Soni Bakes", link)
        assertTrue(msg, msg.contains(link))
        assertTrue(msg, msg.contains("https://calendar.app.google/abc"))
        assertTrue(msg, msg.contains("https://sonibakeart.ca/menu"))
        assertTrue(msg, msg.contains("https://instagram.com/sonibakes"))
    }
}
