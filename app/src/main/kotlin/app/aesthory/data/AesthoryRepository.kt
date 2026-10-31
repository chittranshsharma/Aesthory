package app.aesthory.data

import androidx.room.withTransaction
import app.aesthory.coaching.StarterRoutine
import app.aesthory.profile.BodyMeasurements
import app.aesthory.profile.ProfileBaseline
import app.aesthory.routines.CompletionInput
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.HabitSchedule
import app.aesthory.routines.HabitState
import app.aesthory.routines.TrackingSettings
import app.aesthory.routines.RoutinePack
import app.aesthory.routines.PackHabit
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.combine

data class HabitDraft(
    val title: String,
    val categoryId: String,
    val schedule: HabitSchedule,
    val instructions: String = "",
    val estimatedMinutes: Int? = null,
    val tracking: TrackingSettings = TrackingSettings(),
) {
    init {
        require(title.isNotBlank() && categoryId.isNotBlank())
        require(estimatedMinutes == null || estimatedMinutes > 0)
    }
}

data class TodayHabit(val habit: CurrentHabit, val event: CompletionEventEntity?)

class AesthoryRepository(private val db: AesthoryDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = db.records()
    val profile = dao.observeProfile()
    val measurements = dao.observeMeasurements()
    val routines = dao.observeRoutines()
    val categories = dao.observeCategories()

    suspend fun saveProfile(baseline: ProfileBaseline) = db.withTransaction {
        dao.saveProfile(ProfileEntity(
            displayName = baseline.displayName?.trim()?.takeIf { it.isNotEmpty() },
            ageBand = baseline.ageBand, tone = baseline.tone, coaches = baseline.selectedCoaches,
        ))
        if (baseline.measurements != BodyMeasurements()) recordMeasurements(baseline.measurements)
    }

    suspend fun recordMeasurements(values: BodyMeasurements, atMillis: Long = clock.millis()): Long {
        require(values != BodyMeasurements()) { "Enter at least one measurement." }
        requireNotNull(dao.getProfile()) { "Create the local profile first." }
        return dao.insertMeasurement(MeasurementEntity(recordedAtMillis = atMillis, values = values))
    }

    suspend fun createCategory(name: String): String {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        dao.insertCategory(CategoryEntity(id, name.trim()))
        return id
    }

    suspend fun createRoutine(name: String): String {
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        dao.insertRoutine(RoutineEntity(id, name.trim(), clock.millis()))
        return id
    }

    suspend fun addStarter(starter: StarterRoutine, start: LocalDate): String = db.withTransaction {
        val id = "starter:${starter.id}"
        if (dao.getRoutine(id) != null) {
            dao.restoreRoutine(id)
            return@withTransaction id
        }
        val category = createCategory(starter.focus.displayName)
        dao.insertRoutine(RoutineEntity(id, starter.name, clock.millis()))
        starter.steps.forEach { step -> addHabit(id, HabitDraft(step.title, category, HabitSchedule(start), step.instructions, step.minutes)) }
        id
    }

    suspend fun exportRoutine(id: String): RoutinePack = db.withTransaction {
        val routine = requireNotNull(dao.getRoutine(id))
        val categories = dao.getCategories().associateBy { it.id }
        RoutinePack(routine.name, dao.getHabits(id).filter { it.state != HabitState.ARCHIVED }.map { habit ->
            val definition = requireNotNull(dao.getVersion(habit.id, habit.currentVersion))
            PackHabit(definition.title, requireNotNull(categories[definition.categoryId]).name, definition.instructions,
                definition.estimatedMinutes, definition.schedule.toDomain(), definition.tracking)
        })
    }

    suspend fun importRoutine(pack: RoutinePack): String = db.withTransaction {
        val id = "pack:${pack.fingerprint}"
        if (dao.getRoutine(id) != null) {
            dao.restoreRoutine(id)
            return@withTransaction id
        }
        val categories = dao.getCategories().associate { it.name.lowercase(java.util.Locale.ROOT) to it.id }.toMutableMap()
        dao.insertRoutine(RoutineEntity(id, pack.name, clock.millis()))
        pack.habits.forEach { habit ->
            val category = categories[habit.category.lowercase(java.util.Locale.ROOT)]
                ?: createCategory(habit.category).also { categories[habit.category.lowercase(java.util.Locale.ROOT)] = it }
            addHabit(id, HabitDraft(habit.title, category, habit.schedule, habit.instructions, habit.minutes, habit.tracking))
        }
        id
    }

    suspend fun addHabit(routineId: String, draft: HabitDraft): String = db.withTransaction {
        require(dao.getRoutine(routineId)?.archived == false) { "Choose an active routine." }
        val id = UUID.randomUUID().toString()
        dao.insertHabit(HabitEntity(id, routineId, dao.getHabits(routineId).size, currentVersion = 1))
        dao.insertVersion(draft.toVersion(id, 1))
        id
    }

    suspend fun editHabit(id: String, draft: HabitDraft) = db.withTransaction {
        val habit = requireNotNull(dao.getHabit(id)) { "Habit not found." }
        val next = habit.currentVersion + 1
        dao.insertVersion(draft.toVersion(id, next))
        dao.setVersion(id, next)
    }

    fun habits(routineId: String) = dao.observeCurrentHabits(routineId)

    suspend fun reorderHabits(routineId: String, orderedIds: List<String>) = db.withTransaction {
        val existing = dao.getHabits(routineId).map { it.id }.toSet()
        require(orderedIds.size == existing.size && orderedIds.toSet() == existing) {
            "Reordering must include every habit exactly once."
        }
        orderedIds.forEachIndexed { position, id -> dao.setPosition(id, position) }
    }

    suspend fun setHabitState(id: String, state: HabitState) {
        requireNotNull(dao.getHabit(id))
        dao.setState(id, state)
    }

    suspend fun archiveRoutine(id: String) {
        requireNotNull(dao.getRoutine(id))
        dao.archiveRoutine(id)
    }

    suspend fun recordCompletion(id: String, date: LocalDate, input: CompletionInput): Long = db.withTransaction {
        val habit = requireNotNull(dao.getHabit(id))
        val definition = requireNotNull(dao.getVersion(id, habit.currentVersion))
        input.validateFor(definition.tracking)
        dao.insertEvent(CompletionEventEntity(
            habitId = id, definitionVersion = definition.version, dateDay = date.toEpochDay(),
            recordedAtMillis = clock.millis(), status = input.status, value = input.value,
            checkedItems = input.checkedItems.sorted(),
        ))
    }

    suspend fun undoCompletion(id: String, date: LocalDate) =
        recordCompletion(id, date, CompletionInput(CompletionStatus.UNDONE))

    suspend fun history(id: String) = dao.getHistory(id)
    suspend fun definition(id: String, version: Int) = dao.getVersion(id, version)

    fun today(date: LocalDate) = combine(dao.observeActiveHabits(), dao.observeEventsOn(date.toEpochDay())) { habits, events ->
        val latest = events.distinctBy { it.habitId }.associateBy { it.habitId }
        habits.filter { it.definition.schedule.toDomain().isDueOn(date) }
            .map { TodayHabit(it, latest[it.definition.habitId]) }
    }

    private fun HabitDraft.toVersion(id: String, version: Int) = HabitVersionEntity(
        id, version, title.trim(), categoryId, instructions, estimatedMinutes,
        StoredSchedule.from(schedule), tracking,
    )
}
