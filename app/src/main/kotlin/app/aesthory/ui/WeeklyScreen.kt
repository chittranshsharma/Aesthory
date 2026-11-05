package app.aesthory.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.progress.*
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun WeeklyScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val now = model.timingNow()
    val today = Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate()
    val current = weekStart(today)
    var selected by rememberSaveable { mutableLongStateOf(current.toEpochDay()) }
    val start = LocalDate.ofEpochDay(selected)
    val targetFlow = remember(selected) { model.weeklyTarget(selected) }
    val logsFlow = remember(selected) { model.weeklyLogs(selected) }
    val target by targetFlow.collectAsStateWithLifecycle(null)
    val logs by logsFlow.collectAsStateWithLifecycle(null)
    var editing by rememberSaveable { mutableStateOf(false) }
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Page {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Your week, your pace.", style = MaterialTheme.typography.displaySmall)
        Text(start.format(format) + " – " + start.plusDays(6).format(format))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { selected -= 7 }) { Text("Previous") }
            OutlinedButton(enabled = start < current, onClick = { selected += 7 }) { Text("Next") }
        }
        if (start != current) TextButton(onClick = { selected = current.toEpochDay() }) { Text("This week") }
        if (logs == null) CircularProgressIndicator()
        else {
            val review = reviewWeek(logs!!, start, today)
            Text("${review.completed} actions completed", style = MaterialTheme.typography.headlineMedium)
            Text("${review.partial} partly done · ${review.skipped} skipped · ${review.days} days with activity")
            Text(review.recordedSeconds?.let { "Recorded time: ${it.toLong() / 60} min ${it.toLong() % 60} sec" } ?: "No time recorded for this week.")
            Text("Time includes saved timers and manual time entries. Actions without time are not estimates of zero.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            SectionHeading("Weekly targets")
            target?.completed?.let { Text("Completed actions: ${review.completed} of $it") }
            target?.minutes?.let { Text("Recorded minutes: ${review.recordedSeconds?.let { seconds -> (seconds / 60).toLong().toString() } ?: "none yet"} of $it") }
            if (target?.completed == null && target?.minutes == null) Text("No targets set. Your activity still counts.")
            if (start == current) Button(enabled = !state.busy, onClick = { editing = true }) { Text("Set this week's targets") }
            Text("Targets apply only to this Monday–Sunday week. Pausing a routine does not erase its saved activity. Missing check-ins are not marked as failures.", style = MaterialTheme.typography.bodySmall)
            Text("Each action counts once per day, using your latest check-in. Partial results contribute recorded time, but not completed actions.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editing) {
        var completions by rememberSaveable { mutableStateOf(target?.completed?.toString().orEmpty()) }
        var minutes by rememberSaveable { mutableStateOf(target?.minutes?.toString().orEmpty()) }
        val valid = (completions.isBlank() || completions.toIntOrNull() in 1..10000) && (minutes.isBlank() || minutes.toIntOrNull() in 1..10080)
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Make this week yours") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose targets that fit your week. Leave either blank to remove that target.")
                OutlinedTextField(completions, { completions = it }, label = { Text("Completed actions (1–10,000)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Minutes (1–10,080)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (!valid) Text("Enter a whole number within the shown range.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = valid && !state.busy, onClick = {
            model.saveWeeklyTarget(WeeklyTarget(selected, completions.toIntOrNull(), minutes.toIntOrNull())) { editing = false }
        }) { Text("Save targets") } }, dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } })
    }
}
