package app.aesthory.ui

import android.net.Uri
import app.aesthory.nutrition.*
import app.aesthory.workouts.*
import java.time.ZoneId
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aesthory.coaching.GroqConnection
import app.aesthory.coaching.OfflineCoach
import app.aesthory.coaching.StarterRoutine
import app.aesthory.data.AesthoryRepository
import app.aesthory.data.HabitDraft
import app.aesthory.library.*
import app.aesthory.timing.*
import kotlinx.coroutines.flow.map
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
import app.aesthory.backup.PersonalVault
import app.aesthory.backup.PreparedBackup
import app.aesthory.backup.RestorePreview
import app.aesthory.progress.*
import android.graphics.Bitmap
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

data class NutritionDraft(val scan: NutritionScan?, val existing: NutritionDay? = null, val token: String = java.util.UUID.randomUUID().toString())

data class WorkoutDraft(val bytes: ByteArray, val zone: String, val preview: WorkoutPreview, val token: String = java.util.UUID.randomUUID().toString())

data class AppState(
    val workoutDraft: WorkoutDraft? = null,
    val nutritionDraft: NutritionDraft? = null,
    val nutritionConflict: NutritionDay? = null,
    val timingStatus: TimingStatus = TimingStatus(false, false, false, false),
    val openTimerRequest: Int = 0,
    val openTodayRequest: Int = 0,
    val timerReviewSession: String? = null,
    val openEditors: Int = 0,
    val referenceImport: String? = null,
    val shareReadable: Boolean = false,
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
    val canCancel: Boolean = true,
    val backup: PreparedBackup? = null,
    val restoreSource: Uri? = null,
    val restorePreview: RestorePreview? = null,
    val restoreComplete: Boolean = false,
    val photoSource: Uri? = null,
    val saveAnalyzedPhoto: Boolean = false,
    val editingPhoto: ProgressPhoto? = null,
    val undoPhotoId: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AesthoryRepository,
    private val faces: LocalFaceAnalyzer,
    private val offline: OfflineCoach,
    private val keys: GroqKeyStore,
    private val groq: GroqConnection,
    private val routineFiles: RoutineFiles,
    private val vault: PersonalVault,
    private val referenceFiles: ReferenceFiles,
    private val timing: TimingController,
    private val nutritionOcr: NutritionOcr,
    private val workoutFiles: WorkoutFiles,
    private val steps: app.aesthory.health.StepsRepository,
) : ViewModel() {
    private val state = MutableStateFlow(AppState())
    val dailySteps = steps.state
    suspend fun refreshSteps() = steps.refresh()
    fun clearStepsView() = steps.clearView()
    val workouts = repository.workouts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun importWorkouts(uri: Uri) = action("Choose an original Hevy workout CSV under 5 MB. Your saved workouts are unchanged.") {
        val bytes = workoutFiles.read(uri)
        prepareWorkouts(bytes, timing.clock.now().zone.id)
    }
    private suspend fun prepareWorkouts(bytes: ByteArray, zone: String) {
        val preview = withContext(Dispatchers.IO) { repository.previewWorkouts(HevyCsv.parse(bytes, ZoneId.of(zone))) }
        state.update { it.copy(workoutDraft = WorkoutDraft(bytes, zone, preview)) }
    }
    fun workoutTimezone(zone: String) = action("Choose a valid time zone, such as Asia/Kolkata or Europe/London.") { prepareWorkouts(requireNotNull(state.value.workoutDraft).bytes, zone.trim()) }
    fun discardWorkouts() { if (!state.value.busy) state.update { it.copy(workoutDraft = null) } }
    fun saveWorkouts(replaceChanged: Boolean) = action {
        val draft = requireNotNull(state.value.workoutDraft)
        val fresh = withContext(Dispatchers.IO) { repository.saveWorkouts(draft.preview, replaceChanged) }
        if (fresh == null) state.update { it.copy(workoutDraft = null, message = "Workouts saved. ${draft.preview.skipped} already-saved workouts skipped.") }
        else state.update { it.copy(workoutDraft = draft.copy(preview = fresh, token = java.util.UUID.randomUUID().toString()), message = "Your saved workouts changed. Review the updated comparison before saving.") }
    }
    val nutrition = repository.nutrition.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun importNutrition(uri: Uri) = action("We couldn't read that image. Choose a PNG, JPEG or WebP screenshot under 20 MB, or enter your totals manually.") {
        val scan = nutritionOcr.scan(uri)
        val duplicate = repository.nutritionImport(scan.hash)
        if (duplicate != null) notifyUser("This screenshot was already saved for ${LocalDate.ofEpochDay(duplicate.day)}. Open that day's entry to edit it.")
        else state.update { it.copy(nutritionDraft = NutritionDraft(scan), nutritionConflict = null) }
    }
    fun manualNutrition(existing: NutritionDay? = null) { if (!state.value.busy) state.update { it.copy(nutritionDraft = NutritionDraft(null, existing), nutritionConflict = null) } }
    fun discardNutrition() { if (!state.value.busy) state.update { it.copy(nutritionDraft = null, nutritionConflict = null) } }
    fun clearNutritionConflict() { state.update { it.copy(nutritionConflict = null) } }
    fun saveNutrition(day: LocalDate, values: NutritionValues, confirmedRevision: String? = null) = action {
        val draft = requireNotNull(state.value.nutritionDraft)
        val now = timing.clock.now()
        require(day <= java.time.Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate())
        val candidate = NutritionDay(day.toEpochDay(), values.calories, values.protein, values.carbs, values.fat, draft.scan?.hash, savedAtMillis = now.wall)
        when (val result = repository.saveNutrition(candidate, confirmedRevision)) {
            NutritionSave.Saved -> state.update { it.copy(nutritionDraft = null, nutritionConflict = null, message = "Daily nutrition saved.") }
            is NutritionSave.Replace -> state.update { it.copy(nutritionConflict = result.existing) }
            is NutritionSave.Duplicate -> state.update { it.copy(nutritionConflict = null, message = "This screenshot was already saved for ${LocalDate.ofEpochDay(result.day)}. Nothing was added.") }
        }
    }
    val ui = state.asStateFlow()
    val routines = repository.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val measurements = repository.measurements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progressPhotos = vault.photos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lastBackupMillis = vault.lastBackupMillis
    val activeTimer = timing.timer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val reminders = timing.reminders.stateIn<List<HabitReminder>?>(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val reminderPreferences = timing.preferences.map { it ?: ReminderPreferences() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderPreferences())
    val reminderActions = repository.reminderActions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun timingNow() = timing.clock.now()
    fun weeklyTarget(day: Long) = repository.weeklyTarget(day)
    fun weeklyLogs(day: Long) = repository.weeklyLogs(day)
    fun saveWeeklyTarget(target: WeeklyTarget, saved: () -> Unit) = action { repository.saveWeeklyTarget(target); saved() }
    suspend fun timerDefinition(timer: ActiveTimer) = repository.definition(timer.habitId, timer.definitionVersion)
    fun refreshTiming() { viewModelScope.launch {
        try { timing.refresh(); state.update { it.copy(timingStatus = timing.status()) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.update { it.copy(message = "Timer and reminder alerts couldn't be refreshed. Your saved timer is still here.") } }
    } }
    fun startTimer(id: String) = action("That timer couldn't start. Try again after checking your saved action.") {
        val now = timing.clock.now()
        timing.start(id, java.time.Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate())
        requestTimer()
    }
    fun pauseTimer(session: String) = action { timing.pause(session) }
    fun resumeTimer(session: String) = action { timing.resume(session) }
    fun discardTimer(session: String) = action { timing.discard(session); state.update { it.copy(timerReviewSession = null) } }
    fun reviewTimer(session: String) = action { timing.pause(session); state.update { it.copy(timerReviewSession = session) } }
    fun dismissTimerReview() { state.update { it.copy(timerReviewSession = null) } }
    fun finishTimer(session: String, seconds: Long, day: LocalDate, input: CompletionInput) = action("That timed check-in couldn't be saved. Check the entries; your timer is still saved.") {
        timing.finish(session, seconds, day, input)
        state.update { it.copy(timerReviewSession = null, message = "Timed check-in saved.") }
    }
    fun checkTimerGoal(session: String) { viewModelScope.launch { try { timing.goalReached(session) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { } } }
    fun saveReminder(rule: HabitReminder, saved: () -> Unit) = action { timing.saveReminder(rule); state.update { it.copy(timingStatus = timing.status()) }; saved() }
    fun saveReminderPreferences(prefs: ReminderPreferences) = action { timing.savePreferences(prefs); state.update { it.copy(timingStatus = timing.status()) } }
    fun testReminder() { notifyUser(if (timing.sendTest()) "Test notification sent. Check your notification shade." else "Notifications couldn't be sent. Check your phone's notification settings.") }
    fun requestTimer() {
        if (state.value.openEditors > 0) notifyUser("Your timer is ready. Save or leave this draft first, then open the timer.")
        else state.update { it.copy(openTimerRequest = it.openTimerRequest + 1) }
    }
    fun requestToday() {
        if (state.value.openEditors > 0) notifyUser("Your reminder is ready. Save or leave this draft first, then open Today.")
        else state.update { it.copy(openTodayRequest = it.openTodayRequest + 1) }
    }
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val products = repository.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val references = repository.references.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun observationsFor(id: String) = repository.observationsFor(id)
    fun habitLinks(id: String, version: Int) = repository.links(id, version)
    fun saveProduct(product: ProductEntity, saved: () -> Unit) = action("That product couldn't be saved. Check the details and try again.") { repository.saveProduct(product); saved() }
    fun saveReference(reference: ReferenceEntity, saved: () -> Unit) = action("That note couldn't be saved. Check the details and try again.") { repository.saveReference(reference); saved() }
    fun archiveProduct(id: String, archived: Boolean) = action { repository.archiveProduct(id, archived) }
    fun archiveReference(id: String, archived: Boolean) = action { repository.archiveReference(id, archived) }
    fun addObservation(id: String, day: LocalDate, note: String, saved: () -> Unit) = action { repository.addObservation(id, day, note); saved() }
    fun importReference(uri: Uri) = action("Choose a UTF-8 text or Markdown file under 128 KB with no more than 32,000 characters. PDF and image files aren't supported here yet.") {
        val content = referenceFiles.read(uri)
        state.update { it.copy(referenceImport = content) }
    }
    fun clearReferenceImport() { state.update { it.copy(referenceImport = null) } }
    fun notifyUser(message: String) { state.update { it.copy(message = message) } }
    fun editorSession(open: Boolean) { state.update { it.copy(openEditors = maxOf(0, it.openEditors + if (open) 1 else -1)) } }
    fun addCategory(name: String, saved: (String) -> Unit) = action {
        val existing = repository.categories.first().find { it.name.equals(name.trim(), ignoreCase = true) }
        require(name.isNotBlank() && name.trim().length <= 80)
        saved(existing?.id ?: repository.createCategory(name))
    }
    fun saveHabitDetails(routineId: String, id: String?, draft: HabitDraft, saved: () -> Unit) = action("That action couldn't be saved. Check your entries and try again.") {
        if (id == null) repository.addHabit(routineId, draft) else repository.editHabit(id, draft)
        timing.refresh()
        saved()
    }
    fun moveHabit(id: String, direction: Int) = action { repository.moveHabit(id, direction) }
    suspend fun historyDetails(id: String) = repository.historyDetails(id)
    private val date = MutableStateFlow(LocalDate.now())
    val today = date.flatMapLatest(repository::today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var work: Job? = null

    init {
        refreshTiming()
        viewModelScope.launch { repository.profile.collect { profile -> state.update { it.copy(loaded = true, profile = profile) } } }
        refreshCredentials()
        viewModelScope.launch {
            try { vault.initialize() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(message = "Photo storage couldn't be checked. Try again after checking your free space.") } }
        }
    }

    fun refreshCalendarDate() { date.value = LocalDate.now() }
    fun refreshDate() { refreshCalendarDate(); refreshTiming() }
    fun clearMessage() { state.update { it.copy(message = null, undoPhotoId = null) } }
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
    fun archiveRoutine(id: String) = action { repository.archiveRoutine(id); timing.refresh() }
    fun setHabitState(id: String, habitState: app.aesthory.routines.HabitState) = action { repository.setHabitState(id, habitState); timing.refresh() }
    fun prepareShare(id: String, readable: Boolean = false) = action("That routine couldn't be prepared. Try a smaller routine with at least one action.") {
        val pack = repository.exportRoutine(id)
        state.update { it.copy(packToShare = pack, shareReadable = readable) }
    }
    fun writePack(uri: Uri) = action("The routine file couldn't be saved. Choose a writable folder and try again.") {
        routineFiles.write(uri, requireNotNull(state.value.packToShare), state.value.shareReadable)
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
    fun habits(routineId: String) = repository.habits(routineId)
    fun logHabit(id: String, input: CompletionInput, onSaved: () -> Unit = {}) = action {
        repository.recordCompletion(id, date.value, input); timing.refresh(); onSaved()
    }
    fun undo(id: String) = action { repository.undoCompletion(id, date.value); timing.refresh() }

    fun beginPhoto(uri: Uri) { state.update { it.copy(photoSource = uri, saveAnalyzedPhoto = false, editingPhoto = null, message = null) } }
    fun beginAnalyzedPhoto() { if (state.value.photo != null) state.update { it.copy(photoSource = null, saveAnalyzedPhoto = true, editingPhoto = null, message = null) } }
    fun editPhoto(photo: ProgressPhoto) { state.update { it.copy(editingPhoto = photo, photoSource = null, saveAnalyzedPhoto = false, message = null) } }
    fun dismissPhoto() { state.update { it.copy(photoSource = null, saveAnalyzedPhoto = false, editingPhoto = null) } }
    suspend fun readPhoto(key: String, small: Boolean): Bitmap = vault.readPhoto(key, small)
    suspend fun photoDraftPreview(): Bitmap? {
        val pending = state.value
        return when {
            pending.photoSource != null -> vault.previewPhoto(pending.photoSource)
            pending.editingPhoto != null -> vault.readPhoto(pending.editingPhoto.fileKey, true)
            pending.saveAnalyzedPhoto -> pending.photo?.bitmap
            else -> null
        }
    }
    fun saveProgressPhoto(day: LocalDate, area: PhotoArea, view: PhotoView, note: String) = action("That photo couldn't be saved. Choose a still photo and check your free space.") {
        val pending = state.value
        if (pending.editingPhoto != null) vault.editPhoto(pending.editingPhoto.id, day, area, view, note)
        else vault.addPhoto(pending.photoSource, if (pending.saveAnalyzedPhoto) pending.photo?.bitmap else null, day, area, view, note)
        dismissPhoto()
        state.update { it.copy(message = "Saved in your progress photos.") }
    }
    fun binPhoto(id: String) = action {
        vault.binPhoto(id)
        state.update { it.copy(undoPhotoId = id, message = "Photo moved to Recently deleted.") }
    }
    fun undoPhoto() {
        val id = state.value.undoPhotoId ?: return
        action { vault.binPhoto(id, undo = true); state.update { it.copy(undoPhotoId = null, message = "Photo brought back.") } }
    }
    fun restorePhoto(id: String) = action { vault.binPhoto(id, undo = true); state.update { it.copy(message = "Photo brought back.") } }
    fun deletePhoto(id: String) = action { vault.deletePhoto(id); state.update { it.copy(message = "Photo deleted from this app.", undoPhotoId = null) } }
    fun prepareBackup(password: CharArray) = action("The backup couldn't be prepared. Check your free space and try again.") {
        try { val result = vault.prepareBackup(password); state.update { it.copy(backup = result, restoreComplete = false) } }
        finally { password.fill('\u0000') }
    }
    fun saveBackup(uri: Uri) = action("The backup wasn't saved. Choose a writable folder and try again.") {
        vault.saveBackup(uri, requireNotNull(state.value.backup).token)
        state.update { it.copy(backup = null, message = "Encrypted backup saved. Keep its password somewhere safe.") }
    }
    fun discardBackup() = action { vault.cancelBackup(); state.update { it.copy(backup = null) } }
    fun chooseRestore(uri: Uri) { state.update { it.copy(restoreSource = uri, restoreComplete = false, message = null) } }
    fun dismissRestorePassword() { state.update { it.copy(restoreSource = null) } }
    fun checkBackup(password: CharArray) = action("We couldn't unlock that backup. Check the password or choose an undamaged backup file.") {
        try {
            val preview = vault.previewRestore(requireNotNull(state.value.restoreSource), password)
            state.update { it.copy(restoreSource = null, restorePreview = preview) }
        } finally { password.fill('\u0000') }
    }
    fun cancelRestore() = action { vault.cancelRestore(); state.update { it.copy(restorePreview = null, restoreSource = null) } }
    fun restoreBackup() = action("The restore couldn't finish. Your current records are unchanged. Open the backup again to retry.") {
        state.update { it.copy(canCancel = false) }
        try {
            timing.prepareForRestore()
            try { vault.restoreBackup(requireNotNull(state.value.restorePreview).token) } finally { timing.refresh() }
            state.update { it.copy(restorePreview = null, restoreComplete = true, workoutDraft = null, nutritionDraft = null, nutritionConflict = null, photo = null, offlineText = null, editingPhoto = null, photoSource = null, saveAnalyzedPhoto = false, message = "Your backup is restored.") }
        } finally { state.update { it.copy(restorePreview = null) } }
    }

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
    fun cancelWork() { if (state.value.canCancel) work?.cancel() }

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
                val message = (failure as? WorkoutImportError)?.message ?: (failure as? app.aesthory.backup.VaultUserError)?.message ?: errorMessage ?: when (failure) {
                    is IllegalArgumentException -> "Check the entered values or selected file and try again."
                    is java.io.IOException -> "The file or connection could not be read. Your saved records are unchanged."
                    else -> "We couldn't finish that. Check what you entered or try again in a moment."
                }
                state.update { it.copy(message = message) }
            } finally { state.update { it.copy(busy = false, canCancel = true) } }
        }
    }
}
