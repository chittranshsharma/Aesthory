package app.aesthory.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.R
import app.aesthory.coaching.CoachMode
import app.aesthory.data.CurrentHabit
import app.aesthory.data.ProfileEntity
import app.aesthory.data.RoutineEntity
import app.aesthory.face.CaptureIssue
import app.aesthory.face.FaceReview
import app.aesthory.face.PresentationGoal
import app.aesthory.profile.AgeBand
import app.aesthory.profile.BodyMeasurements
import app.aesthory.profile.CoachingTone
import app.aesthory.profile.ProfileBaseline
import app.aesthory.routines.CompletionInput
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.TrackingKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AesthoryRoot(model: MainViewModel) {
    RefreshCalendarDate(model)
    val state by model.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var photoReview by rememberSaveable { mutableStateOf(false) }
    var recovery by rememberSaveable { mutableStateOf(false) }
    var timerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.openTimerRequest) { if (state.openTimerRequest > 0) { tab = 0; recovery = false; timerOpen = true } }
    LaunchedEffect(state.openTodayRequest) { if (state.openTodayRequest > 0) { tab = 0; recovery = false; timerOpen = false } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            val undo = state.undoPhotoId != null && it == "Photo moved to Recently deleted."
            val result = snackbar.showSnackbar(it, actionLabel = if (undo) "Undo" else "Dismiss", duration = if (undo) SnackbarDuration.Long else SnackbarDuration.Short)
            if (undo && result == SnackbarResult.ActionPerformed) model.undoPhoto() else model.clearMessage()
        }
    }
    AesthoryTheme {
        BackHandler(tab != 0 || recovery || timerOpen) { if (timerOpen) timerOpen = false else if (recovery) recovery = false else if (tab == 1 && photoReview) photoReview = false else tab = 0 }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text(if (timerOpen) "Timer" else if (state.profile == null) "Aesthory" else listOf("Today", "Coach", "Routines", "You")[tab]) }) },
            bottomBar = {
                if (state.profile != null && state.openEditors == 0 && !timerOpen) NavigationBar {
                    val titles = listOf("Today", "Coach", "Routines", "You")
                    val icons = listOf(R.drawable.ic_home, R.drawable.ic_face, R.drawable.ic_routines, R.drawable.ic_profile)
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index; photoReview = false },
                            icon = { Icon(painterResource(icons[index]), contentDescription = null) },
                            label = { Text(title) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(enabled = state.canCancel, onClick = model::cancelWork, modifier = Modifier.align(Alignment.End)) { Text(if (state.canCancel) "Cancel action" else "Finishing safely…") }
                }
                if (!timerOpen && state.openEditors == 0 && state.profile != null) TimerBar(model) { timerOpen = true; tab = 0 }
                when {
                    !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    timerOpen && state.profile != null -> TimerScreen(model) { timerOpen = false; tab = 0 }
                    recovery -> BackupScreen(model, if (state.profile == null) "Back to setup" else "Back to Today") { recovery = false; tab = 0 }
                    state.profile == null -> BaselineForm(null, model, state.busy, onRestore = { recovery = true })
                    tab == 0 -> TodayScreen(model, onRoutines = { tab = 2 }, onAnalyze = { tab = 1; photoReview = true })
                    tab == 1 -> if (photoReview) AnalyzeScreen(model) { photoReview = false } else CoachScreen(model) { photoReview = true }
                    tab == 2 -> RoutinesScreen(model)
                    else -> ProfileScreen(model)
                }
            }
        }
        PhotoDetailsDialog(model)
    }
}

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaselineForm(profile: ProfileEntity?, model: MainViewModel, busy: Boolean, onRestore: (() -> Unit)? = null) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(profile == null && step > 0) { step = 0 }
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.displayName.orEmpty()) }
    var age by rememberSaveable(profile?.id) { mutableStateOf(profile?.ageBand?.name ?: AgeBand.UNSPECIFIED.name) }
    var coaches by rememberSaveable(profile?.id) { mutableStateOf(profile?.coaches?.map { it.name } ?: listOf(CoachMode.FACIAL_AESTHETICS.name)) }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var chest by rememberSaveable { mutableStateOf("") }
    var shoulders by rememberSaveable { mutableStateOf("") }
    var arm by rememberSaveable { mutableStateOf("") }
    var thigh by rememberSaveable { mutableStateOf("") }
    var bodyFat by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var tone by rememberSaveable(profile?.id) { mutableStateOf(profile?.tone?.name ?: CoachingTone.SUPPORTIVE.name) }
    val valid = listOf(height, weight, waist, chest, shoulders, arm, thigh).all {
        it.isBlank() || it.toDoubleOrNull()?.let { n -> n.isFinite() && n > 0 } == true
    } && (bodyFat.isBlank() || bodyFat.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 100 } == true)
    Page {
        Text(if (profile == null) if (step == 0) "Your glow-up, your rules." else "Start where you are." else "About you", style = MaterialTheme.typography.headlineLarge)
        Text("Pick what you want to work on. We'll help you turn it into everyday actions.")
        Text("No sign-up. No downloads to get started. Just you and your next good habit.", style = MaterialTheme.typography.bodyMedium)
        if (profile == null) Text("Step ${step + 1} of 2", style = MaterialTheme.typography.bodyMedium)
        if (profile != null || step == 0) {
        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("What brings you here?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachMode.entries.forEach { coach ->
            FilterChip(
                selected = coach.name in coaches,
                onClick = { coaches = if (coach.name in coaches) coaches - coach.name else coaches + coach.name },
                label = { Text(coach.displayName) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        }
        Text("How should your coach sound?", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoachingTone.entries.forEach { option -> FilterChip(selected = tone == option.name,
                onClick = { tone = option.name }, label = { Text(if (option == CoachingTone.SUPPORTIVE) "Supportive" else "Direct") },
                modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text("Age group (optional)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeBand.entries.forEach { value -> FilterChip(selected = age == value.name,
                onClick = { age = value.name }, label = { Text(when (value) {
                    AgeBand.UNSPECIFIED -> "Not specified"; AgeBand.UNDER_18 -> "Under 18"; AgeBand.ADULT -> "18+"
                }) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        }
        if (profile != null || step == 1) {
        Text("A starting point, if you like", style = MaterialTheme.typography.titleMedium)
        NumberField(height, { height = it }, "Height in cm")
        NumberField(weight, { weight = it }, "Weight in kg")
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide additional measurements" else "Add body measurements") }
        if (advanced) {
            NumberField(waist, { waist = it }, "Waist circumference in cm")
            NumberField(chest, { chest = it }, "Chest circumference in cm")
            NumberField(shoulders, { shoulders = it }, "Shoulder circumference in cm")
            NumberField(arm, { arm = it }, "Upper-arm circumference in cm")
            NumberField(thigh, { thigh = it }, "Thigh circumference in cm")
            NumberField(bodyFat, { bodyFat = it }, "Known body-fat percentage (optional)")
            Text("Use an existing measurement; the photo analyzer does not estimate exact body fat.", style = MaterialTheme.typography.bodySmall)
        }
        if (!valid) Text("Use positive numbers, or leave unknown measurements empty.", color = MaterialTheme.colorScheme.error)
        Text("Skip anything you don't know. You can add or update measurements later.", style = MaterialTheme.typography.bodySmall)
        }
        if (profile == null && step == 0) {
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            onRestore?.let { TextButton(enabled = !busy, onClick = it) { Text("I have a backup to restore") } }
        } else {
        if (profile == null) TextButton(onClick = { step = 0 }) { Text("Back to focus") }
        Button(
            enabled = valid && !busy,
            onClick = {
                model.saveProfile(ProfileBaseline(
                    name, AgeBand.valueOf(age), coaches.map(CoachMode::valueOf).toSet(), CoachingTone.valueOf(tone),
                    measurements = BodyMeasurements(
                        height.toDoubleOrNull(), weight.toDoubleOrNull(), waist.toDoubleOrNull(), chest.toDoubleOrNull(),
                        shoulders.toDoubleOrNull(), arm.toDoubleOrNull(), thigh.toDoubleOrNull(), bodyFat.toDoubleOrNull(),
                    ),
                )) { height = ""; weight = ""; waist = ""; chest = ""; shoulders = ""; arm = ""; thigh = ""; bodyFat = "" }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (profile == null) "Let's get started" else "Save changes") }
        }
    }
}

@Composable
internal fun NumberField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun RoutinesScreen(model: MainViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val routines by model.routines.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var addingRoutine by remember { mutableStateOf(false) }
    var addingHabit by rememberSaveable { mutableStateOf<String?>(null) }
    var editingHabitId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
    if (historyId != null) {
        BackHandler { historyId = null }
        HabitHistoryScreen(model, historyId!!) { historyId = null }
        return
    }
    if (addingHabit != null) {
        val editingHabits by remember(addingHabit) { model.habits(addingHabit!!) }.collectAsStateWithLifecycle(emptyList())
        val existing = editingHabits.find { it.definition.habitId == editingHabitId }
        if (editingHabitId == null || existing != null) HabitEditorScreen(model, addingHabit!!, existing) { addingHabit = null; editingHabitId = null }
        else Page { Text("Opening your action…"); CircularProgressIndicator() }
        return
    }
    var removing by remember { mutableStateOf<RoutineEntity?>(null) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.previewPack(uri) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) model.writePack(uri) }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> if (uri != null) model.writePack(uri) }
    Page {
        Text("Make it a habit.", style = MaterialTheme.typography.headlineMedium)
        Text("Pick a ready-made routine or build something that fits your day.")
        Button(onClick = { addingRoutine = true }, enabled = !state.busy) { Text("New routine") }
        OutlinedButton(enabled = !state.busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Bring in a routine file") }
        if (routines.isEmpty()) Text("Your saved routines will appear here.")
        routines.forEach { routine ->
            var menu by rememberSaveable(routine.id) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(routine.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { menu = true }) { Text("More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save a routine file") }, enabled = !state.busy, onClick = { menu = false; model.prepareShare(routine.id) })
                        DropdownMenuItem(text = { Text("Save a readable plan") }, enabled = !state.busy, onClick = { menu = false; model.prepareShare(routine.id, readable = true) })
                        DropdownMenuItem(text = { Text("Remove from my day") }, enabled = !state.busy, onClick = { menu = false; removing = routine })
                    }
                }
            }
            RoutineHabits(routine, model, onEdit = { editingHabitId = it.definition.habitId; addingHabit = routine.id }, onHistory = { historyId = it })
            FilledTonalButton(onClick = { editingHabitId = null; addingHabit = routine.id }, enabled = !state.busy) { Text("Add an action") }
            HorizontalDivider()
        }
        StarterPicker(model)
    }
    val pack = state.packToImport ?: state.packToShare
    pack?.let { preview ->
        val importing = state.packToImport != null
        AlertDialog(onDismissRequest = model::dismissPacks,
            title = { Text(if (importing) "Add ${preview.name}?" else "Save ${preview.name}?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${preview.habits.size} actions. Includes instructions, schedule and tracking. Products, personal references, photos, measurements, API keys and check-in history stay out. Check your written instructions for private details before sharing.")
                if (!importing && state.shareReadable) Text("A readable text plan for viewing or sharing. Use a routine file if the other person wants to import it.")
                if (importing) Text("This becomes your own editable copy. Check the dates and instructions before adding it; it isn't verified health advice.")
                preview.habits.forEach { habit ->
                    Text(habit.title, style = MaterialTheme.typography.titleMedium)
                    Text(habit.instructions.ifBlank { "No instructions" })
                    Text(repeatLabel(habit.schedule, locale), style = MaterialTheme.typography.bodySmall)
                    val dateFormat = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
                    Text("Starts ${habit.schedule.startDate.format(dateFormat)}" + (habit.schedule.endDate?.let { " · ends ${it.format(dateFormat)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { if (importing) model.importPack() else if (state.shareReadable) saveText.launch("aesthory-plan.txt") else saveFile.launch("aesthory-routine.json") }) { Text(if (importing) "Add to my routines" else "Choose where to save") } },
            dismissButton = { TextButton(onClick = model::dismissPacks) { Text("Not now") } },
        )
    }
    removing?.let { routine -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${routine.name}?") }, text = { Text("It will leave your daily list. Your past check-ins stay saved.") }, confirmButton = { TextButton(onClick = { model.archiveRoutine(routine.id); removing = null }) { Text("Remove routine") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep it") } }) }
    if (addingRoutine) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingRoutine = false }, title = { Text("New routine") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Give it a name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !state.busy, onClick = { model.createRoutine(name) { addingRoutine = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { addingRoutine = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoutineHabits(routine: RoutineEntity, model: MainViewModel, onEdit: (CurrentHabit) -> Unit, onHistory: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val habits by remember(routine.id) { model.habits(routine.id) }.collectAsStateWithLifecycle(emptyList())
    val state by model.ui.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable(routine.id) { mutableStateOf(false) }
    if (habits.any { it.state == app.aesthory.routines.HabitState.ARCHIVED }) TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide archived actions" else "Show archived actions") }
    habits.filter { showArchived || it.state != app.aesthory.routines.HabitState.ARCHIVED }.forEach { habit ->
        var expanded by rememberSaveable(habit.definition.habitId) { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(habit.definition.title, style = MaterialTheme.typography.titleMedium)
                    Text(when (habit.state) { app.aesthory.routines.HabitState.PAUSED -> "Taking a break"; app.aesthory.routines.HabitState.ARCHIVED -> "Archived"; else -> repeatLabel(habit.definition.schedule.toDomain(), locale) + (habit.definition.estimatedMinutes?.let { " · about $it min" } ?: "") }, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "Details") }
            }
            if (expanded) {
                Text(habit.definition.instructions.ifBlank { "No instructions added yet." })
                if (habit.definition.purpose.isNotBlank()) Text("Why: " + habit.definition.purpose)
                if (habit.definition.shortInstructions.isNotBlank()) Text("Busy-day version: " + habit.definition.shortInstructions)
                Text("Tracking: " + trackingLabel(habit.definition.tracking.kind))
                LinkedHabitDetails(model, habit.definition.habitId, habit.definition.version)
                StartTimerButton(model, habit.definition.habitId, enabled = habit.state == app.aesthory.routines.HabitState.ACTIVE)
                ActionReminderButton(model, habit.definition.habitId)
                TextButton(enabled = !state.busy, onClick = { onEdit(habit) }) { Text("Edit action") }
                TextButton(onClick = { onHistory(habit.definition.habitId) }) { Text("Past check-ins") }
                Row {
                    TextButton(enabled = !state.busy && habit.position > 0, onClick = { model.moveHabit(habit.definition.habitId, -1) }) { Text("Move up") }
                    TextButton(enabled = !state.busy && habit.position < habits.lastIndex, onClick = { model.moveHabit(habit.definition.habitId, 1) }) { Text("Move down") }
                }
                TextButton(enabled = !state.busy, onClick = { model.setHabitState(habit.definition.habitId,
                    if (habit.state != app.aesthory.routines.HabitState.ACTIVE) app.aesthory.routines.HabitState.ACTIVE else app.aesthory.routines.HabitState.PAUSED) }) {
                    Text(if (habit.state != app.aesthory.routines.HabitState.ACTIVE) "Bring it back" else "Pause this habit")
                }
                if (habit.state != app.aesthory.routines.HabitState.ARCHIVED) TextButton(enabled = !state.busy, onClick = { model.setHabitState(habit.definition.habitId, app.aesthory.routines.HabitState.ARCHIVED) }) { Text("Archive action") }
            }
        }
    }
}

@Composable
internal fun CompletionDialog(habit: CurrentHabit, model: MainViewModel, dismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val state by model.ui.collectAsStateWithLifecycle()
    val settings = habit.definition.tracking
    val input = CompletionInput(CompletionStatus.DONE, value.toDoubleOrNull(), checked)
    val valid = runCatching { input.validateFor(settings) }.isSuccess
    AlertDialog(
        onDismissRequest = dismiss, title = { Text(habit.definition.title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (habit.definition.instructions.isNotBlank()) Text(habit.definition.instructions)
            if (habit.definition.shortInstructions.isNotBlank()) Text("Busy-day version: " + habit.definition.shortInstructions)
            LinkedHabitDetails(model, habit.definition.habitId, habit.definition.version)
            when (settings.kind) {
                TrackingKind.CHECKBOX -> Text("Done for today? Give yourself the credit.")
                TrackingKind.CHECKLIST -> settings.items.forEachIndexed { index, label -> SelectRow(label, index in checked) {
                    checked = if (index in checked) checked - index else checked + index
                } }
                else -> NumberField(value, { value = it }, when (settings.kind) {
                    TrackingKind.DURATION -> "Seconds"; TrackingKind.RATING -> "Rating (1–${settings.target?.toInt()})"
                    TrackingKind.VALUE -> settings.unit.orEmpty(); else -> "Count"
                })
            }
            if (settings.kind != TrackingKind.CHECKBOX) {
                val partial = CompletionInput(CompletionStatus.PARTIAL, value.toDoubleOrNull(), checked)
                TextButton(enabled = !state.busy && runCatching { partial.validateFor(settings) }.isSuccess, onClick = { model.logHabit(habit.definition.habitId, partial, dismiss) }) { Text("Save what I did") }
            }
            TextButton(enabled = !state.busy, onClick = { model.logHabit(habit.definition.habitId, CompletionInput(CompletionStatus.SKIPPED), dismiss) }) { Text("Skip for today") }
        } },
        confirmButton = { TextButton(enabled = valid && !state.busy, onClick = { model.logHabit(habit.definition.habitId, input, dismiss) }) { Text("Complete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyzeScreen(model: MainViewModel, onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val state by model.ui.collectAsStateWithLifecycle()
    var goalName by rememberSaveable { mutableStateOf(PresentationGoal.DOCUMENT.name) }
    val goal = PresentationGoal.valueOf(goalName)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.analyze(uri, goal) }
    Page {
        TextButton(onClick = onBack) { Text("Back to your coach") }
        Text("Get to know your look.", style = MaterialTheme.typography.displaySmall)
        Text("Explore your face shape and styling ideas with a photo. It stays on your phone, and no AI setup is needed.")
        Text("What would you like to explore?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresentationGoal.entries.forEach { option -> FilterChip(
            selected = goal == option, enabled = !state.busy,
            onClick = { if (goal != option) { goalName = option.name; model.clearAnalysis() } },
            label = { Text(when (option) {
                PresentationGoal.DOCUMENT -> "Get to know my face"; PresentationGoal.MORE_LENGTH -> "Explore a longer look"
                PresentationGoal.MORE_WIDTH -> "Explore a wider look"; PresentationGoal.SOFTER_PRESENTATION -> "Softer styling"
                PresentationGoal.MORE_DEFINITION -> "A more defined look"
            }) }, modifier = Modifier.heightIn(min = 48.dp),
        ) }
        }
        Button(enabled = !state.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose a face photo") }
        Text("Use an unfiltered front photo, level camera, neutral expression and even lighting. Leave space around the full face.", style = MaterialTheme.typography.bodyMedium)
        state.photo?.let { photo ->
            Image(photo.bitmap.asImageBitmap(), contentDescription = "Selected photo for local analysis", modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp))
            FilledTonalButton(enabled = !state.busy, onClick = model::beginAnalyzedPhoto) { Text("Keep as a progress photo") }
            when (val review = photo.review) {
                is FaceReview.Recapture -> {
                    Text("One quick photo tip", style = MaterialTheme.typography.titleLarge)
                    review.issues.forEach { Text(captureMessage(it)) }
                }
                is FaceReview.Measured -> {
                    Text("Your next style experiment", style = MaterialTheme.typography.headlineMedium)
                    review.priorities.take(2).forEach { Text(it) }
                    Text("This photo can help explore your look. It can't reveal hidden bones or tell you how attractive you are.", style = MaterialTheme.typography.bodyMedium)
                    var details by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { details = !details }) { Text(if (details) "Hide photo measurements" else "Curious? See photo measurements") }
                    if (details) {
                    val m = review.metrics
                    val rows = listOf(
                        "Face width / height" to m.faceWidthToHeight,
                        "Jaw contour / cheek width" to m.jawToCheekWidth,
                        "Inner / outer eye span" to m.innerToOuterEyeSpan,
                        "Lower-face share" to m.lowerFaceShare,
                        "Image-left eye-corner slope (°)" to m.imageLeftEyeTiltDegrees,
                        "Image-right eye-corner slope (°)" to m.imageRightEyeTiltDegrees,
                    )
                    rows.forEach { (label, number) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, modifier = Modifier.weight(1f)); Text(String.format(locale, "%.2f", number), fontWeight = FontWeight.Medium)
                    } }
                    }
                    if (state.modelInstalled) {
                        FilledTonalButton(enabled = !state.busy, onClick = { model.generateOffline(goal) }) { Text("Ask my optional AI coach") }
                        Text("The imported language model receives these measurements, not the original photo. Its advice is a draft.", style = MaterialTheme.typography.bodySmall)
                    } else Text("These ideas work without extra AI. Explore more in your Coach tab.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.offlineText?.let { Text("Extra coaching ideas", style = MaterialTheme.typography.titleLarge); Text(it) }
    }
}

private fun captureMessage(issue: CaptureIssue) = when (issue) {
    CaptureIssue.ONE_FACE_REQUIRED -> "Choose a photo containing one clearly visible face."
    CaptureIssue.INCOMPLETE_LANDMARKS -> "We couldn't see your whole face clearly. Try even lighting and keep hair away from your eyes."
    CaptureIssue.IMAGE_TOO_SMALL -> "That photo is too small to check clearly. Choose the original photo rather than a small thumbnail."
    CaptureIssue.CROPPED_FACE -> "Keep your forehead, chin and both sides of the face in frame."
    CaptureIssue.FACE_TOO_SMALL -> "Move closer while leaving space around your face."
    CaptureIssue.LEVEL_CAMERA -> "Level the camera and keep your head upright."
    CaptureIssue.FACE_FORWARD -> "Face the camera straight on; avoid a side angle."
    CaptureIssue.RELAX_EXPRESSION -> "Relax your expression and close your mouth gently."
}

@Composable
private fun ProfileScreen(model: MainViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    val measurements by model.measurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var extras by rememberSaveable { mutableStateOf(false) }
    var local by rememberSaveable { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importModel(uri) }
    if (section != null) {
        BackHandler { section = null }
        when (section) {
            "photos" -> ProgressScreen(model) { section = null }
            "library" -> LibraryScreen(model) { section = null }
            "nutrition" -> NutritionScreen(model) { section = null }
            "weekly" -> WeeklyScreen(model) { section = null }
            "reminders" -> RemindersScreen(model) { section = null }
            else -> BackupScreen(model) { section = null }
        }
        return
    }
    if (editing) {
        BackHandler { editing = false }
        Column {
            TextButton(onClick = { editing = false }) { Text("Back to you") }
            Box(Modifier.weight(1f)) { BaselineForm(state.profile, model, state.busy) }
        }
        return
    }
    Page {
        Text(state.profile?.displayName?.let { "Made for $it." } ?: "Make it yours.", style = MaterialTheme.typography.displaySmall)
        Text("Your goals, your pace. Change the plan whenever life changes.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your coach is included", style = MaterialTheme.typography.headlineMedium)
                Text("Your daily plan, care guides and photo checks are ready. No AI setup needed.")
            }
        }
        Button(onClick = { editing = true }) { Text("About you & your goals") }
        FilledTonalButton(onClick = { section = "photos" }) { Text("My progress photos") }
        FilledTonalButton(onClick = { section = "library" }) { Text("Products & references") }
        FilledTonalButton(onClick = { section = "nutrition" }) { Text("Nutrition") }
        FilledTonalButton(onClick = { section = "weekly" }) { Text("Weekly review & targets") }
        OutlinedButton(onClick = { section = "reminders"; model.refreshTiming() }) { Text("Reminders & timers") }
        OutlinedButton(onClick = { section = "backup" }) { Text("Backup & restore") }
        measurements.firstOrNull()?.let { latest ->
            SectionHeading("Your measurements")
            latest.values.heightCm?.let { Text("Height: $it cm") }
            latest.values.weightKg?.let { Text("Weight: $it kg") }
            Text("${measurements.size} saved check-in(s). Add new measurements in About you.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        SectionHeading("Extra AI tools", "Optional extras for people who want to experiment.")
        TextButton(onClick = { extras = !extras }) { Text(if (extras) "Close extra AI tools" else "Set up optional AI") }
        if (extras) {
            Text("Groq connection", style = MaterialTheme.typography.titleLarge)
            Text("Already use Groq? Save your own API key here. Keep your account on its Free plan. For now this checks your connection; built-in coaching works without it.")
            OutlinedTextField(key, { key = it }, label = { Text("Groq API key") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = !state.busy && key.isNotBlank(), onClick = { model.saveKey(key) { key = "" } }) { Text("Save my key") }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }) { Text("Get a Groq key") }
            if (state.keyConfigured) {
                Text("Your key is saved securely. Testing contacts Groq; it doesn't check your billing plan.")
                OutlinedButton(enabled = !state.busy, onClick = model::testKey) { Text("Check connection") }
                TextButton(enabled = !state.busy, onClick = model::deleteKey) { Text("Remove my key") }
            }
            TextButton(onClick = { local = !local }) { Text(if (local) "Close advanced offline AI" else "Advanced: offline AI") }
            if (local) {
                Text("For experienced users: import a trusted CPU-compatible .litertlm model. This adds experimental written suggestions and can use substantial storage and memory. It's separate from the included guidance.")
                Text(if (state.modelInstalled) "An extra offline AI model is installed." else "No extra model installed. Your built-in coach is ready anyway.")
                Button(enabled = !state.busy, onClick = { importer.launch(arrayOf("*/*")) }) { Text("Choose a model file") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.com/edge/litert-lm/android"))) }) { Text("Read the technical setup guide") }
                if (state.modelInstalled) TextButton(enabled = !state.busy, onClick = model::removeModel) { Text("Remove extra model") }
            }
        }
        HorizontalDivider()
        SectionHeading("Your privacy")
        Text("Your routines, measurements and saved progress photos stay in this app. Photo checks run on your phone too. Save a photo to your journal if you want to keep it.")
        Text("Use Backup & restore to save a password-protected copy elsewhere. Uninstalling removes data from this app; keep a backup and its password if you want to recover it.", style = MaterialTheme.typography.bodySmall)
    }
}
