package app.aesthory.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.backup.RecordSnapshot
import app.aesthory.data.*
import app.aesthory.profile.ProfileBaseline
import app.aesthory.routines.*
import java.time.LocalDate
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
class LibraryRepositoryTest {
    private lateinit var db: AesthoryDatabase
    private lateinit var repo: AesthoryRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 10, 31)
    private lateinit var routine: String
    private lateinit var category: String
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        repo = AesthoryRepository(db)
        repo.saveProfile(ProfileBaseline())
        routine = repo.createRoutine("Evening")
        category = repo.createCategory("Care")
        repo.saveProduct(ProductEntity("product", "Original product", brand = "Brand", labelNotes = "Original label", notes = "PRIVATE observation"))
        repo.saveReference(ReferenceEntity("reference", "Original source", "PRIVATE source text", "https://example.com/guide", day.toEpochDay()))
    }
    @After fun close() { db.close() }
    private fun draft() = HabitDraft("Care", category, HabitSchedule(day), "Original instructions", 5, TrackingSettings(TrackingKind.COUNT, 4.0),
        purpose = "Stay consistent", tags = listOf("evening"), shortInstructions = "One small step", products = listOf(ProductChoice("product", "Original use note")), referenceIds = listOf("reference"))

    @Test fun historicalLinksAndObservationsKeepTheirContentsAfterLibraryAndHabitEdits() = runBlocking {
        val id = repo.addHabit(routine, draft())
        repo.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE, 4.0))
        repo.addObservation("product", day, "Personal note")
        repo.saveProduct(db.records().getProduct("product")!!.copy(name = "New product name", labelNotes = "New label"))
        repo.saveReference(db.records().getReference("reference")!!.copy(content = "New source text"))
        repo.editHabit(id, draft().copy(title = "Changed care", instructions = "Changed instructions", tracking = TrackingSettings(TrackingKind.CHECKLIST, items = listOf("A", "B"))))
        val history = repo.historyDetails(id).single()
        assertEquals(1, history.event.definitionVersion)
        assertEquals("Original instructions", history.definition.instructions)
        assertEquals("Original product", history.links.products.single().name)
        assertEquals("Original label", history.links.products.single().labelNotes)
        assertEquals("PRIVATE source text", history.links.references.single().content)
        assertEquals("New product name", repo.links(id, 2).first().products.single().name)
        assertEquals("New source text", repo.links(id, 2).first().references.single().content)
        assertEquals("Original product", repo.observations.first().single().productName)
        repo.archiveProduct("product", true); repo.archiveReference("reference", true)
        assertTrue(repo.products.first().single().archived)
        assertEquals(1, repo.links(id, 1).first().products.size)
        assertEquals(1, repo.observations.first().size)
    }
    @Test fun missingLinksRollbackNewHabitsAndNewVersionsAtomically() = runBlocking {
        assertTrue(runCatching { repo.addHabit(routine, draft().copy(referenceIds = listOf("missing"))) }.isFailure)
        assertTrue(repo.habits(routine).first().isEmpty())
        assertTrue(db.records().allVersions().isEmpty())
        val id = repo.addHabit(routine, draft())
        assertTrue(runCatching { repo.editHabit(id, draft().copy(products = listOf(ProductChoice("missing")))) }.isFailure)
        assertEquals(1, repo.habits(routine).first().single().definition.version)
        assertEquals(1, db.records().allVersions().size)
        assertEquals("Original product", repo.links(id, 1).first().products.single().name)
    }
    @Test fun movementPauseAndArchivePreserveHistoryAndCanBeReversed() = runBlocking {
        val first = repo.addHabit(routine, draft())
        val second = repo.addHabit(routine, draft().copy(title = "Second"))
        repo.recordCompletion(first, day, CompletionInput(CompletionStatus.PARTIAL, 2.0))
        repo.moveHabit(second, -1)
        assertEquals(listOf(second, first), repo.habits(routine).first().map { it.definition.habitId })
        repo.setHabitState(first, HabitState.PAUSED)
        assertEquals(listOf(second), repo.today(day).first().map { it.habit.definition.habitId })
        repo.setHabitState(first, HabitState.ARCHIVED)
        repo.setHabitState(first, HabitState.ACTIVE)
        assertEquals(CompletionStatus.PARTIAL, repo.today(day).first().last().event!!.status)
        assertEquals(1, repo.history(first).size)
    }
    @Test fun backupsRestoreLibrariesObservationsAndVersionedLinksButRoutineExportsExcludeThem() = runBlocking {
        val id = repo.addHabit(routine, draft())
        repo.addObservation("product", day, "Observation kept in backup")
        repo.archiveReference("reference", true)
        val snapshot = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(db, 123)))
        RecordSnapshot.validate(context, snapshot)
        assertEquals(1, RecordSnapshot.summary(snapshot).products)
        repo.saveProduct(db.records().getProduct("product")!!.copy(name = "Changed"))
        RecordSnapshot.replace(db, snapshot)
        assertEquals("Original product", repo.products.first().single().name)
        assertTrue(repo.references.first().single().archived)
        assertEquals("Observation kept in backup", repo.observations.first().single().note)
        assertEquals("PRIVATE source text", repo.links(id, 1).first().references.single().content)
        val pack = repo.exportRoutine(routine)
        val bytes = RoutinePacks.encode(pack)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("PRIVATE"))
        assertFalse(RoutinePacks.readableText(pack).contains("PRIVATE"))
        assertTrue(RoutinePacks.readableText(pack).contains("One small step"))
        val imported = repo.importRoutine(RoutinePacks.decode(bytes))
        val copy = repo.habits(imported).first().single()
        assertEquals("Stay consistent", copy.definition.purpose)
        assertEquals(listOf("evening"), copy.definition.tags)
        assertTrue(repo.links(copy.definition.habitId, 1).first().products.isEmpty())
    }
    @Test fun invalidLibraryRecordsAndUnsafeLinksAreRejected() {
        listOf("javascript:alert(1)", "file:///private", "content://other", "https://name:secret@example.com", "https:///bad", "not a link").forEach { assertFalse(it, safeWebUrl(it)) }
        listOf("", "https://example.com/path?q=1#notes", "http://example.com").forEach { assertTrue(it, safeWebUrl(it)) }
        assertTrue(runCatching { ProductEntity("id", "Name", openedDay = day.toEpochDay(), expiryDay = day.minusDays(1).toEpochDay()) }.isFailure)
        assertTrue(runCatching { ReferenceEntity("id", "Empty") }.isFailure)
        assertTrue(runCatching { ProductEntity("id", "Name", periodMonths = 0) }.isFailure)
    }
}
