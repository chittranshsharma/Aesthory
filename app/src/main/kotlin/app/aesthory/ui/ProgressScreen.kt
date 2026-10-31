package app.aesthory.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.progress.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

@Composable
internal fun ProgressScreen(model: MainViewModel, onBack: () -> Unit) {
    val photos by model.progressPhotos.collectAsStateWithLifecycle()
    val state by model.ui.collectAsStateWithLifecycle()
    var bin by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProgressPhoto?>(null) }
    val visible = photos.filter { (it.deletedAtMillis != null) == bin }
    val chosen = photos.filter { it.id in selected && it.deletedAtMillis == null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.beginPhoto(uri) }
    LaunchedEffect(photos) {
        selected = selected.filter { id -> photos.any { it.id == id && it.deletedAtMillis == null } }
        if (opened != null && visible.none { it.id == opened }) opened = null
    }
    if (comparing && chosen.size == 2) {
        BackHandler { comparing = false }
        ComparePhotos(model, comparisonPair(chosen)) { comparing = false }
        return
    }
    val detail = visible.find { it.id == opened }
    if (detail != null) {
        BackHandler { opened = null }
        Page {
            TextButton(onClick = { opened = null }) { Text("Back to photos") }
            Text(photoDate(detail.day), style = MaterialTheme.typography.headlineLarge)
            Text("${detail.area.label} · ${detail.view.label}")
            PhotoImage(model, detail, false, Modifier.fillMaxWidth().height(340.dp))
            if (detail.note.isNotBlank()) Text(detail.note)
            if (bin) {
                Button(enabled = !state.busy, onClick = { model.restorePhoto(detail.id); opened = null }) { Text("Bring photo back") }
                TextButton(enabled = !state.busy, onClick = { deleting = detail }) { Text("Delete permanently") }
            } else {
                Button(enabled = !state.busy, onClick = { model.editPhoto(detail) }) { Text("Edit date & notes") }
                TextButton(enabled = !state.busy, onClick = { model.binPhoto(detail.id); opened = null }) { Text("Move to Recently deleted") }
            }
        }
    } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to you") }
            Text("See your progress.", style = MaterialTheme.typography.displaySmall)
            Text("A private photo journal. Notice what you prefer, at your own pace.")
        }
        item { Button(enabled = !state.busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Add a photo") } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !bin, onClick = { bin = false; selected = emptyList() }, label = { Text("My photos") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = bin, onClick = { bin = true; selected = emptyList() }, label = { Text("Recently deleted") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            if (bin) Text("Photos stay here until you bring them back or delete them permanently. They're included in full backups.", style = MaterialTheme.typography.bodySmall)
            else {
                Text("Choose two photos from the same area and view to compare.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(enabled = chosen.size == 2, onClick = { comparing = true }) { Text("Compare ${chosen.size}/2") }
            }
        }
        if (visible.isEmpty()) item { Text(if (bin) "Nothing in Recently deleted." else "Start with one photo. Add a date and a short note if you like—no analysis or AI needed.") }
        items(visible, key = { it.id }) { photo ->
            Card(onClick = { opened = photo.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(photoDate(photo.day), style = MaterialTheme.typography.titleMedium); Text("${photo.area.label} · ${photo.view.label}", style = MaterialTheme.typography.bodySmall) }
                        if (!bin) {
                            val matching = chosen.isEmpty() || chosen.first().let { it.area == photo.area && it.view == photo.view }
                            Checkbox(checked = photo.id in selected, enabled = photo.id in selected || (selected.size < 2 && matching),
                                modifier = Modifier.semantics { contentDescription = "Select ${photo.area.label}, ${photo.view.label}, ${LocalDate.ofEpochDay(photo.day)} for comparison" },
                                onCheckedChange = { selected = if (it) selected + photo.id else selected - photo.id })
                        }
                    }
                    PhotoImage(model, photo, true, Modifier.fillMaxWidth().height(180.dp))
                    if (photo.note.isNotBlank()) Text(photo.note, maxLines = 3)
                    Text("Open photo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight())
                }
            }
        }
    }
    deleting?.let { photo -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this photo permanently?") },
        text = { Text("This removes the photo from this app. It won't remove copies in backups you've already saved.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { model.deletePhoto(photo.id); deleting = null; opened = null }) { Text("Delete photo") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } }) }
}

@Composable
private fun ComparePhotos(model: MainViewModel, pair: List<ProgressPhoto>, onBack: () -> Unit) {
    var later by rememberSaveable(pair.map { it.id }) { mutableStateOf(false) }
    val photo = pair[if (later) 1 else 0]
    Page {
        TextButton(onClick = onBack) { Text("Back to photos") }
        Text("Then & now.", style = MaterialTheme.typography.displaySmall)
        Text("${photo.area.label} · ${photo.view.label}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !later, onClick = { later = false }, label = { Text("Earlier") }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = later, onClick = { later = true }, label = { Text("Later") }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Text(photoDate(photo.day), style = MaterialTheme.typography.titleLarge)
        PhotoImage(model, photo, false, Modifier.fillMaxWidth().height(340.dp))
        if (photo.note.isNotBlank()) Text(photo.note)
        Text("For a fair comparison, use similar lighting, distance and expression. A changed angle or shadow can change how a photo looks.")
    }
}

@Composable
private fun PhotoImage(model: MainViewModel, photo: ProgressPhoto, small: Boolean, modifier: Modifier) {
    key(photo.fileKey, small) {
    val result by produceState<Result<Bitmap>?>(null, photo.fileKey, small) {
        value = try { Result.success(model.readPhoto(photo.fileKey, small)) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { Result.failure(failure) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = result?.getOrNull()
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "${photo.area.label}, ${photo.view.label}, ${LocalDate.ofEpochDay(photo.day)}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else if (result == null) CircularProgressIndicator()
        else Text("This photo couldn't be opened. A complete backup can help recover it.")
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoDetailsDialog(model: MainViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    if (state.photoSource == null && !state.saveAnalyzedPhoto && state.editingPhoto == null) return
    val old = state.editingPhoto
    var dayText by rememberSaveable(old?.id, state.photoSource, state.saveAnalyzedPhoto) { mutableStateOf(LocalDate.ofEpochDay(old?.day ?: LocalDate.now().toEpochDay()).toString()) }
    var areaName by rememberSaveable(old?.id, state.photoSource) { mutableStateOf(old?.area?.name ?: PhotoArea.FACE.name) }
    var viewName by rememberSaveable(old?.id, state.photoSource) { mutableStateOf(old?.view?.name ?: PhotoView.FRONT.name) }
    var note by rememberSaveable(old?.id, state.photoSource) { mutableStateOf(old?.note.orEmpty()) }
    val day = LocalDate.parse(dayText)
    val context = LocalContext.current
    val previewResult by produceState<Result<Bitmap?>?>(null, old?.id, state.photoSource, state.saveAnalyzedPhoto) {
        value = try { Result.success(model.photoDraftPreview()) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { Result.failure(failure) }
    }
    val preview = previewResult?.getOrNull()
    AlertDialog(onDismissRequest = { if (!state.busy) model.dismissPhoto() }, title = { Text(if (old == null) "Keep this progress photo" else "Edit photo details") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            preview?.let { Image(it.asImageBitmap(), contentDescription = "Selected progress photo", modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit) }
            if (previewResult?.isFailure == true) Text("That photo couldn't be opened. Choose a still photo under 50 MB.", color = MaterialTheme.colorScheme.error)
            if (old == null) Text("Saves a private copy in Aesthory. Your gallery original stays as it is.")
            TextButton(enabled = !state.busy, onClick = {
                DatePickerDialog(context, { _, year, month, date -> dayText = LocalDate.of(year, month + 1, date).toString() }, day.year, day.monthValue - 1, day.dayOfMonth).show()
            }) { Text("Date: ${photoDate(day.toEpochDay())}") }
            Text("What are you tracking?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoArea.entries.forEach { area -> FilterChip(selected = areaName == area.name, onClick = { areaName = area.name }, label = { Text(area.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            Text("Photo view", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoView.entries.forEach { view -> FilterChip(selected = viewName == view.name, onClick = { viewName = view.name }, label = { Text(view.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            OutlinedTextField(note, { if (it.length <= 500) note = it }, label = { Text("Note (optional)") }, supportingText = { Text("${note.length}/500") })
            if (!state.busy) state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !state.busy && (old != null || preview != null), onClick = { model.saveProgressPhoto(day, PhotoArea.valueOf(areaName), PhotoView.valueOf(viewName), note) }) { Text(if (old == null) "Save photo" else "Save changes") } },
        dismissButton = { TextButton(enabled = !state.busy, onClick = model::dismissPhoto) { Text("Not now") } })
}

@Composable
private fun photoDate(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
