package app.aesthory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.coaching.CoachMode
import app.aesthory.profile.BodyMeasurements
import app.aesthory.profile.ProfileBaseline
import app.aesthory.routines.CompletionInput
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.HabitSchedule
import app.aesthory.routines.HabitState
import app.aesthory.routines.TrackingKind
import app.aesthory.routines.TrackingSettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AesthoryRepositoryTest {
    private lateinit var db: AesthoryDatabase
    private lateinit var repository: AesthoryRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 10, 31)

    @Before fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).allowMainThreadQueries().build()
        repository = AesthoryRepository(db, clock)
    }
    @After fun closeDatabase() { db.close() }

    @Test fun `profile and measurements survive closing and reopening a file database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "profile-reopen-test.db"
        context.deleteDatabase(name)
        fun open(): AesthoryDatabase = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val first = open()
            try {
                AesthoryRepository(first, clock).saveProfile(ProfileBaseline(
                    displayName = "  Test  ", selectedCoaches = setOf(CoachMode.FACIAL_AESTHETICS),
                    measurements = BodyMeasurements(heightCm = 175.0, weightKg = 70.0),
                ))
            } finally { first.close() }
            val second = open()
            try {
                val restored = AesthoryRepository(second, clock)
                assertEquals("Test", restored.profile.first()!!.displayName)
                assertEquals(setOf(CoachMode.FACIAL_AESTHETICS), restored.profile.first()!!.coaches)
                assertEquals(70.0, restored.measurements.first().single().values.weightKg!!, 0.0)
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun `optional profile stores no placeholder measurement and dated updates remain separate`() = runBlocking {
        repository.saveProfile(ProfileBaseline())
        assertTrue(repository.measurements.first().isEmpty())
        repository.recordMeasurements(BodyMeasurements(weightKg = 70.0), 100)
        repository.recordMeasurements(BodyMeasurements(weightKg = 71.0), 200)
        assertEquals(listOf(71.0, 70.0), repository.measurements.first().map { it.values.weightKg })
    }

    @Test fun `editing instructions preserves the definition attached to old completions`() = runBlocking {
        val (routine, category) = parents()
        val draft = HabitDraft("Evening care", category, HabitSchedule(day), instructions = "Original instructions")
        val id = repository.addHabit(routine, draft)
        repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE))
        repository.editHabit(id, draft.copy(instructions = "New instructions"))
        assertEquals(2, repository.habits(routine).first().single().definition.version)
        val recordedVersion = repository.history(id).single().definitionVersion
        assertEquals("Original instructions", repository.definition(id, recordedVersion)!!.instructions)
    }

    @Test fun `reordering is atomic and rejects duplicate or incomplete lists`() = runBlocking {
        val (routine, category) = parents()
        val one = repository.addHabit(routine, HabitDraft("One", category, HabitSchedule(day)))
        val two = repository.addHabit(routine, HabitDraft("Two", category, HabitSchedule(day)))
        repository.reorderHabits(routine, listOf(two, one))
        assertEquals(listOf(two, one), repository.habits(routine).first().map { it.definition.habitId })
        try { repository.reorderHabits(routine, listOf(one, one)); fail("Expected validation") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf(two, one), repository.habits(routine).first().map { it.definition.habitId })
    }

    @Test fun `completion validation prevents invalid payloads from being written`() = runBlocking {
        val (routine, category) = parents()
        val id = repository.addHabit(routine, HabitDraft(
            "Count", category, HabitSchedule(day), tracking = TrackingSettings(TrackingKind.COUNT, target = 10.0),
        ))
        for (invalid in listOf(-1.0, Double.NaN, 1.5)) {
            try { repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE, value = invalid)); fail("Expected validation") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(repository.history(id).isEmpty())
        repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE, value = 10.0))
        assertEquals(10.0, repository.history(id).single().value!!, 0.0)
    }

    @Test fun `undo appends history and latest event resolves equal timestamps deterministically`() = runBlocking {
        val (routine, category) = parents()
        val id = repository.addHabit(routine, HabitDraft("Care", category, HabitSchedule(day)))
        repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE))
        repository.undoCompletion(id, day)
        assertEquals(2, repository.history(id).size)
        assertEquals(CompletionStatus.UNDONE, repository.today(day).first().single().event!!.status)
    }

    @Test fun `paused habits and archived routines disappear from Today while history remains`() = runBlocking {
        val (routine, category) = parents()
        val id = repository.addHabit(routine, HabitDraft("Care", category, HabitSchedule(day)))
        repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE))
        repository.setHabitState(id, HabitState.PAUSED)
        assertTrue(repository.today(day).first().isEmpty())
        repository.setHabitState(id, HabitState.ACTIVE)
        assertEquals(1, repository.today(day).first().size)
        repository.archiveRoutine(routine)
        assertTrue(repository.today(day).first().isEmpty())
        assertEquals(1, repository.history(id).size)
    }

    @Test fun `foreign key failure does not leave a half created habit`() = runBlocking {
        val routine = repository.createRoutine("Care")
        try { repository.addHabit(routine, HabitDraft("Care", "missing-category", HabitSchedule(day))); fail("Expected foreign key failure") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(repository.habits(routine).first().isEmpty())
    }

    @Test fun `checklist punctuation unicode and checked indices round trip`() = runBlocking {
        val (routine, category) = parents()
        val items = listOf("Read \"label\"", "Routine, step\n2", "देखभाल")
        val id = repository.addHabit(routine, HabitDraft(
            "Checklist", category, HabitSchedule(day), tracking = TrackingSettings(TrackingKind.CHECKLIST, items = items),
        ))
        repository.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE, checkedItems = listOf(2, 0, 1)))
        assertEquals(items, repository.habits(routine).first().single().definition.tracking.items)
        assertEquals(listOf(0, 1, 2), repository.history(id).single().checkedItems)
    }

    private suspend fun parents() = repository.createRoutine("Evening") to repository.createCategory("Personal care")
}
