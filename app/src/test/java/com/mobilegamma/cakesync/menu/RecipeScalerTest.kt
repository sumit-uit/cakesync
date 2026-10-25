package com.mobilegamma.cakesync.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeScalerTest {
    private val eight = Pan(PanShape.ROUND, 8.0)

    @Test fun eightInchToTwelveInch() {
        val f = RecipeScaler.factor(eight, Pan(PanShape.ROUND, 12.0))
        assertEquals(2.25, f, 1e-9)
        assertEquals("1125 gm - Mix\n608 gm - Water\n56 gm - Oil", RecipeScaler.scale("500 gm - Mix\n270 gm - Water\n25 gm - Oil", f))
    }

    @Test fun fractionsShapesAndPlainLines() {
        val f = RecipeScaler.factor(Pan(PanShape.ROUND, 6.0), Pan(PanShape.SQUARE, 6.0)) // 36 / 28.27 = 1.27
        assertEquals("2½ cups flour", RecipeScaler.scaleLine("2 cups flour", f))
        assertEquals("  1¾ tsp salt", RecipeScaler.scaleLine("  1 1/2 tsp salt", 1.17))
        assertEquals("1 tsp vanilla", RecipeScaler.scaleLine("½ tsp vanilla", 2.0))
        assertEquals("Frosting:", RecipeScaler.scaleLine("Frosting:", f))
        assertEquals(117.0 / (Math.PI * 16), RecipeScaler.factor(eight, Pan(PanShape.RECT, 9.0, 13.0)), 1e-9)
    }
}
