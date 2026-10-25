package com.mobilegamma.cakesync.orders

import com.mobilegamma.cakesync.menu.Extra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSuggestTest {
    private val extras = listOf(
        Extra("Stacking", "$10"), Extra("Colour", "$5"), Extra("Edible print + cut", "$30"),
        Extra("Topper simple", "$10"), Extra("Topper complex", "$15"), Extra("Topper super complex", "$30"),
    )

    @Test
    fun marioPhotoFillsTheOrder() {
        // Mario himself is a 3D character ($30); the goomba is complex.
        val found = mapOf(PhotoSuggest.TIERS to 2, PhotoSuggest.SIMPLE to 7, PhotoSuggest.COMPLEX to 1, PhotoSuggest.SUPER to 1, PhotoSuggest.PRINT to 1)
        val (o, note) = PhotoSuggest.apply(Order("1", size = "8\"", flavour = "Vanilla"), found, listOf("Blue", "Yellow"), extras)
        assertEquals(listOf(Tier(flavour = "Vanilla")), o.moreTiers)
        assertEquals(mapOf("Colour" to 2, "Stacking" to 1, "Topper simple" to 7, "Topper complex" to 1, "Topper super complex" to 1, "Edible print + cut" to 1), o.optionCounts)
        assertEquals(extras.map { it.name }.toSet(), o.options)
        assertTrue(note, note.startsWith("Photo: 2 tiers, Blue + Yellow, 1 3D character, 1 complex topper, 7 simple toppers, edible print."))
    }

    @Test
    fun keepsChosenTiersAndNamesMissingExtras() {
        val order = Order("2", moreTiers = listOf(Tier("6\"", "Rasmalai"), Tier("4\"", "Mango")))
        val (o, note) = PhotoSuggest.apply(order, mapOf(PhotoSuggest.TIERS to 2, PhotoSuggest.SIMPLE to 3), emptyList(), listOf(Extra("Eggless", "+20%")))
        assertEquals(listOf(Tier("6\"", "Rasmalai")), o.moreTiers)
        assertTrue(note, note.endsWith("Add menu extras for: stacking, simple topper."))
        assertEquals("Couldn't make out the cake in that photo.", PhotoSuggest.apply(order, emptyMap(), emptyList(), extras).second)
    }
}
