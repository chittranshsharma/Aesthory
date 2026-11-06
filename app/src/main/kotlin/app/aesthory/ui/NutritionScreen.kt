package app.aesthory.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.nutrition.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun NutritionScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val entries by model.nutrition.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.importNutrition(uri) }
    val draft = state.nutritionDraft
    if (draft != null) { key(draft.token) { NutritionEditor(model, draft) }; return }
    BackHandler { if (!state.busy) onBack() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(enabled = !state.busy, onClick = onBack) { Text("Back") }
            Text("Bring your day together.", style = MaterialTheme.typography.displaySmall)
            Text("Add your daily totals from a calorie-app screenshot. Read on this phone, then checked by you.")
        }
        item { Button(enabled = !state.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Import a screenshot") } }
        item { TextButton(enabled = !state.busy, onClick = { model.manualNutrition() }) { Text("Enter totals myself") } }
        item { Text("One snapshot per day. New totals replace that day's entry after you confirm; they are never added together. Screenshots are not kept or uploaded.", style = MaterialTheme.typography.bodySmall) }
        if (entries.isEmpty()) item { Text("No nutrition saved yet. Start with a screenshot or enter what you know.") }
        else item { SectionHeading("Your saved days", "Showing your latest 366 entries. Older entries remain saved on this phone.") }
        items(entries, key = { it.day }) { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(nutritionDate(entry.day), style = MaterialTheme.typography.titleLarge)
                NutritionTotals(entry.calories, entry.protein, entry.carbs, entry.fat)
                TextButton(enabled = !state.busy, onClick = { model.manualNutrition(entry) }) { Text("Review this day") }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NutritionEditor(model: MainViewModel, draft: NutritionDraft) {
    val state by model.ui.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { model.editorSession(true); onDispose { model.editorSession(false) } }
    val now = model.timingNow()
    val today = Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate()
    var day by rememberSaveable { mutableStateOf(draft.existing?.day?.let { LocalDate.ofEpochDay(it).toString() } ?: today.toString()) }
    val initial = draft.scan?.values ?: draft.existing?.let { NutritionValues(it.calories, it.protein, it.carbs, it.fat) } ?: NutritionValues()
    var calories by rememberSaveable { mutableStateOf(initial.calories?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(initial.protein?.toString().orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(initial.carbs?.toString().orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(initial.fat?.toString().orEmpty()) }
    var verified by rememberSaveable { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val values = runCatching { NutritionValues(NutritionParser.entered(calories, 20000.0), NutritionParser.entered(protein, 5000.0), NutritionParser.entered(carbs, 5000.0), NutritionParser.entered(fat, 5000.0)) }.getOrNull()
    val date = runCatching { LocalDate.parse(day) }.getOrNull()
    val valid = values != null && listOf(values.calories, values.protein, values.carbs, values.fat).any { it != null } && date != null && date <= today
    fun changed() { verified = false; model.clearNutritionConflict() }
    BackHandler { if (!state.busy) discard = true }
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(enabled = !state.busy, onClick = { discard = true }) { Text("Discard draft") }
            Text("Check your daily totals.", style = MaterialTheme.typography.headlineLarge)
            Text("Use what you ate, not your goal or remaining allowance. Missing or unclear values stay blank. Blank means unknown, not zero.")
            draft.scan?.let { scan ->
                Image(scan.bitmap.asImageBitmap(), contentDescription = "Selected nutrition screenshot for comparison", modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
                Text("Suggested numbers may need correcting. English labels and clear single totals work best; complex layouts may need manual entry.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Hide detected text" else "Show detected text") }
                if (showText) Text(scan.text.ifBlank { "No readable text found. You can still enter the totals yourself." })
            }
            if (draft.existing == null) DateChoice("Nutrition date", day) { day = it; changed() }
            else Text("Nutrition date: " + nutritionDate(draft.existing.day))
            Text("The date starts as today unless you're editing a saved day. Match it to the screenshot.", style = MaterialTheme.typography.bodySmall)
            NutritionField("Calories (kcal)", calories, { calories = it; changed() })
            NutritionField("Protein (g)", protein, { protein = it; changed() })
            NutritionField("Carbohydrates (g)", carbs, { carbs = it; changed() })
            NutritionField("Fat (g)", fat, { fat = it; changed() })
            Text("Use up to two decimal places, without thousands separators. Calories: 0–20,000; each macro: 0–5,000 g. These are entry limits, not recommended intake.", style = MaterialTheme.typography.bodySmall)
            if (!valid) Text("Enter at least one valid total and choose today or an earlier date.", color = MaterialTheme.colorScheme.error)
            SelectRow("I checked the date and these are my consumed daily totals", verified) { verified = !verified }
        }
        Button(enabled = valid && verified && !state.busy, onClick = { model.saveNutrition(date!!, values!!) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Text("Save daily totals") }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this review?") }, text = { Text("Your saved nutrition will stay as it is.") },
        confirmButton = { TextButton(onClick = model::discardNutrition) { Text("Discard draft") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep reviewing") } })
    state.nutritionConflict?.let { existing ->
        AlertDialog(onDismissRequest = model::clearNutritionConflict, title = { Text("Replace this day's totals?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(nutritionDate(existing.day))
                Text("Already saved:")
                NutritionTotals(existing.calories, existing.protein, existing.carbs, existing.fat)
                Text("Your reviewed totals will replace all four fields, including blanks. They won't be added to these numbers.")
            }
        }, confirmButton = { TextButton(enabled = valid && verified && !state.busy, onClick = { model.saveNutrition(date!!, values!!, existing.revision) }) { Text("Replace totals") } },
            dismissButton = { TextButton(onClick = model::clearNutritionConflict) { Text("Keep reviewing") } })
    }
}

@Composable
private fun NutritionField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, { change(it.take(16)) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun NutritionTotals(calories: Double?, protein: Double?, carbs: Double?, fat: Double?) {
    fun amount(value: Double?, unit: String) = value?.let { "$it $unit" } ?: "Not recorded"
    Text("Calories: " + amount(calories, "kcal"))
    Text("Protein: " + amount(protein, "g") + " · Carbs: " + amount(carbs, "g") + " · Fat: " + amount(fat, "g"))
}
private fun nutritionDate(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
