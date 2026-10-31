package app.aesthory.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aesthory.coaching.GroqConnection
import app.aesthory.coaching.OfflineCoach
import app.aesthory.coaching.StarterRoutine
import app.aesthory.data.AesthoryRepository
import app.aesthory.data.HabitDraft
import app.aesthory.data.ProfileEntity
import app.aesthory.face.AnalyzedPhoto
import app.aesthory.face.FaceReview
import app.aesthory.face.LocalFaceAnalyzer
import app.aesthory.face.PresentationGoal
import app.aesthory.profile.ProfileBaseline
import app.aesthory.routines.CompletionInput
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.HabitSchedule
import app.aesthory.routines.RoutineFiles
import app.aesthory.routines.RoutinePack
import app.aesthory.security.GroqKeyStore
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppState(
    val loaded: Boolean = false,
    val profile: ProfileEntity? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val photo: AnalyzedPhoto? = null,
    val offlineText: String? = null,
    val modelInstalled: Boolean = false,
    val keyConfigured: Boolean = false,
    val packToShare: RoutinePack? = null,
    val packToImport: RoutinePack? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AesthoryRepository,
    private val faces: LocalFaceAnalyzer,
    private val offline: OfflineCoach,
    private val keys: GroqKeyStore,
    private val groq: GroqConnection,
    private val routineFiles: RoutineFiles,
) : ViewModel() {
    private val state = MutableStateFlow(AppState())
    val ui = state.asStateFlow()
    val routines = repository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val measurements = repository.measurements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val date = MutableStateFlow(LocalDate.now())
    val today = date.flatMapLatest(repository::today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var work: Job? = null

    init {
        viewModelScope.launch { repository.profile.collect { profile -> state.update { it.copy(loaded = true, profile = profile) } } }
        refreshCredentials()
    }

    fun refreshDate() { date.value = LocalDate.now() }
    fun clearMessage() { state.update { it.copy(message = null) } }
    fun saveProfile(profile: ProfileBaseline, onSaved: () -> Unit = {}) = action {
        repository.saveProfile(profile)
        state.update { it.copy(message = "You're all set. Let's make it your own.") }
        onSaved()
    }
    fun createRoutine(name: String, onSaved: () -> Unit) = action { repository.createRoutine(name); onSaved() }
    fun addStarter(starter: StarterRoutine) = action {
        repository.addStarter(starter, date.value)
        state.update { it.copy(message = "${starter.name} is ready in your routines.") }
    }
    fun archiveRoutine(id: String) = action { repository.archiveRoutine(id) }
    fun setHabitState(id: String, habitState: app.aesthory.routines.HabitState) = action { repository.setHabitState(id, habitState) }
    fun prepareShare(id: String) = action("That routine couldn't be prepared. Try a smaller routine with at least one action.") {
        val pack = repository.exportRoutine(id)
        state.update { it.copy(packToShare = pack) }
    }
    fun writePack(uri: Uri) = action("The routine file couldn't be saved. Choose a writable folder and try again.") {
        routineFiles.write(uri, requireNotNull(state.value.packToShare))
        state.update { it.copy(packToShare = null, message = "Routine file saved. You can send it using your Files app.") }
    }
    fun previewPack(uri: Uri) = action("That isn't a supported Aesthory routine file. Choose an exported routine file under 256 KB.") {
        val pack = routineFiles.read(uri)
        state.update { it.copy(packToImport = pack) }
    }
    fun importPack() = action("That routine couldn't be added. Your existing routines are still saved.") {
        repository.importRoutine(requireNotNull(state.value.packToImport))
        state.update { it.copy(packToImport = null, message = "Routine ready. Re-importing the same file won't add it twice.") }
    }
    fun dismissPacks() { state.update { it.copy(packToShare = null, packToImport = null) } }
    fun addHabit(routineId: String, title: String, instructions: String, minutes: Int?, onSaved: () -> Unit) = action {
        val category = repository.categories.first().firstOrNull()?.id
            ?: repository.createCategory("General")
        repository.addHabit(routineId, HabitDraft(title, category, HabitSchedule(LocalDate.now()), instructions, minutes))
        onSaved()
    }
    fun habits(routineId: String) = repository.habits(routineId)
    fun logHabit(id: String, input: CompletionInput, onSaved: () -> Unit = {}) = action {
        repository.recordCompletion(id, date.value, input); onSaved()
    }
    fun undo(id: String) = action { repository.undoCompletion(id, date.value) }

    fun analyze(uri: Uri, goal: PresentationGoal) = action {
        state.update { it.copy(photo = null, offlineText = null) }
        val result = faces.analyze(uri, goal)
        state.update { it.copy(photo = result) }
    }
    fun clearAnalysis() { if (!state.value.busy) state.update { it.copy(photo = null, offlineText = null) } }
    fun generateOffline(goal: PresentationGoal) = action {
        val report = state.value.photo?.review as? FaceReview.Measured ?: error("Analyze a suitable photo first.")
        state.update { it.copy(offlineText = null) }
        val answer = offline.review(report, goal)
        state.update { it.copy(offlineText = answer) }
    }
    fun importModel(uri: Uri) = action {
        offline.importModel(uri)
        state.update { it.copy(modelInstalled = true, message = "Local model imported and initialized on this device. Review generated advice before using it.") }
    }
    fun removeModel() = action { offline.removeModel(); state.update { it.copy(modelInstalled = false, offlineText = null) } }
    fun saveKey(value: String, onSaved: () -> Unit) = action {
        withContext(Dispatchers.IO) { keys.save(value) }
        state.update { it.copy(keyConfigured = true, message = "Key saved encrypted on this device.") }
        onSaved()
    }
    fun deleteKey() = action {
        withContext(Dispatchers.IO) { keys.delete() }
        state.update { it.copy(keyConfigured = false, message = "API key removed.") }
    }
    fun testKey() = action {
        val models = groq.test()
        state.update { it.copy(message = "Connection accepted: ${models.size} models listed. Check your Free plan and limits in Groq; this does not verify billing or image quality.") }
    }
    fun cancelWork() { work?.cancel() }

    private fun refreshCredentials() {
        viewModelScope.launch {
            val configured = withContext(Dispatchers.IO) { keys.isConfigured() }
            val installed = withContext(Dispatchers.IO) { offline.isInstalled() }
            state.update { it.copy(keyConfigured = configured, modelInstalled = installed) }
        }
    }

    private fun action(errorMessage: String? = null, block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        work = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val message = errorMessage ?: when (failure) {
                    is IllegalArgumentException -> "Check the entered values or selected file and try again."
                    is java.io.IOException -> "The file or connection could not be read. Your saved records are unchanged."
                    else -> "We couldn't finish that. Check what you entered or try again in a moment."
                }
                state.update { it.copy(message = message) }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
}
