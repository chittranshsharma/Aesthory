package app.aesthory.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.aesthory.data.HabitVersionEntity
import app.aesthory.routines.*
import app.aesthory.timing.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
private fun timingMoment(model: MainViewModel, running: Boolean): TimingMoment {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val moment by produceState(model.timingNow(), running, lifecycle) {
        value = model.timingNow()
        if (running) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { value = model.timingNow(); delay(1000) } }
    }
    return moment
}

@Composable
internal fun RefreshCalendarDate(model: MainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { model.refreshCalendarDate(); delay(30000) }
    } }
}

@Composable
internal fun TimerBar(model: MainViewModel, onOpen: () -> Unit) {
    val timer by model.activeTimer.collectAsStateWithLifecycle()
    val current = timer ?: return
    val now = timingMoment(model, current.running)
    val reached = current.goalMillis?.let { TimerMath.elapsed(current, now) >= it } == true
    LaunchedEffect(current.sessionId, current.goalAlerted, reached) { if (reached && !current.goalAlerted) model.checkTimerGoal(current.sessionId) }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((if (current.running) "Timer · " else "Paused · ") + formatTimer(TimerMath.elapsed(current, now)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text("Open timer") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerScreen(model: MainViewModel, onBack: () -> Unit) {
    val timer by model.activeTimer.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    val current = timer
    val definition by produceState<HabitVersionEntity?>(null, current?.sessionId) { value = current?.let { model.timerDefinition(it) } }
    val now = timingMoment(model, current?.running == true)
    val elapsed = current?.let { TimerMath.elapsed(it, now) } ?: 0
    val reached = current?.goalMillis?.let { elapsed >= it } == true
    LaunchedEffect(current?.sessionId, current?.goalAlerted, reached) {
        if (current != null && reached && !current.goalAlerted) model.checkTimerGoal(current.sessionId)
    }
    Page {
        TextButton(onClick = onBack) { Text("Back to Today") }
        Text("One thing at a time.", style = MaterialTheme.typography.displaySmall)
        if (current == null) Text("No timer is running. Open an action on Today or in Routines, then choose Start timer.")
        else {
            Text(definition?.title ?: "Your action", style = MaterialTheme.typography.headlineMedium)
            Text(formatTimer(elapsed), style = MaterialTheme.typography.displayLarge)
            Text(if (current.running) "Running—even when you close the app." else "Paused. Your time is saved.")
            current.goalMillis?.let { goal ->
                LinearProgressIndicator(progress = { (elapsed.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(if (reached) "Goal reached. Keep going if you want; nothing is logged until you save." else "Goal: " + formatTimer(goal))
            }
            if (elapsed == MAX_TIMER_MILLIS) Text("This timer has reached its seven-day limit. Review and save it.")
            if (current.recovery == TimerRecovery.PHONE_RESTART) Text("Your phone restarted. The timer is paused and the shown time is an estimate. Check it before saving.")
            if (current.recovery == TimerRecovery.BACKUP) Text("This timer came from a backup. It's paused so you can check the saved time.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = !state.busy && (current.running || elapsed < MAX_TIMER_MILLIS), onClick = { if (current.running) model.pauseTimer(current.sessionId) else model.resumeTimer(current.sessionId) }) { Text(if (current.running) "Pause" else "Resume") }
                Button(enabled = !state.busy && definition != null, onClick = { model.reviewTimer(current.sessionId) }) { Text("Finish & review") }
            }
            TextButton(enabled = !state.busy, onClick = { discard = true }) { Text("Discard this timer") }
            if (!state.timingStatus.timers) Text("The timer works here. Allow notifications in Reminders & timers to see it outside the app and get goal alerts.")
            else if (!state.timingStatus.precise) Text("Goal alerts may be late when the app is closed. Precise alerts are optional in Reminders & timers.")
            Text("Started for " + LocalDate.ofEpochDay(current.day) + ". You can change the check-in date and recorded duration before saving.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (discard && current != null) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard this timer?") }, text = { Text("This removes its unsaved time. Your past check-ins stay saved.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { model.discardTimer(current.sessionId); discard = false }) { Text("Discard timer") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep timer") } })
    if (current != null && definition != null && state.timerReviewSession == current.sessionId) TimerReviewDialog(model, current, definition!!, elapsed)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerReviewDialog(model: MainViewModel, timer: ActiveTimer, definition: HabitVersionEntity, elapsed: Long) {
    val state by model.ui.collectAsStateWithLifecycle()
    var minutes by rememberSaveable(timer.sessionId) { mutableStateOf((elapsed / 60_000).toString()) }
    var seconds by rememberSaveable(timer.sessionId) { mutableStateOf(((elapsed / 1000) % 60).toString()) }
    var day by rememberSaveable(timer.sessionId) { mutableStateOf(LocalDate.ofEpochDay(timer.day).toString()) }
    var status by rememberSaveable(timer.sessionId) { mutableStateOf(CompletionStatus.DONE) }
    var value by rememberSaveable(timer.sessionId) { mutableStateOf("") }
    var checked by rememberSaveable(timer.sessionId) { mutableStateOf(emptyList<Int>()) }
    val total = minutes.toLongOrNull()?.takeIf { it in 0..10080 }?.let { min -> seconds.toIntOrNull()?.takeIf { it in 0..59 }?.let { min * 60 + it } }
        ?.takeIf { it <= MAX_TIMER_MILLIS / 1000 }
    val tracking = definition.tracking
    val input = CompletionInput(status, if (tracking.kind == TrackingKind.DURATION) total?.toDouble() else value.toDoubleOrNull(), checked)
    val valid = total != null && runCatching { input.validateFor(tracking) }.isSuccess
    AlertDialog(onDismissRequest = model::dismissTimerReview, title = { Text("Save your timed check-in") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(definition.title, style = MaterialTheme.typography.titleLarge)
            Text("Your timer is paused. Check the time and date, then save what you did.")
            if (timer.recovery != TimerRecovery.NONE) Text("This is recovered time. Adjust it if it doesn't match the time you spent.")
            NumberField(minutes, { minutes = it.take(5) }, "Minutes")
            NumberField(seconds, { seconds = it.take(2) }, "Extra seconds (0–59)")
            if (total == null) Text("Use whole minutes and seconds, up to seven days.", color = MaterialTheme.colorScheme.error)
            DateChoice("Check-in date", day) { day = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(status == CompletionStatus.DONE, { status = CompletionStatus.DONE }, label = { Text("Completed") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(status == CompletionStatus.PARTIAL, { status = CompletionStatus.PARTIAL }, label = { Text("Some done") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            when (tracking.kind) {
                TrackingKind.CHECKBOX, TrackingKind.DURATION -> Unit
                TrackingKind.CHECKLIST -> tracking.items.forEachIndexed { index, label -> SelectRow(label, index in checked) { checked = if (index in checked) checked - index else checked + index } }
                else -> NumberField(value, { value = it }, when (tracking.kind) { TrackingKind.COUNT -> "Count"; TrackingKind.RATING -> "Rating (1–" + tracking.target?.toInt() + ")"; else -> tracking.unit.orEmpty() })
            }
            if (!valid && total != null) Text("Add a valid result, or choose Some done for an unfinished checklist.", color = MaterialTheme.colorScheme.error)
            Text("Uses the instructions from when you started.", style = MaterialTheme.typography.bodySmall)
            LinkedHabitDetails(model, definition.habitId, definition.version)
        } },
        confirmButton = { TextButton(enabled = valid && !state.busy, onClick = { model.finishTimer(timer.sessionId, total!!, LocalDate.parse(day), input) }) { Text("Save check-in") } },
        dismissButton = { TextButton(enabled = !state.busy, onClick = model::dismissTimerReview) { Text("Keep paused") } })
}

@Composable
internal fun StartTimerButton(model: MainViewModel, id: String, enabled: Boolean = true) {
    val timer by model.activeTimer.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    TextButton(enabled = enabled && !state.busy, onClick = { if (timer == null) model.startTimer(id) else model.requestTimer() }) { Text(if (timer == null) "Start timer" else "Open running timer") }
}

@Composable
internal fun ActionReminderButton(model: MainViewModel, id: String) {
    val rules by model.reminders.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    val rule = rules?.find { it.habitId == id }
    TextButton(enabled = rules != null && !state.busy, onClick = { editing = true }) { Text(if (rule?.enabled == true) "Reminder: " + displayTime(rule.minuteOfDay) else "Add a reminder") }
    if (editing) ReminderDialog(model, id, rule) { editing = false }
}

@Composable
private fun ReminderDialog(model: MainViewModel, id: String, old: HabitReminder?, onClose: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    var enabled by rememberSaveable { mutableStateOf(old?.enabled ?: true) }
    var minute by rememberSaveable { mutableIntStateOf(old?.minuteOfDay ?: 8 * 60) }
    AlertDialog(onDismissRequest = onClose, title = { Text("A nudge for this action") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchRow("Remind me", enabled) { enabled = it }
            TimeChoice("Time", minute) { minute = it }
            Text("Follows this action's scheduled days in your phone's time zone. Completed or skipped actions won't nudge you again that day.")
            Text("Quiet hours can postpone it. Android may delay reminders, especially with battery restrictions.")
            if (!state.timingStatus.reminders) Text("Notifications are off. Save your choice, then allow them under You → Reminders & timers.")
        }
    }, confirmButton = { TextButton(enabled = !state.busy, onClick = { model.saveReminder(HabitReminder(id, minute, enabled), onClose) }) { Text("Save reminder") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Not now") } })
}

@Composable
internal fun RemindersScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val prefs by model.reminderPreferences.collectAsStateWithLifecycle()
    val actions by model.reminderActions.collectAsStateWithLifecycle()
    val rules by model.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refreshTiming() }
    fun openSettings(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { model.notifyUser("That setting couldn't be opened. Find Aesthory in your phone's app settings.") }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to you") }
            Text("A nudge, on your terms.", style = MaterialTheme.typography.displaySmall)
            Text("Choose what helps you show up. Timers work even if notifications are off.")
        }
        item {
            SectionHeading("Phone notifications")
            Text(if (state.timingStatus.notifications) "Notifications are allowed on this phone." else "Allow notifications to get reminders and timer controls outside the app.")
            if (!state.timingStatus.notifications && Build.VERSION.SDK_INT >= 33) Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            TextButton(onClick = { openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
            if (state.timingStatus.notifications && !state.timingStatus.reminders) Text("The Routine reminders category is off in phone settings.")
            if (state.timingStatus.notifications && !state.timingStatus.timers) Text("The Timers category is off in phone settings.")
            OutlinedButton(enabled = state.timingStatus.reminders, onClick = model::testReminder) { Text("Send a test reminder") }
            if (state.timingStatus.backgroundIssue) Text("A background update couldn't finish. Reopen this screen to retry, and check your phone settings.")
        }
        item {
            SectionHeading("Your reminder rhythm")
            SwitchRow("Pause all reminders", prefs.paused) { model.saveReminderPreferences(prefs.copy(paused = it)) }
            SwitchRow("Quiet hours", prefs.quietEnabled) {
                if (it && prefs.quietStart == prefs.quietEnd) model.notifyUser("Choose different start and end times for quiet hours.")
                else model.saveReminderPreferences(prefs.copy(quietEnabled = it))
            }
            if (prefs.quietEnabled) {
                TimeChoice("Quiet from", prefs.quietStart) { if (it == prefs.quietEnd) model.notifyUser("Quiet hours need different start and end times.") else model.saveReminderPreferences(prefs.copy(quietStart = it)) }
                TimeChoice("Until", prefs.quietEnd) { if (it == prefs.quietStart) model.notifyUser("Quiet hours need different start and end times.") else model.saveReminderPreferences(prefs.copy(quietEnd = it)) }
                Text("Reminders during quiet hours wait until the quiet period ends. Timer-goal alerts use your phone's Timers notification settings.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionHeading("Timer-goal alerts")
            Text(if (state.timingStatus.precise) "Precise timer alerts are available. Phone notification and Do Not Disturb settings still apply." else "Android may delay a goal alert while the app is closed. You can allow precise alerts in your phone settings.")
            if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = { openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) }) { Text("Precise timer alert settings") }
        }
        item {
            SectionHeading("Your actions")
            Text("Only active actions in active routines send reminders. Use Snooze 10 min on a reminder when you need a little longer.")
            if (actions.isEmpty()) Text("Add an action in Routines first.")
        }
        items(actions, key = { it.habit.definition.habitId }) { action ->
            val habit = action.habit
            if (habit.state != HabitState.ARCHIVED || rules.orEmpty().any { it.habitId == habit.definition.habitId }) {
                Text(habit.definition.title, style = MaterialTheme.typography.titleLarge)
                Text(if (action.routineArchived || habit.state != HabitState.ACTIVE) "Reminder paused while this action or routine is inactive." else repeatLabel(habit.definition.schedule.toDomain(), LocalConfiguration.current.locales[0]))
                ActionReminderButton(model, habit.definition.habitId)
                HorizontalDivider()
            }
        }
        item { Text("Reminders are approximate and can be affected by battery settings. Force-stopping the app blocks its alerts until you open it again; saved timer time still recovers.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun TimeChoice(label: String, minute: Int, change: (Int) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = { TimePickerDialog(context, { _, hour, min -> change(hour * 60 + min) }, minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show() }) { Text(label + ": " + displayTime(minute)) }
}

@Composable
private fun displayTime(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(
    if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a", LocalConfiguration.current.locales[0]))
