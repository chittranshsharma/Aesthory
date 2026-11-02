package app.aesthory.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import app.aesthory.backup.BackupSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun BackupScreen(model: MainViewModel, backLabel: String = "Back to you", onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val lastBackup by model.lastBackupMillis.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) model.saveBackup(uri) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.chooseRestore(uri) }
    Page {
        TextButton(onClick = onBack) { Text(backLabel) }
        Text("Keep your progress safe.", style = MaterialTheme.typography.displaySmall)
        Text("Save a password-protected copy of your records and photos. You choose where the file goes.")
        if (state.restoreComplete) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your backup is restored.", style = MaterialTheme.typography.headlineMedium)
                    Text("Your saved records and photos are ready. Your AI key and extra model files stay as they were on this phone.")
                }
            }
        }
        if (state.profile != null) {
        SectionHeading("Make a backup")
        Text("Includes your profile, measurements, routines, check-ins, products, references and photos—including Recently deleted. API keys and extra AI model files are left out.")
        lastBackup?.let { Text("Last saved on this phone: ${backupDate(it)}", style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !state.busy, onClick = { creating = true }) { Text("Make a backup") }
        state.backup?.let { ready ->
            SectionHeading("Your file is ready")
            BackupCounts(ready.summary)
            Button(enabled = !state.busy, onClick = { save.launch("Aesthory-backup.aesthory") }) { Text("Choose where to save") }
            TextButton(enabled = !state.busy, onClick = model::discardBackup) { Text("Discard this prepared file") }
        }
        HorizontalDivider()
        }
        SectionHeading("Restore a backup", "Moving phones or recovering your progress?")
        Text("Choose an Aesthory backup and enter its password. We'll check the file before asking you to replace anything.")
        OutlinedButton(enabled = !state.busy, onClick = { open.launch(arrayOf("*/*")) }) { Text("Choose a backup") }
        SectionHeading("Remember your password")
        Text("We don't keep a copy of it. If you forget it, we can't unlock that backup. Keep the password somewhere safe, separate from the file.")
        Text("Backups support up to 512 MB in this version. Uninstalling removes data from the app; a backup you saved elsewhere remains available.", style = MaterialTheme.typography.bodySmall)
    }
    if (creating || (state.restoreSource != null && !state.busy)) {
        val isNew = creating
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val valid = password.length in 12..256 && (!isNew || password == confirm)
        AlertDialog(onDismissRequest = { if (!state.busy) { creating = false; model.dismissRestorePassword() } },
            title = { Text(if (isNew) "Choose a backup password" else "Unlock your backup") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isNew) "Use at least 12 characters. You'll need this exact password to restore the file." else "Enter the password used when this backup was made. Your current data won't change while we check it.")
                OutlinedTextField(password, { if (it.length <= 256) password = it }, label = { Text("Backup password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), supportingText = { Text("At least 12 characters") })
                if (isNew) OutlinedTextField(confirm, { if (it.length <= 256) confirm = it }, label = { Text("Enter it again") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = confirm.isNotEmpty() && confirm != password)
                if (isNew && confirm.isNotEmpty() && confirm != password) Text("Those passwords don't match.", color = MaterialTheme.colorScheme.error)
                if (!isNew && !state.busy) state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = valid && !state.busy, onClick = {
                val copy = password.toCharArray(); password = ""; confirm = ""
                if (isNew) { creating = false; model.prepareBackup(copy) } else model.checkBackup(copy)
            }) { Text(if (isNew) "Prepare backup" else "Check backup") } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = { password = ""; confirm = ""; creating = false; model.dismissRestorePassword() }) { Text("Not now") } })
    }
    state.restorePreview?.let { preview ->
        AlertDialog(onDismissRequest = { if (!state.busy) model.cancelRestore() }, title = { Text("Restore this backup?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Made ${backupDate(preview.summary.createdAtMillis)}")
                BackupCounts(preview.summary)
                Text("This replaces your current profile, routines, check-ins, measurements, products, references and photos. It won't change your AI key or extra model files.")
                Text("If you need your current records too, save a backup of them first.")
                if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Restoring your records and photos…") }
            } },
            confirmButton = { TextButton(enabled = !state.busy, onClick = model::restoreBackup) { Text("Replace & restore") } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = model::cancelRestore) { Text("Keep current data") } })
    }
}

@Composable
private fun backupDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale))
}
@Composable
private fun BackupCounts(summary: BackupSummary) {
    Text("${summary.routines} routines · ${summary.checkIns} check-ins · ${summary.photos} photos")
    Text("${summary.products} products · ${summary.references} references")
    if (summary.binnedPhotos > 0) Text("${summary.binnedPhotos} of those photos are in Recently deleted.", style = MaterialTheme.typography.bodySmall)
}
