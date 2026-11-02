package app.aesthory.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.data.CurrentHabit
import app.aesthory.library.HabitLinks
import app.aesthory.routines.TrackingKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun HabitEditorScreen(model: MainViewModel, routineId: String, existing: CurrentHabit?, onBack: () -> Unit) {
    val definition = existing?.definition
    val links by remember(definition?.habitId, definition?.version) {
        if (definition == null) flowOf(HabitLinks(emptyList(), emptyList())) else model.habitLinks(definition.habitId, definition.version)
    }.collectAsStateWithLifecycle(null)
    if (links == null) { Page { Text("Opening your action…"); CircularProgressIndicator() }; return }
    key(definition?.habitId, definition?.version) { HabitEditorForm(model, routineId, existing, links!!, onBack) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorForm(model: MainViewModel, routineId: String, existing: CurrentHabit?, links: HabitLinks, onBack: () -> Unit) {
    EditorFocus(model)
    val state by model.ui.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val products by model.products.collectAsStateWithLifecycle()
    val references by model.references.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(HabitEditorInput.from(existing?.definition, links)) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var library by rememberSaveable { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val original = remember { HabitEditorInput.from(existing?.definition, links) }
    val dirty = input != original && !(existing == null && input == original.copy(categoryId = input.categoryId))
    fun back() { if (dirty) leaving = true else onBack() }
    BackHandler { back() }
    LaunchedEffect(categories.size) {
        if (categories.isNotEmpty() && input.categoryId.isEmpty()) input = input.copy(categoryId = categories.first().id)
    }
    LaunchedEffect(Unit) { if (categories.isEmpty()) model.addCategory("General") { input = input.copy(categoryId = it) } }
    if (library) {
        BackHandler { library = false }
        LibraryScreen(model) { library = false }
        return
    }
    val errors = input.errors()
    Column(Modifier.fillMaxSize().imePadding()) {
    Box(Modifier.weight(1f)) { Page {
        TextButton(onClick = { back() }) { Text("Back to routines") }
        Text(if (existing == null) "Your next good habit." else "Make it work for you.", style = MaterialTheme.typography.displaySmall)
        Text("Give it a name and save. Open the extra details whenever you need them.")
        OutlinedTextField(input.title, { input = input.copy(title = it.take(120)) }, label = { Text("What will you do?") }, modifier = Modifier.fillMaxWidth(),
            isError = touched && "title" in errors, supportingText = { if (touched) errors["title"]?.let { Text(it) } })
        EditorSection("Instructions", "Steps, your reason and a shorter version", initiallyOpen = existing?.definition?.instructions?.isNotBlank() == true) {
            OutlinedTextField(input.instructions, { input = input.copy(instructions = it.take(8000)) }, label = { Text("How you'll do it") }, minLines = 3, maxLines = 8, supportingText = { Text("Write your steps in the order you'll follow them.") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(input.purpose, { input = input.copy(purpose = it.take(1000)) }, label = { Text("Why it matters to you (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(input.shortInstructions, { input = input.copy(shortInstructions = it.take(2000)) }, label = { Text("A shorter version for busy days") }, modifier = Modifier.fillMaxWidth())
        }
        EditorSection("Schedule", scheduleSummary(input), initiallyOpen = false) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("daily" to "Every day", "weekdays" to "Pick days", "interval" to "Every few days").forEach { (value, label) ->
                    FilterChip(input.frequency == value, { input = input.copy(frequency = value) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (input.frequency == "weekdays") {
                val locale = LocalConfiguration.current.locales[0]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day -> FilterChip(day.value in input.days, { input = input.copy(days = if (day.value in input.days) input.days - day.value else input.days + day.value) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) }, modifier = Modifier.heightIn(min = 48.dp)) }
                }
            }
            if (input.frequency == "interval") NumberField(input.interval, { input = input.copy(interval = it.take(4)) }, "Repeat every how many days?")
            DateChoice("Start", input.start) { input = input.copy(start = it) }
            DateChoice("End (optional)", input.end, optional = true) { input = input.copy(end = it) }
            errors["schedule"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        EditorSection("How to track it", trackingLabel(input.kind)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackingKind.entries.forEach { kind -> FilterChip(input.kind == kind, { input = input.copy(kind = kind, target = if (kind == TrackingKind.RATING) "5" else "", unit = "", checklist = "") },
                    label = { Text(trackingLabel(kind)) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            when (input.kind) {
                TrackingKind.CHECKBOX -> Text("One tap to check it off. Great for most daily habits.")
                TrackingKind.CHECKLIST -> OutlinedTextField(input.checklist, { input = input.copy(checklist = it.take(12100)) }, label = { Text("Your steps, one per line") }, modifier = Modifier.fillMaxWidth())
                else -> NumberField(input.target, { input = input.copy(target = it.take(24)) }, when (input.kind) {
                    TrackingKind.DURATION -> "Goal in minutes"
                    TrackingKind.RATING -> "Top of your rating scale (2–100)"
                    TrackingKind.VALUE -> "Goal (optional)"
                    else -> "Goal count"
                })
            }
            if (input.kind == TrackingKind.VALUE) OutlinedTextField(input.unit, { input = input.copy(unit = it.take(40)) }, label = { Text("Unit, such as cm or kg") }, modifier = Modifier.fillMaxWidth())
            if (input.kind == TrackingKind.DURATION) Text("Start a running timer from your action, or log time manually. The timer keeps going when you close the app.")
            errors["tracking"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        EditorSection("Time & category", input.minutes.takeIf { it.isNotBlank() }?.let { "About " + it + " minutes" } ?: "Optional time estimate and a place to keep it") {
            NumberField(input.minutes, { input = input.copy(minutes = it.take(4)) }, "About how many minutes? (optional)")
            errors["minutes"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            var choosing by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { choosing = true }) { Text("Category: " + (categories.find { it.id == input.categoryId }?.name ?: "Choose one")) }
                DropdownMenu(choosing, { choosing = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                    categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { input = input.copy(categoryId = category.id); choosing = false }) }
                }
            }
            TextButton(onClick = { categoryDialog = true }) { Text("New category") }
            OutlinedTextField(input.tags, { input = input.copy(tags = it.take(420)) }, label = { Text("Tags (optional)") }, supportingText = { Text("Separate with commas, such as morning, travel.") }, modifier = Modifier.fillMaxWidth())
            errors["tags"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        EditorSection("Products & references", (input.productIds.size + input.referenceIds.size).toString() + " linked") {
            Text("Choose from your library. Saved instructions keep a copy of the details used at the time.")
            TextButton(onClick = { library = true }) { Text("Manage products & references") }
            products.filter { !it.archived || it.id in input.productIds }.forEach { product ->
                SelectRow(product.name + if (product.archived) " (archived)" else "", product.id in input.productIds) {
                    if (product.id in input.productIds) input = input.copy(productIds = input.productIds - product.id)
                    else if (input.productIds.size < 20) input = input.copy(productIds = input.productIds + product.id)
                }
                if (product.id in input.productIds) OutlinedTextField(input.usageNotes[product.id].orEmpty(), { input = input.copy(usageNotes = input.usageNotes + (product.id to it.take(2000))) },
                    label = { Text("Your use notes for " + product.name) }, modifier = Modifier.fillMaxWidth())
            }
            references.filter { !it.archived || it.id in input.referenceIds }.forEach { reference ->
                SelectRow(reference.title + if (reference.archived) " (archived)" else "", reference.id in input.referenceIds) {
                    if (reference.id in input.referenceIds) input = input.copy(referenceIds = input.referenceIds - reference.id)
                    else if (input.referenceIds.size < 20) input = input.copy(referenceIds = input.referenceIds + reference.id)
                }
            }
            if (products.none { !it.archived } && references.none { !it.archived }) Text("Add a product, note or link in your library first. Nothing is sent to AI.")
            Text("Up to 20 products and 20 references per action.", style = MaterialTheme.typography.bodySmall)
        }
        if (existing != null) Text("Your past check-ins and their instructions stay saved. Your next check-ins will use these changes.", style = MaterialTheme.typography.bodySmall)
    } }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (touched) errors.values.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !state.busy, onClick = {
                touched = true
                if (errors.isEmpty()) model.saveHabitDetails(routineId, existing?.definition?.habitId, input.toDraft(), onBack)
            }, modifier = Modifier.fillMaxWidth()) { Text(if (existing == null) "Add action" else "Save changes") }
        }
    }
    }
    if (categoryDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { categoryDialog = false }, title = { Text("New category") }, text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text("Category name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !state.busy, onClick = { model.addCategory(name) { input = input.copy(categoryId = it); categoryDialog = false } }) { Text("Add category") } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text("Cancel") } })
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Leave without saving?") }, text = { Text("Your changes to this action haven't been saved.") },
        confirmButton = { TextButton(onClick = onBack) { Text("Discard changes") } }, dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } })
}

@Composable
internal fun EditorSection(title: String, summary: String, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    HorizontalDivider()
    TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(summary, style = MaterialTheme.typography.bodySmall) }
        Text(if (open) "Less" else "Open", modifier = Modifier.padding(start = 12.dp))
    }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun EditorFocus(model: MainViewModel) {
    DisposableEffect(model) { model.editorSession(true); onDispose { model.editorSession(false) } }
}

@Composable
internal fun SelectRow(label: String, selected: Boolean, change: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(selected, role = Role.Checkbox, onValueChange = { change() }),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(selected, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}

@Composable
internal fun DateChoice(label: String, value: String, optional: Boolean = false, change: (String) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val date = value.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    TextButton(onClick = {
        val initial = date ?: LocalDate.now()
        DatePickerDialog(context, { _, year, month, day -> change(LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }) { Text(label + ": " + (date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) ?: "Not set")) }
    if (optional && date != null) TextButton(onClick = { change("") }) { Text("Clear " + label.lowercase()) }
}

private fun scheduleSummary(input: HabitEditorInput) = when (input.frequency) { "weekdays" -> "Days you choose"; "interval" -> "Every " + input.interval + " days"; else -> "Every day" }
internal fun trackingLabel(kind: TrackingKind) = when (kind) {
    TrackingKind.CHECKBOX -> "Check it off"; TrackingKind.COUNT -> "Count"; TrackingKind.DURATION -> "Time spent"
    TrackingKind.VALUE -> "A measurement"; TrackingKind.RATING -> "A rating"; TrackingKind.CHECKLIST -> "A checklist"
}
