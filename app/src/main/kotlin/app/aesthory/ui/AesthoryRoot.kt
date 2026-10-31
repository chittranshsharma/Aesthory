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

private val light = lightColorScheme(
    primary = Color(0xFF315B43), onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E8D4), onPrimaryContainer = Color(0xFF102B19),
    secondary = Color(0xFF526354), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E6D8), onSecondaryContainer = Color(0xFF152419),
    tertiary = Color(0xFF41636A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC5E9F1), onTertiaryContainer = Color(0xFF062027),
    background = Color(0xFFF7F8F4), surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF19221C), onSurface = Color(0xFF19221C),
    surfaceVariant = Color(0xFFDFE4D9), onSurfaceVariant = Color(0xFF424B42),
    surfaceContainer = Color(0xFFEBEFE6), surfaceContainerLow = Color(0xFFF1F4ED),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE5EADF),
    surfaceContainerHighest = Color(0xFFDFE4D9), outline = Color(0xFF737C72),
    outlineVariant = Color(0xFFC2CABB), surfaceTint = Color(0xFF315B43),
)
private val dark = darkColorScheme(
    primary = Color(0xFFA4D3B2), onPrimary = Color(0xFF143521),
    primaryContainer = Color(0xFF2A4D35), onPrimaryContainer = Color(0xFFD0E8D4),
    secondary = Color(0xFFB6CDB8), onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF344A39), onSecondaryContainer = Color(0xFFD9EBD8),
    tertiary = Color(0xFFA1CCD3), onTertiary = Color(0xFF14343A),
    tertiaryContainer = Color(0xFF2A4B52), onTertiaryContainer = Color(0xFFC5E9F1),
    background = Color(0xFF101612), surface = Color(0xFF18221C),
    onBackground = Color(0xFFEDF4EE), onSurface = Color(0xFFEDF4EE),
    surfaceVariant = Color(0xFF354038), onSurfaceVariant = Color(0xFFC2CFC3),
    surfaceContainer = Color(0xFF1C2820), surfaceContainerLow = Color(0xFF17211B),
    surfaceContainerLowest = Color(0xFF0C120E), surfaceContainerHigh = Color(0xFF253128),
    surfaceContainerHighest = Color(0xFF303D33), outline = Color(0xFF8C978D),
    outlineVariant = Color(0xFF424F44), surfaceTint = Color(0xFFA4D3B2),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AesthoryRoot(model: MainViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it, actionLabel = "Dismiss"); model.clearMessage() }
    }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light) {
        BackHandler(tab != 0) { tab = 0 }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text(if (state.profile == null) "Aesthory" else listOf("Today", "Analyze", "Routines", "Profile")[tab]) }) },
            bottomBar = {
                if (state.profile != null) NavigationBar {
                    val titles = listOf("Today", "Analyze", "Routines", "Profile")
                    val icons = listOf(R.drawable.ic_home, R.drawable.ic_face, R.drawable.ic_routines, R.drawable.ic_profile)
                    titles.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
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
                    TextButton(onClick = model::cancelWork, modifier = Modifier.align(Alignment.End)) { Text("Cancel action") }
                }
                when {
                    !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.profile == null -> BaselineForm(null, model, state.busy)
                    tab == 0 -> TodayScreen(model, onRoutines = { tab = 2 }, onAnalyze = { tab = 1 })
                    tab == 1 -> AnalyzeScreen(model)
                    tab == 2 -> RoutinesScreen(model)
                    else -> ProfileScreen(model)
                }
            }
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
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
private fun BaselineForm(profile: ProfileEntity?, model: MainViewModel, busy: Boolean) {
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
        Text(if (profile == null) if (step == 0) "Make it personal." else "Your starting point." else "Your baseline", style = MaterialTheme.typography.headlineLarge)
        Text("Face, physique, skin and hair. Choose what matters to you; add the rest when you're ready.")
        Text("Your profile stays on this device. No email or account needed.", style = MaterialTheme.typography.bodyMedium)
        if (profile == null) Text("Step ${step + 1} of 2", style = MaterialTheme.typography.bodyMedium)
        if (profile != null || step == 0) {
        OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Your focus", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachMode.entries.forEach { coach ->
            FilterChip(
                selected = coach.name in coaches,
                onClick = { coaches = if (coach.name in coaches) coaches - coach.name else coaches + coach.name },
                label = { Text(coach.displayName) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        }
        Text("Coaching tone", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoachingTone.entries.forEach { option -> FilterChip(selected = tone == option.name,
                onClick = { tone = option.name }, label = { Text(if (option == CoachingTone.SUPPORTIVE) "Supportive" else "Direct") },
                modifier = Modifier.heightIn(min = 48.dp)) }
        }
        Text("Age band (optional)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeBand.entries.forEach { value -> FilterChip(selected = age == value.name,
                onClick = { age = value.name }, label = { Text(when (value) {
                    AgeBand.UNSPECIFIED -> "Not specified"; AgeBand.UNDER_18 -> "Under 18"; AgeBand.ADULT -> "18+"
                }) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        }
        if (profile != null || step == 1) {
        Text("Measurements (optional)", style = MaterialTheme.typography.titleMedium)
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
        Text("Later measurements are separate dated records. AI and photos are optional in Profile.", style = MaterialTheme.typography.bodySmall)
        }
        if (profile == null && step == 0) {
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
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
        ) { Text(if (profile == null) "Start my baseline" else "Save profile") }
        }
    }
}

@Composable
private fun NumberField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TodayScreen(model: MainViewModel, onRoutines: () -> Unit, onAnalyze: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val items by model.today.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf<CurrentHabit?>(null) }
    Page {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)), style = MaterialTheme.typography.bodyLarge)
        Text("Small steps. Your direction.", style = MaterialTheme.typography.headlineLarge)
        Text("A clear plan for your appearance goals, built around what you actually do.")
        FilledTonalButton(onClick = onAnalyze) { Text("Analyze a photo locally") }
        Text("Today's routines", style = MaterialTheme.typography.titleLarge)
        if (items.isEmpty()) {
            Text("Nothing scheduled yet. Add your first routine and give it a manageable daily action.")
            Button(onClick = onRoutines) { Text("Create a routine") }
        } else {
            Text("${items.count { it.event?.status == CompletionStatus.DONE }} of ${items.size} complete", style = MaterialTheme.typography.bodyMedium)
            items.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.habit.definition.title, style = MaterialTheme.typography.titleMedium)
                        item.habit.definition.estimatedMinutes?.let { Text("$it min", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (item.event?.status == CompletionStatus.DONE) TextButton(enabled = !state.busy, onClick = { model.undo(item.habit.definition.habitId) }) { Text("Undo") }
                    else TextButton(enabled = !state.busy, onClick = { logging = item.habit }) { Text("Log") }
                }
                HorizontalDivider()
            }
        }
    }
    logging?.let { habit -> CompletionDialog(habit, model) { logging = null } }
}

@Composable
private fun RoutinesScreen(model: MainViewModel) {
    val routines by model.routines.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var addingRoutine by remember { mutableStateOf(false) }
    var addingHabit by remember { mutableStateOf<String?>(null) }
    Page {
        Text("Build a routine you can repeat.", style = MaterialTheme.typography.headlineMedium)
        Text("Keep instructions specific and the daily effort realistic.")
        Button(onClick = { addingRoutine = true }, enabled = !state.busy) { Text("New routine") }
        if (routines.isEmpty()) Text("Your routines will appear here. Start with one part of your day.")
        routines.forEach { routine ->
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            RoutineHabits(routine, model)
            TextButton(onClick = { addingHabit = routine.id }, enabled = !state.busy) { Text("Add habit") }
            HorizontalDivider()
        }
    }
    if (addingRoutine) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingRoutine = false }, title = { Text("New routine") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Routine name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !state.busy, onClick = { model.createRoutine(name) { addingRoutine = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { addingRoutine = false }) { Text("Cancel") } },
        )
    }
    addingHabit?.let { routineId ->
        var title by rememberSaveable { mutableStateOf("") }
        var instructions by rememberSaveable { mutableStateOf("") }
        var minutes by rememberSaveable { mutableStateOf("") }
        val validMinutes = minutes.isBlank() || minutes.toIntOrNull()?.let { it > 0 } == true
        AlertDialog(
            onDismissRequest = { addingHabit = null }, title = { Text("Daily habit") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Habit name") })
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions") })
                NumberField(minutes, { minutes = it }, "Estimated minutes (optional)")
                Text("Starts today, repeats daily, and tracks completion.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = title.isNotBlank() && validMinutes && !state.busy, onClick = {
                model.addHabit(routineId, title, instructions, minutes.toIntOrNull()) { addingHabit = null }
            }) { Text("Add habit") } },
            dismissButton = { TextButton(onClick = { addingHabit = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoutineHabits(routine: RoutineEntity, model: MainViewModel) {
    val habits by remember(routine.id) { model.habits(routine.id) }.collectAsStateWithLifecycle(emptyList())
    habits.forEach { habit ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(habit.definition.title, style = MaterialTheme.typography.titleMedium)
            if (habit.definition.instructions.isNotBlank()) Text(habit.definition.instructions, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CompletionDialog(habit: CurrentHabit, model: MainViewModel, dismiss: () -> Unit) {
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
            when (settings.kind) {
                TrackingKind.CHECKBOX -> Text("Mark this habit complete for today.")
                TrackingKind.CHECKLIST -> settings.items.forEachIndexed { index, label -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(index in checked, onCheckedChange = { checked = if (it) checked + index else checked - index }); Text(label)
                } }
                else -> NumberField(value, { value = it }, when (settings.kind) {
                    TrackingKind.DURATION -> "Seconds"; TrackingKind.RATING -> "Rating (1–${settings.target?.toInt()})"
                    TrackingKind.VALUE -> settings.unit.orEmpty(); else -> "Count"
                })
            }
        } },
        confirmButton = { TextButton(enabled = valid && !state.busy, onClick = { model.logHabit(habit.definition.habitId, input, dismiss) }) { Text("Complete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyzeScreen(model: MainViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val state by model.ui.collectAsStateWithLifecycle()
    var goalName by rememberSaveable { mutableStateOf(PresentationGoal.DOCUMENT.name) }
    val goal = PresentationGoal.valueOf(goalName)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.analyze(uri, goal) }
    Page {
        Text("Understand your proportions.", style = MaterialTheme.typography.headlineLarge)
        Text("A local face-landmark model reviews visible facial structure, eye placement and jaw/cheek contours. Your photo stays on this device.")
        Text("What would you like to explore?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresentationGoal.entries.forEach { option -> FilterChip(
            selected = goal == option, enabled = !state.busy,
            onClick = { if (goal != option) { goalName = option.name; model.clearAnalysis() } },
            label = { Text(when (option) {
                PresentationGoal.DOCUMENT -> "Document my baseline"; PresentationGoal.MORE_LENGTH -> "A longer presentation"
                PresentationGoal.MORE_WIDTH -> "A wider presentation"; PresentationGoal.SOFTER_PRESENTATION -> "Softer styling"
                PresentationGoal.MORE_DEFINITION -> "More definition through styling"
            }) }, modifier = Modifier.heightIn(min = 48.dp),
        ) }
        }
        Button(enabled = !state.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose a face photo") }
        Text("Use an unfiltered front photo, level camera, neutral expression and even lighting. Leave space around the full face.", style = MaterialTheme.typography.bodyMedium)
        state.photo?.let { photo ->
            Image(photo.bitmap.asImageBitmap(), contentDescription = "Selected photo for local analysis", modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp))
            when (val review = photo.review) {
                is FaceReview.Recapture -> {
                    Text("Let's get a clearer baseline.", style = MaterialTheme.typography.titleLarge)
                    review.issues.forEach { Text(captureMessage(it)) }
                }
                is FaceReview.Measured -> {
                    Text("Your photo's proportions", style = MaterialTheme.typography.titleLarge)
                    Text("Surface-landmark estimates, not hidden-bone measurements or an objective PSL score.", style = MaterialTheme.typography.bodyMedium)
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
                    Text("What to try next", style = MaterialTheme.typography.titleLarge)
                    review.priorities.forEach { Text(it) }
                    if (state.modelInstalled) {
                        FilledTonalButton(enabled = !state.busy, onClick = { model.generateOffline(goal) }) { Text("Draft deeper offline coaching") }
                        Text("The imported language model receives these measurements, not the original photo. Its advice is a draft.", style = MaterialTheme.typography.bodySmall)
                    } else Text("You can import an optional offline language model in Profile for a deeper coaching draft.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.offlineText?.let { Text("Offline coaching draft", style = MaterialTheme.typography.titleLarge); Text(it) }
    }
}

private fun captureMessage(issue: CaptureIssue) = when (issue) {
    CaptureIssue.ONE_FACE_REQUIRED -> "Choose a photo containing one clearly visible face."
    CaptureIssue.INCOMPLETE_LANDMARKS -> "The facial landmarks were incomplete. Try an evenly lit, unobstructed photo."
    CaptureIssue.IMAGE_TOO_SMALL -> "Choose a photo at least 512 pixels on both sides."
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
    var key by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importModel(uri) }
    if (editing) {
        BackHandler { editing = false }
        Column {
            TextButton(onClick = { editing = false }) { Text("Back to profile") }
            BaselineForm(state.profile, model, state.busy)
        }
        return
    }
    Page {
        Text(state.profile?.displayName ?: "Your profile", style = MaterialTheme.typography.headlineLarge)
        Text("No email login. Your routines, measurements and preferences live here on your device.")
        Button(onClick = { editing = true }) { Text("Edit baseline") }
        measurements.firstOrNull()?.let { latest -> Text("Latest measurements: ${latest.values.heightCm?.let { "$it cm" } ?: "height not entered"}, ${latest.values.weightKg?.let { "$it kg" } ?: "weight not entered"}. ${measurements.size} dated records.") }
        HorizontalDivider()
        Text("Offline coaching", style = MaterialTheme.typography.titleLarge)
        Text("The face-landmark model is included. For a deeper written draft, import a compatible .litertlm text model from a trusted source. For your 8 GB+ phone, start with a quantized small model; performance depends on the device.")
        Text(if (state.modelInstalled) "A local language-model file is installed." else "No optional language model installed.")
        Button(enabled = !state.busy, onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import local model") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.google.com/edge/litert-lm/android"))) }) { Text("Compatible model guide") }
        if (state.modelInstalled) TextButton(enabled = !state.busy, onClick = model::removeModel) { Text("Remove local model") }
        Text("Choose a CPU-compatible LiteRT-LM model. Import verifies its container and initializes it before replacing an existing model. Large files consume storage; all processing stays local.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Optional Groq connection", style = MaterialTheme.typography.titleLarge)
        Text("Use your own Groq Free-plan key. This is optional cloud access; local analysis and routines do not need it.")
        OutlinedTextField(key, { key = it }, label = { Text("Groq API key") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !state.busy && key.isNotBlank(), onClick = { model.saveKey(key) { key = "" } }) { Text("Save encrypted key") }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }) { Text("Get Groq API key") }
        if (state.keyConfigured) {
            Text("A key is saved. Test sends it to Groq to list available models; billing status is not verified.")
            OutlinedButton(enabled = !state.busy, onClick = model::testKey) { Text("Test connection") }
            TextButton(enabled = !state.busy, onClick = model::deleteKey) { Text("Delete API key") }
        }
    }
}
