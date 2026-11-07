package app.aesthory.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.health.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun StepsScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.dailySteps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var openError by remember { mutableStateOf(false) }
    var refreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun refresh() { refreshJob?.cancel(); refreshJob = scope.launch { model.refreshSteps() } }
    fun open(intent: Intent) {
        try { context.startActivity(intent); openError = false } catch (_: Exception) { openError = true }
    }
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
            if (event == Lifecycle.Event.ON_STOP) { refreshJob?.cancel(); model.clearStepsView() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); refreshJob?.cancel(); model.clearStepsView() }
    }
    BackHandler(onBack = onBack)
    Page {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Every step counts.", style = MaterialTheme.typography.displaySmall)
        Text("See the last seven days from Health Connect. Connect your fitness app there to bring its steps into view.")
        if (state.loading) { LinearProgressIndicator(); Text("Checking your steps…") }
        else if (state.error) {
            Text("Your steps couldn't be refreshed. Try again, or check Health Connect settings.")
            Button(onClick = ::refresh) { Text("Try again") }
        } else when (state.access) {
            StepsAccess.UNAVAILABLE -> Text("Health Connect isn't available on this device. It needs a supported phone running Android 9 or newer.")
            StepsAccess.UPDATE -> {
                Text("Install or update Health Connect to get started.")
                Button(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }) { Text("Get Health Connect") }
                TextButton(onClick = ::refresh) { Text("Check again") }
            }
            StepsAccess.PERMISSION -> {
                Text("Allow Aesthory to read your steps. You choose access on the next screen.")
                Button(onClick = { try { permission.launch(AndroidStepsSource.permissions) } catch (_: Exception) { openError = true } }) { Text("Connect steps") }
            }
            StepsAccess.AVAILABLE -> {
                Button(onClick = ::refresh) { Text("Refresh steps") }
                state.checked?.let { Text("Checked ${it.atZone(ZoneId.of(state.zone)).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))} · ${state.zone}", style = MaterialTheme.typography.bodySmall) }
                if (state.days.all { it.total.count == null }) {
                    Text("No data yet. Enable step sharing in your fitness app, then refresh here.")
                    TextButton(onClick = { open(HealthConnectClient.getHealthConnectManageDataIntent(context)) }) { Text("Check shared data") }
                }
                state.days.forEachIndexed { index, day ->
                    Text(if (index == 0) "Today" else day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleLarge)
                    Text(day.total.count?.let { "${java.text.NumberFormat.getIntegerInstance().format(it)} steps" } ?: "No steps shared for this day", style = MaterialTheme.typography.headlineSmall)
                    if (day.total.sources.isNotEmpty()) Text("Contributing apps: " + day.total.sources.joinToString(), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
            }
        }
        Text("Health Connect combines overlapping step records using your source priorities. Each refresh replaces the displayed totals.", style = MaterialTheme.typography.bodySmall)
        Text("Missing steps? In your fitness app, enable sharing steps with Health Connect. Then check app permissions and data-source priorities in Health Connect.")
        TextButton(onClick = { open(HealthConnectClient.getHealthConnectManageDataIntent(context)) }) { Text("Open Health Connect") }
        TextButton(onClick = { open(Intent(context, HealthPrivacyActivity::class.java)) }) { Text("How your steps are used") }
        if (openError) Text("That screen couldn't open. Search for Health Connect in your phone's Settings.")
    }
}
