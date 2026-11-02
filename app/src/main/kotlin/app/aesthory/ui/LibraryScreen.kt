package app.aesthory.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aesthory.library.*
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryScreen(model: MainViewModel, onBack: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    val products by model.products.collectAsStateWithLifecycle()
    val references by model.references.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("products") }
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var referenceId by rememberSaveable { mutableStateOf<String?>(null) }
    var edit by rememberSaveable { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.importReference(uri) }
    LaunchedEffect(state.referenceImport) {
        if (state.referenceImport != null) { referenceId = "new"; edit = true; productId = null }
    }
    fun closeItem() { productId = null; referenceId = null; edit = false; model.clearReferenceImport() }
    if (productId != null) {
        val product = products.find { it.id == productId }
        BackHandler { closeItem() }
        if (edit) ProductEditor(model, product) { closeItem() }
        else if (product != null) ProductDetail(model, product, onBack = { closeItem() }, onEdit = { edit = true })
        return
    }
    if (referenceId != null) {
        val reference = references.find { it.id == referenceId }
        BackHandler { closeItem() }
        if (edit) ReferenceEditor(model, reference, state.referenceImport.orEmpty()) { closeItem() }
        else if (reference != null) ReferenceDetail(model, reference, onBack = { closeItem() }, onEdit = { edit = true })
        return
    }
    BackHandler(onBack = onBack)
    val shownProducts = products.filter { it.archived == archived && (it.name + " " + it.brand + " " + it.kind + " " + it.notes).contains(query, true) }
    val shownReferences = references.filter { it.archived == archived && (it.title + " " + it.content + " " + it.url).contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Your everyday toolkit.", style = MaterialTheme.typography.displaySmall)
            Text("Keep what you use and what helps you here. Link it to an action whenever you need it.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tab == "products", { tab = "products"; query = "" }, label = { Text("Products") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(tab == "references", { tab = "references"; query = "" }, label = { Text("Notes & links") }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        item { OutlinedTextField(query, { query = it.take(200) }, label = { Text("Search your library") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Button(enabled = !state.busy, onClick = { edit = true; if (tab == "products") productId = "new" else referenceId = "new" }) { Text(if (tab == "products") "Add a product" else "Add a note or link") }
            if (tab == "references") TextButton(enabled = !state.busy, onClick = { import.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")) }) { Text("Bring in a text file") }
            SelectRow("Show archived items", archived) { archived = !archived }
        }
        if ((tab == "products" && shownProducts.isEmpty()) || (tab == "references" && shownReferences.isEmpty())) item {
            Text(if (query.isNotBlank()) "No matches. Try another word." else if (archived) "No archived items here." else if (tab == "products") "Start with a product you already use. A name is enough." else "Save a useful note, a source link or a text/Markdown file. PDF and image attachments are coming later.")
        }
        if (tab == "products") items(shownProducts, key = { it.id }) { product ->
            ListItem(headlineContent = { Text(product.name) }, supportingContent = { Text(listOf(product.brand, product.kind, product.stock.label).filter { it.isNotBlank() }.joinToString(" · ")) },
                trailingContent = { TextButton(onClick = { productId = product.id; edit = false }) { Text("Open") } })
        } else items(shownReferences, key = { it.id }) { reference ->
            ListItem(headlineContent = { Text(reference.title) }, supportingContent = { Text(if (reference.url.isNotBlank()) reference.url else reference.content.take(100), maxLines = 2) },
                trailingContent = { TextButton(onClick = { referenceId = reference.id; edit = false }) { Text("Open") } })
        }
    }
}

@Composable
private fun ProductDetail(model: MainViewModel, product: ProductEntity, onBack: () -> Unit, onEdit: () -> Unit) {
    val observations by remember(product.id) { model.observationsFor(product.id) }.collectAsStateWithLifecycle(emptyList())
    val state by model.ui.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Page {
        TextButton(onClick = onBack) { Text("Back to library") }
        Text(product.name, style = MaterialTheme.typography.displaySmall)
        Text(listOf(product.brand, product.kind, product.size, product.stock.label).filter { it.isNotBlank() }.joinToString(" · "))
        if (product.notes.isNotBlank()) Text(product.notes)
        if (product.labelNotes.isNotBlank()) { SectionHeading("From the label"); Text(product.labelNotes) }
        product.openedDay?.let { Text("Opened: " + LocalDate.ofEpochDay(it)) }
        product.expiryDay?.let { Text("Label expiry: " + LocalDate.ofEpochDay(it)) }
        product.periodMonths?.let { Text("Label period after opening: " + it + " months") }
        if (product.openedDay != null && product.periodMonths != null) Text("Opening date + label period: " + LocalDate.ofEpochDay(product.openedDay).plusMonths(product.periodMonths.toLong()), style = MaterialTheme.typography.bodySmall)
        if (product.expiryDay != null || product.periodMonths != null) Text("These dates come from your entries. They aren't a check of the product's condition or safety.", style = MaterialTheme.typography.bodySmall)
        WebLink(product.url, model)
        Button(enabled = !state.busy, onClick = onEdit) { Text("Edit product") }
        TextButton(enabled = !state.busy, onClick = { model.archiveProduct(product.id, !product.archived) }) { Text(if (product.archived) "Bring product back" else "Archive product") }
        Text("Archiving hides it from new choices. Linked instructions and past notes stay saved.", style = MaterialTheme.typography.bodySmall)
        SectionHeading("What you've noticed")
        Text("Your latest 100 dated observations. They don't establish what caused a change. All notes stay saved.")
        FilledTonalButton(enabled = !state.busy, onClick = { adding = true }) { Text("Add an observation") }
        observations.filter { it.productId == product.id }.forEach { item ->
            Text(LocalDate.ofEpochDay(item.day).toString() + " · " + item.productName, style = MaterialTheme.typography.titleSmall)
            Text(item.note)
        }
    }
    if (adding) {
        var note by rememberSaveable { mutableStateOf("") }
        var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("What did you notice?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateChoice("Date", day) { day = it }
                OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Your observation") }, maxLines = 8, supportingText = { Text("Saved as a dated note. It stays as written.") })
            } },
            confirmButton = { TextButton(enabled = note.isNotBlank() && !state.busy, onClick = { model.addObservation(product.id, LocalDate.parse(day), note) { adding = false } }) { Text("Save note") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductEditor(model: MainViewModel, old: ProductEntity?, onBack: () -> Unit) {
    EditorFocus(model)
    val state by model.ui.collectAsStateWithLifecycle()
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(old?.name.orEmpty()) }
    var brand by rememberSaveable { mutableStateOf(old?.brand.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(old?.kind.orEmpty()) }
    var size by rememberSaveable { mutableStateOf(old?.size.orEmpty()) }
    var label by rememberSaveable { mutableStateOf(old?.labelNotes.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(old?.notes.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(old?.url.orEmpty()) }
    var opened by rememberSaveable { mutableStateOf(old?.openedDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var expiry by rememberSaveable { mutableStateOf(old?.expiryDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var period by rememberSaveable { mutableStateOf(old?.periodMonths?.toString().orEmpty()) }
    var stock by rememberSaveable { mutableStateOf(old?.stock ?: StockState.READY) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val dirty = name != old?.name.orEmpty() || brand != old?.brand.orEmpty() || kind != old?.kind.orEmpty() || size != old?.size.orEmpty() || label != old?.labelNotes.orEmpty() || notes != old?.notes.orEmpty() ||
        url != old?.url.orEmpty() || opened != old?.openedDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty() || expiry != old?.expiryDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty() || period != old?.periodMonths?.toString().orEmpty() || stock != (old?.stock ?: StockState.READY)
    fun back() { if (dirty) leaving = true else onBack() }
    BackHandler { back() }
    val badDates = opened.isNotBlank() && expiry.isNotBlank() && expiry < opened
    val badPeriod = period.isNotBlank() && period.toIntOrNull()?.let { it in 1..120 } != true
    Page {
        TextButton(onClick = { back() }) { Text("Back to library") }
        Text(if (old == null) "Keep your go-tos here." else "Update your product.", style = MaterialTheme.typography.displaySmall)
        Text("A name is all you need. Add the rest when it helps.")
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Product name") }, isError = tried && name.isBlank(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(brand, { brand = it.take(120) }, label = { Text("Brand (optional)") }, modifier = Modifier.fillMaxWidth())
        EditorSection("Product details", "Type, size, label and your own notes") {
            OutlinedTextField(kind, { kind = it.take(80) }, label = { Text("Type, such as cleanser or shampoo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(size, { size = it.take(120) }, label = { Text("Size or amount (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(label, { label = it.take(8000) }, label = { Text("Ingredients or instructions from the label") }, maxLines = 8, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(8000) }, label = { Text("Your notes") }, maxLines = 8, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it.take(2048) }, label = { Text("Product link (optional)") }, supportingText = { Text("Use a full https:// or http:// link.") }, isError = !safeWebUrl(url.trim()), modifier = Modifier.fillMaxWidth())
        }
        EditorSection("Dates & stock", stock.label) {
            DateChoice("Opened", opened, optional = true) { opened = it }
            DateChoice("Label expiry", expiry, optional = true) { expiry = it }
            NumberField(period, { period = it.take(3) }, "Months after opening, from the label (optional)")
            if (badDates) Text("Expiry must be on or after the opening date.", color = MaterialTheme.colorScheme.error)
            if (badPeriod) Text("Enter whole months from 1 to 120, or leave it empty.", color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { StockState.entries.forEach { value ->
                FilterChip(stock == value, { stock = value }, label = { Text(value.label) }, modifier = Modifier.heightIn(min = 48.dp))
            } }
        }
        if (tried && (name.isBlank() || !safeWebUrl(url.trim()) || badDates || badPeriod)) Text("Add a name and check the link, dates and months above.", color = MaterialTheme.colorScheme.error)
        Button(enabled = !state.busy, onClick = {
            tried = true
            if (name.isNotBlank() && safeWebUrl(url.trim()) && !badDates && !badPeriod)
                model.saveProduct(ProductEntity(id, name.trim(), brand.trim(), kind.trim(), size.trim(), label.trim(), notes.trim(), url.trim(),
                    opened.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }, expiry.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }, period.toIntOrNull(), stock, old?.archived ?: false), onBack)
        }, modifier = Modifier.fillMaxWidth()) { Text("Save product") }
    }
    UnsavedDialog(leaving, { leaving = false }, onBack)
}

@Composable
private fun ReferenceEditor(model: MainViewModel, old: ReferenceEntity?, imported: String, onBack: () -> Unit) {
    EditorFocus(model)
    val state by model.ui.collectAsStateWithLifecycle()
    val id = rememberSaveable { old?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(old?.title.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(old?.content ?: imported) }
    var url by rememberSaveable { mutableStateOf(old?.url.orEmpty()) }
    var day by rememberSaveable { mutableStateOf(old?.sourceDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val dirty = title != old?.title.orEmpty() || content != old?.content.orEmpty() || url != old?.url.orEmpty() || day != old?.sourceDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()
    fun back() { if (dirty) leaving = true else onBack() }
    BackHandler { back() }
    val valid = title.isNotBlank() && (content.isNotBlank() || url.isNotBlank()) && safeWebUrl(url.trim())
    Page {
        TextButton(onClick = { back() }) { Text("Back to library") }
        Text("Keep a useful reference.", style = MaterialTheme.typography.displaySmall)
        Text(if (imported.isNotBlank()) "Review the imported text before saving. Nothing has been added yet." else "A note, a source link, or both. Only you can see it here.")
        OutlinedTextField(title, { title = it.take(120) }, label = { Text("Give it a title") }, isError = tried && title.isBlank(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(content, { content = it.take(32000) }, label = { Text("Your note or reference text") }, minLines = 4, maxLines = 12, supportingText = { Text(content.length.toString() + "/32,000 · Plain text; Markdown stays readable as written.") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it.take(2048) }, label = { Text("Source link (optional)") }, isError = !safeWebUrl(url.trim()), supportingText = { Text("Use a full https:// or http:// link. Links open in your browser.") }, modifier = Modifier.fillMaxWidth())
        DateChoice("Source date", day, optional = true) { day = it }
        if (tried && !valid) Text("Add a title and either some text or a valid web link.", color = MaterialTheme.colorScheme.error)
        Button(enabled = !state.busy, onClick = {
            tried = true
            if (valid) model.saveReference(ReferenceEntity(id, title.trim(), content.trim(), url.trim(), day.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }, old?.archived ?: false), onBack)
        }, modifier = Modifier.fillMaxWidth()) { Text("Save reference") }
    }
    UnsavedDialog(leaving, { leaving = false }, onBack)
}

@Composable
private fun ReferenceDetail(model: MainViewModel, reference: ReferenceEntity, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    Page {
        TextButton(onClick = onBack) { Text("Back to library") }
        Text(reference.title, style = MaterialTheme.typography.displaySmall)
        reference.sourceDay?.let { Text("Source date: " + LocalDate.ofEpochDay(it)) }
        if (reference.content.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(reference.content) }
        WebLink(reference.url, model)
        Button(enabled = !state.busy, onClick = onEdit) { Text("Edit reference") }
        TextButton(enabled = !state.busy, onClick = { model.archiveReference(reference.id, !reference.archived) }) { Text(if (reference.archived) "Bring reference back" else "Archive reference") }
        Text("Archiving hides it from new choices. Past linked instructions keep their saved copy.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun WebLink(url: String, model: MainViewModel) {
    if (url.isBlank()) return
    val handler = LocalUriHandler.current
    TextButton(onClick = { if (safeWebUrl(url)) runCatching { handler.openUri(url) }.onFailure { model.notifyUser("That link couldn't be opened. Check that a browser is available.") } }) { Text("Open source link") }
}

@Composable
internal fun UnsavedDialog(show: Boolean, dismiss: () -> Unit, discard: () -> Unit) {
    if (show) AlertDialog(onDismissRequest = dismiss, title = { Text("Leave without saving?") }, text = { Text("Your changes haven't been saved.") },
        confirmButton = { TextButton(onClick = discard) { Text("Discard changes") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Keep editing") } })
}
