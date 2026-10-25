package com.mobilegamma.cakesync.menu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToLong

enum class PanShape(val label: String) { ROUND("Round"), SQUARE("Square"), RECT("Rectangle") }

/** A cake pan in inches; [length] only matters for rectangles. */
data class Pan(val shape: PanShape, val width: Double, val length: Double = width) {
    val area: Double
        get() = when (shape) {
            PanShape.ROUND -> PI * width * width / 4
            PanShape.SQUARE -> width * width
            PanShape.RECT -> width * length
        }

    val label: String
        get() = when (shape) {
            PanShape.ROUND -> "${Pricing.trim(width)}\" round"
            PanShape.SQUARE -> "${Pricing.trim(width)}\" square"
            PanShape.RECT -> "${Pricing.trim(width)} × ${Pricing.trim(length)}\""
        }

    companion object {
        val presets = listOf(
            Pan(PanShape.ROUND, 6.0), Pan(PanShape.ROUND, 8.0), Pan(PanShape.ROUND, 10.0),
            Pan(PanShape.SQUARE, 6.0), Pan(PanShape.SQUARE, 8.0), Pan(PanShape.RECT, 9.0, 13.0),
        )
    }
}

/** A recipe written for [pan]; [ingredients] is one ingredient per line, quantity first ("2 cups flour"). */
data class Recipe(val id: String, val name: String, val pan: Pan, val ingredients: String, val notes: String = "")

/** Scales a recipe to another pan by area (same pan depth). */
object RecipeScaler {
    fun factor(from: Pan, to: Pan): Double = if (from.area > 0) to.area / from.area else 1.0

    fun scale(ingredients: String, factor: Double): String = ingredients.lines().joinToString("\n") { scaleLine(it, factor) }

    private val vulgar = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1 / 3.0, '⅔' to 2 / 3.0, '⅛' to 0.125)

    /** Leading quantity: "1 1/2", "1/2", "1½", "½", "1.5", "250". */
    private val leading = Regex("""^(\s*)(\d+\s+\d+/\d+|\d+/\d+|\d*[¼½¾⅓⅔⅛]|\d+(?:\.\d+)?)""")

    fun scaleLine(line: String, factor: Double): String {
        val m = leading.find(line) ?: return line
        val value = parse(m.groupValues[2]) ?: return line
        return m.groupValues[1] + format(value * factor) + line.substring(m.range.last + 1)
    }

    fun parse(text: String): Double? {
        val t = text.trim()
        vulgar[t.lastOrNull()]?.let { return (t.dropLast(1).toDoubleOrNull() ?: 0.0) + it }
        val v = t.split(Regex("\\s+")).sumOf { p ->
            if ('/' in p) p.substringBefore('/').toDouble() / p.substringAfter('/').toDouble() else p.toDouble()
        }
        return v.takeIf { it.isFinite() }
    }

    /** Whole numbers from 10 up (grams, ml); quarters below (cups, spoons, eggs). */
    fun format(x: Double): String {
        if (x >= 10) return x.roundToLong().toString()
        val q = (x * 4).roundToLong() / 4.0
        if (q == 0.0) return Pricing.trim((x * 100).roundToLong() / 100.0)
        val whole = floor(q).toLong()
        val frac = when (q - whole) { 0.25 -> "¼"; 0.5 -> "½"; 0.75 -> "¾"; else -> "" }
        return (if (whole > 0) whole.toString() else "") + frac
    }
}

class RecipeStore(context: Context) {
    private val file = File(context.filesDir, "recipes.json")

    @Synchronized
    fun load(): List<Recipe> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val r = a.getJSONObject(i)
            val p = r.getJSONObject("pan")
            Recipe(
                id = r.getString("id"),
                name = r.getString("name"),
                pan = Pan(PanShape.valueOf(p.getString("shape")), p.getDouble("width"), p.optDouble("length", p.getDouble("width"))),
                ingredients = r.optString("ingredients"),
                notes = r.optString("notes"),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(recipes: List<Recipe>) {
        val a = JSONArray(recipes.map { r ->
            JSONObject().put("id", r.id).put("name", r.name).put("ingredients", r.ingredients).put("notes", r.notes)
                .put("pan", JSONObject().put("shape", r.pan.shape.name).put("width", r.pan.width).put("length", r.pan.length))
        })
        val tmp = File(file.parentFile, "recipes.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file)
    }
}
