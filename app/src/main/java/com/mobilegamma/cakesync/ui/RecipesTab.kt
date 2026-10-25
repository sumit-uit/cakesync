package com.mobilegamma.cakesync.ui

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recipe.name.isBlank()) "New recipe" else recipe.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Vanilla sponge)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Recipe is for", fontWeight = FontWeight.Bold)
                PanPicker(pan) { pan = it }
                OutlinedTextField(
                    ingredients, { ingredients = it },
                    label = { Text("Ingredients, one per line") },
                    placeholder = { Text("500 g cake mix\n270 g water\n25 g oil\n2 eggs") },
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
