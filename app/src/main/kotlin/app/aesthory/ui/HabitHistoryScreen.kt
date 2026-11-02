package app.aesthory.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.data.HistoryEntry
import app.aesthory.library.HabitLinks
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.TrackingKind
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

@Composable
internal fun LinkedHabitDetails(model: MainViewModel, id: String, version: Int) {
    val links by remember(id, version) { model.habitLinks(id, version) }.collectAsStateWithLifecycle(null)
    links?.let { SavedLinks(it, model) }
}

@Composable
private fun SavedLinks(links: HabitLinks, model: MainViewModel) {
    if (links.products.isEmpty() && links.references.isEmpty()) return
    EditorSection("Saved products & references", (links.products.size + links.references.size).toString() + " linked") {
        links.products.forEach { product ->
            Text(listOf(product.name, product.brand).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
            if (product.usageNote.isNotBlank()) Text(product.usageNote)
            if (product.labelNotes.isNotBlank()) Text("Label notes: " + product.labelNotes)
        }
        links.references.forEach { reference ->
            var open by rememberSaveable(reference.referenceId) { mutableStateOf(false) }
            TextButton(onClick = { open = !open }) { Text(reference.title + if (open) " · Close" else " · Read") }
            if (open) {
                reference.sourceDay?.let { Text("Source date: " + LocalDate.ofEpochDay(it)) }
                if (reference.content.isNotBlank()) SelectionContainer { Text(reference.content) }
                WebLink(reference.url, model)
            }
        }
    }
}

@Composable
internal fun HabitHistoryScreen(model: MainViewModel, id: String, onBack: () -> Unit) {
    val result by produceState<Result<List<HistoryEntry>>?>(null, id) {
        value = try { Result.success(model.historyDetails(id)) } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { Result.failure(failure) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back to routines") }
            Text("Your past check-ins.", style = MaterialTheme.typography.displaySmall)
            Text("The latest 200 records, including skips and undo. Each keeps the instructions used when you logged it.")
        }
        if (result == null) item { CircularProgressIndicator() }
        else if (result?.isFailure == true) item { Text("These check-ins couldn't be opened. Go back and try again.") }
        else if (result?.getOrNull().isNullOrEmpty()) item { Text("No check-ins yet. Your first one will appear here.") }
        items(result?.getOrNull().orEmpty(), key = { it.event.id }) { entry ->
            var open by rememberSaveable(entry.event.id) { mutableStateOf(false) }
            val event = entry.event
            val definition = entry.definition
            Text(LocalDate.ofEpochDay(event.dateDay).toString() + " · " + when (event.status) {
                CompletionStatus.DONE -> "Completed"; CompletionStatus.PARTIAL -> "Some done"; CompletionStatus.SKIPPED -> "Skipped"; CompletionStatus.UNDONE -> "Undone"
            }, style = MaterialTheme.typography.titleMedium)
            Text(definition.title)
            event.value?.let { value ->
                Text(value.toString() + when (definition.tracking.kind) {
                    TrackingKind.DURATION -> " seconds"; TrackingKind.RATING -> " out of " + definition.tracking.target?.toInt()
                    else -> " " + definition.tracking.unit.orEmpty()
                })
            }
            if (event.checkedItems.isNotEmpty()) Text(event.checkedItems.joinToString("\n") { definition.tracking.items[it] })
            event.timedSeconds?.let { seconds -> Text("Timer: " + app.aesthory.timing.formatTimer(seconds * 1000), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { open = !open }) { Text(if (open) "Hide saved instructions" else "See saved instructions") }
            if (open) {
                Text(definition.instructions.ifBlank { "No instructions added." })
                if (definition.purpose.isNotBlank()) Text("Why: " + definition.purpose)
                if (definition.shortInstructions.isNotBlank()) Text("Busy-day version: " + definition.shortInstructions)
                SavedLinks(entry.links, model)
            }
            HorizontalDivider()
        }
    }
}
