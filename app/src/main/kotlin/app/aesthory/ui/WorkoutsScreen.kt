package app.aesthory.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.workouts.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun WorkoutsScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val history by model.workouts.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importWorkouts) }
    var selected by remember { mutableStateOf<WorkoutBundle?>(null) }
    var exercise by rememberSaveable { mutableStateOf<String?>(null) }
    val draft = state.workoutDraft
    DisposableEffect(draft != null) { if (draft != null) model.editorSession(true); onDispose { if (draft != null) model.editorSession(false) } }
    if (selected != null) {
        WorkoutDetails(selected!!, onBack = { selected = null }, onExercise = if (draft == null) ({ exercise = it; selected = null }) else null)
        return
    }
    if (exercise != null) {
        BackHandler { exercise = null }
        WorkoutList {
            item { TextButton(onClick = { exercise = null }) { Text("Back to workouts") }; Text(exercise!!, style = MaterialTheme.typography.headlineLarge); Text("Sets from your latest 1,000 saved workouts. Units stay as recorded.") }
            history.filter { bundle -> bundle.sets.any { it.exercise == exercise } }.forEach { bundle ->
                item(key = bundle.workout.id) { Text(workoutDate(bundle.workout), style = MaterialTheme.typography.titleLarge) }
                items(bundle.sets.filter { it.exercise == exercise }.sortedBy { it.position }, key = { "${it.workoutId}:${it.position}" }) { SetRow(it, displaySetNumber(it, bundle)) }
            }
        }
        return
    }
    if (draft != null) {
        key(draft.token) { WorkoutReview(model, draft, onOpen = { selected = it }) }
        return
    }
    BackHandler { if (!state.busy) onBack() }
    WorkoutList {
        item {
            TextButton(enabled = !state.busy, onClick = onBack) { Text("Back") }
            Text("Every set has a story.", style = MaterialTheme.typography.displaySmall)
            Text("Bring your Hevy workouts here and see your exercise history, all on your phone.")
        }
        item { Button(enabled = !state.busy, onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream", "application/vnd.ms-excel")) }) { Text("Import Hevy workouts") } }
        item { Text("In Hevy, open Settings → Export & Import Data → Export Data → Export Workouts. Choose that CSV file here. You'll review it before anything is saved.", style = MaterialTheme.typography.bodySmall) }
        if (history.isEmpty()) item { Text("Your first workout is waiting. Import a file to bring your sets, notes and training history together.") }
        else item { SectionHeading("Your training log", "Showing your latest 1,000 workouts. Older workouts remain saved on this phone.") }
        items(history, key = { it.workout.id }) { bundle -> WorkoutHeading(bundle); TextButton(onClick = { selected = bundle }) { Text("See exercises & sets") }; HorizontalDivider() }
    }
}

@Composable
private fun WorkoutReview(model: MainViewModel, draft: WorkoutDraft, onOpen: (WorkoutBundle) -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var zone by rememberSaveable { mutableStateOf(draft.zone) }
    var editZone by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (!state.busy) discard = true }
    Column(Modifier.fillMaxSize().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutList(Modifier.weight(1f)) {
            item {
                TextButton(enabled = !state.busy, onClick = { discard = true }) { Text("Cancel import") }
                Text("Ready for your training log?", style = MaterialTheme.typography.headlineLarge)
                Text("${draft.preview.added} new · ${draft.preview.changed} changed · ${draft.preview.skipped} already saved", style = MaterialTheme.typography.titleMedium)
                Text("Times use ${draft.zone}. Choose the time zone where these workouts happened. Split exports from different time zones before importing.")
                TextButton(enabled = !state.busy, onClick = { editZone = !editZone; confirmed = false }) { Text("Change time zone") }
                if (editZone) {
                    OutlinedTextField(value = zone, onValueChange = { zone = it; confirmed = false }, label = { Text("Time zone, e.g. Asia/Kolkata") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !state.busy && zone.isNotBlank(), onClick = { model.workoutTimezone(zone) }) { Text("Apply time zone") }
                }
                if (draft.preview.changed > 0) Text("Changed workouts replace the entire saved workout, including its sets and notes. Compare both versions below. Nothing is added to the old totals.", color = MaterialTheme.colorScheme.error)
                Text("A matching start time identifies a workout. If you changed its start time in Hevy, cancel and check your history first to avoid a second entry.", style = MaterialTheme.typography.bodySmall)
            }
            items(draft.preview.items, key = { it.incoming.workout.id }) { item ->
                Text(when { item.unchanged -> "Already saved · will skip"; item.changed -> "Changed · will replace"; else -> "New workout" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                WorkoutHeading(item.incoming)
                TextButton(onClick = { onOpen(item.incoming) }) { Text("Review imported sets") }
                if (item.changed) TextButton(onClick = { onOpen(item.existing!!) }) { Text("Compare saved version") }
                HorizontalDivider()
            }
            if (draft.preview.added + draft.preview.changed > 0) item {
                SelectRow(if (draft.preview.changed > 0) "I checked the dates, time zone and units. Replace the changed workouts." else "I checked the dates, time zone and units.", confirmed) {
                    if (!state.busy && zone == draft.zone) confirmed = !confirmed
                }
            }
        }
        val noChanges = draft.preview.added + draft.preview.changed == 0
        Button(enabled = !state.busy && (noChanges || confirmed && zone == draft.zone), onClick = { if (noChanges) model.discardWorkouts() else model.saveWorkouts(draft.preview.changed > 0) }, modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) { Text(if (noChanges) "Done" else "Save workouts") }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Leave this import?") }, text = { Text("Nothing from this file has been saved. You can select it again anytime.") },
        confirmButton = { TextButton(onClick = { model.discardWorkouts() }) { Text("Leave import") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep reviewing") } })
}

@Composable
private fun WorkoutDetails(bundle: WorkoutBundle, onBack: () -> Unit, onExercise: ((String) -> Unit)?) {
    BackHandler(onBack = onBack)
    WorkoutList {
        item { TextButton(onClick = onBack) { Text("Back") }; WorkoutHeading(bundle); Text("Times: ${bundle.workout.zone}. Units are from your export."); if (bundle.workout.description.isNotBlank()) Text(bundle.workout.description) }
        items(bundle.sets.sortedBy { it.position }, key = { it.position }) { set ->
            Text(set.exercise, style = MaterialTheme.typography.titleMedium)
            SetRow(set, displaySetNumber(set, bundle))
            if (onExercise != null) TextButton(onClick = { onExercise(set.exercise) }) { Text("See exercise history") }
            HorizontalDivider()
        }
    }
}

@Composable private fun WorkoutHeading(bundle: WorkoutBundle) {
    Text(bundle.workout.title, style = MaterialTheme.typography.headlineSmall)
    Text(workoutDate(bundle.workout))
    val exercises = bundle.sets.map { it.exercise }.distinct().size
    Text("${bundle.sets.size} ${if (bundle.sets.size == 1) "set" else "sets"} · $exercises ${if (exercises == 1) "exercise" else "exercises"} · ${(bundle.workout.endMillis - bundle.workout.startMillis) / 60000} min")
}

@Composable private fun SetRow(set: WorkoutSet, number: Int) {
    Text("Set $number · ${set.type}")
    Text(listOfNotNull(set.weight?.let { "${workoutNumber(it)} ${set.weightUnit}" }, set.reps?.let { "$it reps" }, set.distance?.let { "${workoutNumber(it)} ${set.distanceUnit}" }, set.seconds?.let { "${workoutNumber(it)} sec" }, set.rpe?.let { "Effort ${workoutNumber(it)}/10" }).joinToString(" · ").ifBlank { "No measurements recorded" })
    if (set.superset.isNotBlank()) Text("Superset ${set.superset}", style = MaterialTheme.typography.bodySmall)
    if (set.notes.isNotBlank()) Text(set.notes, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun WorkoutList(modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
private fun workoutDate(workout: Workout): String = LocalDateTime.parse(workout.startLocal).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
private fun workoutNumber(number: Double) = if (number % 1 == 0.0) number.toLong().toString() else number.toString()

private fun displaySetNumber(set: WorkoutSet, bundle: WorkoutBundle): Int = bundle.sets.filter { it.exercise == set.exercise }.sortedBy { it.position }.indexOf(set) + 1
