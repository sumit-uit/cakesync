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
        private val rect = Regex("""(\d+(?:\.\d+)?)\s*[x×X*]\s*(\d+(?:\.\d+)?)""")
        private val inches = Regex("""(\d+(?:\.\d+)?)\s*(?:"|”|''|in\b|inch)""", RegexOption.IGNORE_CASE)

        /** Reads a size like `8"`, "8 inch", `6" square` or "9x13"; null when there's no size in inches. */
        fun parse(text: String): Pan? {
            rect.find(text)?.let { m ->
                val (w, l) = m.destructured
                return if (w == l) Pan(PanShape.SQUARE, w.toDouble()) else Pan(PanShape.RECT, w.toDouble(), l.toDouble())
            }
            val w = inches.find(text)?.groupValues?.get(1)?.toDouble() ?: return null
            return Pan(if (text.contains("sq", true)) PanShape.SQUARE else PanShape.ROUND, w)
        }

        val presets = listOf(
            Pan(PanShape.ROUND, 6.0), Pan(PanShape.ROUND, 8.0), Pan(PanShape.ROUND, 10.0),
            Pan(PanShape.SQUARE, 6.0), Pan(PanShape.SQUARE, 8.0), Pan(PanShape.RECT, 9.0, 13.0),
        )
    }
}

/** A recipe written for [pan]; [ingredients] is one ingredient per line, quantity first ("2 cups flour"). */
data class Recipe(val id: String, val name: String, val pan: Pan, val ingredients: String, val notes: String = "")

object Recipes {
    /** The recipe for a flavour: names match either way round ("Vanilla" ↔ "Vanilla sponge"). */
    fun match(recipes: List<Recipe>, flavour: String): Recipe? {
        val f = flavour.trim()
        if (f.isEmpty()) return null
        return recipes.firstOrNull { it.name.equals(f, true) }
            ?: recipes.firstOrNull { it.name.isNotBlank() && (it.name.contains(f, true) || f.contains(it.name, true)) }
    }

    /** What [read] understands; also what the "Copy AI prompt" asks another AI to write. */
    const val FORMAT = """Recipe: Vanilla sponge
Pan: 8" round
500 g cake mix
270 g water
25 g oil
2 eggs
Notes: Bake at 175°C for 35 min"""

    const val AI_PROMPT = "Rewrite my cake recipe in exactly this format, plain text, no extra words. " +
        "Pan is the pan the recipe is for, like 8\" round, 6\" square or 9x13. " +
        "One ingredient per line with the number first (500 g flour, 1 1/2 cups milk, 2 eggs). " +
        "Put method, temperature and bake time after Notes:\n\n$FORMAT\n\nMy recipe:\n"

    data class Parsed(val name: String?, val pan: Pan?, val ingredients: String, val notes: String?)

    /**
     * Reads pasted text in [FORMAT] or text from a recipe photo. "Recipe:", "Pan:" and "Notes:" lines are optional;
     * without "Pan:", a short line like "8 inch" is taken as the pan. Bullets are dropped.
     */
    fun read(text: String): Parsed {
        val lines = text.lines().map { it.trim().removePrefix("•").removePrefix("* ").removePrefix("- ").trim() }.filter { it.isNotEmpty() }
        fun field(vararg keys: String) = lines.firstOrNull { l -> keys.any { l.startsWith("$it:", true) } }
        val nameLine = field("Recipe", "Name")
        val panLine = field("Pan", "Size") ?: lines.firstOrNull { it.length <= 20 && Pan.parse(it) != null }
        val notesAt = lines.indexOfFirst { it.startsWith("Notes:", true) }
        val body = if (notesAt >= 0) lines.take(notesAt) else lines
        fun value(l: String) = l.substringAfter(':', l).trim()
        return Parsed(
            name = nameLine?.let(::value)?.ifBlank { null },
            pan = panLine?.let { Pan.parse(it) },
            ingredients = body.filter { it !== nameLine && it !== panLine }.joinToString("\n"),
            notes = if (notesAt >= 0) (listOf(value(lines[notesAt])) + lines.drop(notesAt + 1)).filter { it.isNotBlank() }.joinToString("\n") else null,
        )
    }
}

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
