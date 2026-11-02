package app.aesthory.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.coaching.*
import app.aesthory.data.CurrentHabit
import app.aesthory.profile.AgeBand
import app.aesthory.routines.*
import java.util.Locale
import java.time.format.TextStyle

internal fun repeatLabel(schedule: HabitSchedule, locale: Locale): String = when (val recurrence = schedule.recurrence) {
    Recurrence.Daily -> "Every day"
    is Recurrence.EveryNDays -> "Every ${recurrence.interval} days"
    is Recurrence.Weekdays -> recurrence.days.sortedBy { it.value }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

@Composable
internal fun TodayScreen(model: MainViewModel, onRoutines: () -> Unit, onAnalyze: () -> Unit) {
    val items by model.today.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    val timer by model.activeTimer.collectAsStateWithLifecycle()
    val guidance = guideToday(items)
    var logging by remember { mutableStateOf<CurrentHabit?>(null) }
    val haptic = LocalHapticFeedback.current
    fun complete(habit: CurrentHabit) {
        if (habit.definition.tracking.kind == TrackingKind.CHECKBOX) {
            model.logHabit(habit.definition.habitId, CompletionInput(CompletionStatus.DONE)) { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
        } else logging = habit
    }
    Page {
        Text(state.profile?.displayName?.let { "Hey, $it." } ?: "Hey, you.", style = MaterialTheme.typography.displaySmall)
        Text("Let's make today feel good.", style = MaterialTheme.typography.titleMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(guidance.title, style = MaterialTheme.typography.headlineLarge)
                Text(guidance.detail)
                if (guidance.total > 0) {
                    LinearProgressIndicator(progress = { guidance.done.toFloat() / guidance.total }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onPrimary, trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                    Text("${guidance.done} of ${guidance.total} actions done", style = MaterialTheme.typography.labelLarge)
                }
                guidance.next?.let { next ->
                    Text(next.habit.definition.title, style = MaterialTheme.typography.titleLarge)
                    Button(enabled = !state.busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary), onClick = {
                        if (next.habit.definition.tracking.kind == TrackingKind.DURATION) { if (timer == null) model.startTimer(next.habit.definition.habitId) else model.requestTimer() } else complete(next.habit)
                    }) { Text(if (next.habit.definition.tracking.kind == TrackingKind.DURATION) { if (timer == null) "Start timer" else "Open timer" } else if (next.habit.definition.tracking.kind == TrackingKind.CHECKBOX) "Did it!" else "Check in") }
                }
            }
        }
        if (items.isNotEmpty()) {
            SectionHeading("Your daily actions", "Small wins count.")
            items.forEach { item ->
                val done = item.event?.status == CompletionStatus.DONE
                var expanded by rememberSaveable(item.habit.definition.habitId) { mutableStateOf(false) }
                Column(Modifier.animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = done, enabled = !state.busy,
                            modifier = Modifier.semantics { contentDescription = if (done) "Undo ${item.habit.definition.title}" else "Complete ${item.habit.definition.title}" },
                            onCheckedChange = { if (done) model.undo(item.habit.definition.habitId) else complete(item.habit) })
                        Column(Modifier.weight(1f)) {
                            Text(item.habit.definition.title, style = MaterialTheme.typography.titleMedium)
                            Text(when (item.event?.status) {
                                CompletionStatus.DONE -> "Done today"; CompletionStatus.SKIPPED -> "Skipped today"; CompletionStatus.PARTIAL -> "Started today"
                                else -> item.habit.definition.estimatedMinutes?.let { "About $it min" } ?: "At your pace"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "How to") }
                    }
                    if (expanded) {
                        Text(item.habit.definition.instructions.ifBlank { "Make this action yours. You can add instructions in your routine." }, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
                        TextButton(enabled = !state.busy, onClick = { model.logHabit(item.habit.definition.habitId, CompletionInput(CompletionStatus.SKIPPED)) }) { Text("Skip today") }
                        StartTimerButton(model, item.habit.definition.habitId)
                        ActionReminderButton(model, item.habit.definition.habitId)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            TextButton(onClick = onRoutines) { Text("Change my routines") }
        } else StarterPicker(model)
        FilledTonalButton(onClick = onAnalyze, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Explore my look with a photo") }
        Text("Your actions and photo check work right away. No extra AI download needed.", style = MaterialTheme.typography.bodySmall)
    }
    logging?.let { CompletionDialog(it, model) { logging = null } }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun StarterPicker(model: MainViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    val routines by model.routines.collectAsStateWithLifecycle()
    var all by rememberSaveable { mutableStateOf(false) }
    SectionHeading("A little help getting started", "Ready-made ideas. Change anything later.")
    val choices = StarterRoutines.suggested(state.profile?.coaches.orEmpty())
    (if (all) choices else choices.take(2)).forEachIndexed { index, starter ->
        val added = routines.any { it.id == "starter:${starter.id}" }
        Card(colors = CardDefaults.cardColors(containerColor = if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(starter.name, style = MaterialTheme.typography.headlineMedium)
                Text(starter.description)
                Text("${starter.steps.size} simple actions · about ${starter.minutes} min", style = MaterialTheme.typography.labelLarge)
                FilledTonalButton(enabled = !added && !state.busy, onClick = { model.addStarter(starter) }) { Text(if (added) "Added to your day" else "Try this routine") }
            }
        }
    }
    TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer ideas" else "See all starter routines") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoachScreen(model: MainViewModel, onPhoto: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = topicName?.let(GuideTopic::valueOf)
    val age = state.profile?.ageBand ?: AgeBand.UNSPECIFIED
    val guides = CoachLibrary.find(query, topic, age)
    Page {
        Text("Your coach is ready.", style = MaterialTheme.typography.displaySmall)
        Text("Pick a topic. Find a clear next step for your skin, hair, style or fitness.")
        FilledTonalButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Try a photo check") }
        Text("Explore your face shape and styling ideas. Your photo stays on your phone.", style = MaterialTheme.typography.bodySmall)
        Text("What are you curious about?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(query, { query = it }, label = { Text("Search skincare, jaw, hair…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = topic == null, onClick = { topicName = null }, label = { Text("All") }, modifier = Modifier.heightIn(min = 48.dp))
            GuideTopic.entries.forEach { option -> FilterChip(selected = topic == option, onClick = { topicName = option.name }, label = { Text(option.title) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        if (guides.isEmpty()) Text(if (topic == GuideTopic.PROCEDURES && age != AgeBand.ADULT) "Procedure guides are for adults. You can set your age group in You → About you. Everyday style and care guides are available to everyone." else "No match yet. Try a shorter word or choose another topic.")
        guides.forEach { guide -> GuideRow(guide) }
        Text("A growing guide library, not every forum on the internet. We separate care guidance, personal style and forum claims so you can judge advice for yourself.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GuideRow(guide: CoachGuide) {
    var open by rememberSaveable(guide.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { open = !open }, shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(guide.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(if (guide.sources.isEmpty()) "Style & vocabulary" else "With expert sources", style = MaterialTheme.typography.labelMedium)
            }
            Text(if (open) "Close" else "Read", modifier = Modifier.padding(start = 12.dp))
        }
        if (open) {
            Text(guide.summary, style = MaterialTheme.typography.titleMedium)
            guide.actions.forEachIndexed { index, action -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(action, modifier = Modifier.weight(1f))
            } }
            Text(guide.note, style = MaterialTheme.typography.bodySmall)
            guide.sources.forEach { source -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }) { Text("Read source: ${source.name}") } }
        }
        HorizontalDivider()
    }
}
