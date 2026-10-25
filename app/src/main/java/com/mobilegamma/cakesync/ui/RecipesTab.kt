package com.mobilegamma.cakesync.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobilegamma.cakesync.menu.Pan
import com.mobilegamma.cakesync.menu.PanShape
import com.mobilegamma.cakesync.menu.Pricing
import com.mobilegamma.cakesync.menu.Recipe
import com.mobilegamma.cakesync.menu.RecipeScaler
import com.mobilegamma.cakesync.menu.RecipeStore
import com.mobilegamma.cakesync.menu.Recipes
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Saved recipes; open one and pick another pan to see the amounts for that size. */
@Composable
fun RecipesTab() {
    val context = LocalContext.current
    val store = remember { RecipeStore(context) }
    var recipes by remember { mutableStateOf(store.load()) }
    var open by remember { mutableStateOf<Recipe?>(null) }
    fun persist(list: List<Recipe>) { recipes = list; store.save(list) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { open = Recipe(UUID.randomUUID().toString(), "", Pan(PanShape.ROUND, 8.0), "") }) { Text("+ New recipe") }
        if (recipes.isEmpty()) Text(
            "Add your recipe once for the pan you usually bake (e.g. 8\" round), then pick any other pan to get the new amounts.",
            style = MaterialTheme.typography.bodyMedium,
        )
        recipes.forEach { r ->
            Card(Modifier.fillMaxWidth().clickable { open = r }) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                    Text("${r.pan.label} · ${r.ingredients.lines().count { it.isNotBlank() }} ingredients", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    open?.let { r ->
        RecipeDialog(
            recipe = r,
            onDismiss = { open = null },
            onSave = { saved -> persist(recipes.filter { it.id != saved.id } + saved); open = null },
            onDelete = { persist(recipes.filter { it.id != r.id }); open = null },
        )
    }
}

@Composable
private fun RecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onSave: (Recipe) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(recipe.name) }
    var pan by remember { mutableStateOf(recipe.pan) }
    var ingredients by remember { mutableStateOf(recipe.ingredients) }
    var notes by remember { mutableStateOf(recipe.notes) }
    var target by remember { mutableStateOf(recipe.pan) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    /** Fills the form from recipe text (pasted or read from a photo); returns a short note. */
    fun fill(text: String): String {
        val p = Recipes.read(text)
        p.name?.let { if (name.isBlank()) name = it }
        p.pan?.let { pan = it; target = it }
        ingredients = listOf(ingredients.trimEnd(), p.ingredients).filter { it.isNotBlank() }.joinToString("\n")
        p.notes?.let { notes = listOf(notes.trim(), it).filter { n -> n.isNotBlank() }.joinToString("\n") }
        return "Added ${p.ingredients.lines().count { it.isNotBlank() }} ingredients" + (p.pan?.let { " for ${it.label}" } ?: "") + "."
    }
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    // Reads a photo of a written recipe on the phone (no internet); handwriting may need fixing up.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scanNote = "Reading the photo…"
        scope.launch {
            val text = runCatching {
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { it.process(InputImage.fromFilePath(context, uri)).await().text }
            }.getOrNull()
            if (text.isNullOrBlank()) { scanNote = "Couldn't read any text in that photo."; return@launch }
            scanNote = fill(text) + " Check them, handwriting can be misread."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recipe.name.isBlank()) "New recipe" else recipe.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Vanilla sponge)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("📷 Photo") }
                    OutlinedButton(onClick = {
                        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        scanNote = if (text.isNullOrBlank()) "Copy a recipe first, then tap Paste." else fill(text)
                    }) { Text("📋 Paste") }
                }
                TextButton(onClick = {
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Recipe prompt", Recipes.AI_PROMPT))
                    scanNote = "Prompt copied. Paste it into any AI chat with your recipe, copy its answer, then tap Paste."
                }) { Text("✨ Copy AI prompt to convert a recipe") }
                scanNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Recipe is for", fontWeight = FontWeight.Bold)
                PanPicker(pan) { pan = it }
                OutlinedTextField(
                    ingredients, { ingredients = it },
                    label = { Text("Ingredients, one per line") },
                    placeholder = { Text(Recipes.FORMAT.lines().drop(2).dropLast(1).joinToString("\n")) },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (bake time, temp…)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Make it for", fontWeight = FontWeight.Bold)
                PanPicker(target) { target = it }
                val factor = RecipeScaler.factor(pan, target)
                Text("${target.label}: ×${Pricing.trim((factor * 100).toLong() / 100.0)} of the ${pan.label} recipe (same pan depth)", style = MaterialTheme.typography.bodySmall)
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(RecipeScaler.scale(ingredients, factor).ifBlank { "—" }, modifier = Modifier.padding(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(recipe.copy(name = name.trim(), pan = pan, ingredients = ingredients.trimEnd(), notes = notes.trim())) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (recipe.name.isNotBlank()) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A saved recipe sized for one order tier. */
@Composable
fun ScaledRecipeDialog(recipe: Recipe, pan: Pan, onDismiss: () -> Unit) {
    val factor = RecipeScaler.factor(recipe.pan, pan)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${recipe.name} · ${pan.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("×${Pricing.trim((factor * 100).toLong() / 100.0)} of the ${recipe.pan.label} recipe", style = MaterialTheme.typography.bodySmall)
                Text(RecipeScaler.scale(recipe.ingredients, factor))
                if (recipe.notes.isNotBlank()) Text(recipe.notes, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Quick picks for common pans, plus shape and size in inches for anything else. */
@Composable
private fun PanPicker(pan: Pan, onChange: (Pan) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pan.presets.forEach { p -> FilterChip(selected = p == pan, onClick = { onChange(p) }, label = { Text(p.label) }, shape = CircleShape) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PanShape.entries.forEach { s ->
            FilterChip(selected = pan.shape == s, onClick = { onChange(pan.copy(shape = s)) }, label = { Text(s.label) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InchField(if (pan.shape == PanShape.ROUND) "Across (in)" else "Width (in)", pan.width, Modifier.weight(1f)) { onChange(pan.copy(width = it)) }
        if (pan.shape == PanShape.RECT) InchField("Length (in)", pan.length, Modifier.weight(1f)) { onChange(pan.copy(length = it)) }
    }
}

@Composable
private fun InchField(label: String, value: Double, modifier: Modifier, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(Pricing.trim(value)) }
    OutlinedTextField(
        text, { t -> text = t; t.toDoubleOrNull()?.takeIf { it > 0 }?.let(onChange) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
