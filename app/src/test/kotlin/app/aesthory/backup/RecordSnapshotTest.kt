package app.aesthory.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.coaching.StarterRoutines
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import app.aesthory.profile.ProfileBaseline
import app.aesthory.profile.BodyMeasurements
import app.aesthory.routines.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordSnapshotTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AesthoryDatabase
    private lateinit var repo: AesthoryRepository
    private val day = LocalDate.of(2026, 10, 31)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build(); repo = AesthoryRepository(db) }
    @After fun close() { db.close() }
    private suspend fun seed() {
        repo.saveProfile(ProfileBaseline(displayName = "Original", measurements = BodyMeasurements(heightCm = 180.0)))
        val id = repo.addStarter(StarterRoutines.all.first(), day)
        val habit = repo.habits(id).first().first()
        repo.recordCompletion(habit.definition.habitId, day, CompletionInput(CompletionStatus.DONE))
        repo.editHabit(habit.definition.habitId, app.aesthory.data.HabitDraft("Updated title", habit.definition.categoryId, HabitSchedule(day)))
    }
    @Test fun `snapshot retains dated records historical definitions and completion references`() = runBlocking {
        seed()
        val snapshot = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(db, 123)))
        RecordSnapshot.validate(context, snapshot)
        repo.saveProfile(ProfileBaseline(displayName = "Changed"))
        RecordSnapshot.replace(db, snapshot)
        assertEquals("Original", repo.profile.first()!!.displayName)
        assertEquals(1, db.records().allMeasurements().size)
        assertEquals(3, db.records().allVersions().size)
        val event = db.records().allEvents().single()
        assertEquals(1, event.definitionVersion)
        assertEquals("Cleanse gently", repo.definition(event.habitId, 1)!!.title)
    }
    @Test fun `restore transaction rolls back partial inserts on an invalid reference`() = runBlocking {
        seed()
        val snapshot = RecordSnapshot.capture(db, 123)
        snapshot.getJSONObject("tables").getJSONArray("habit_versions").getJSONObject(0).put("categoryId", "missing-category")
        assertTrue(runCatching { RecordSnapshot.replace(db, snapshot) }.isFailure)
        assertEquals("Original", repo.profile.first()!!.displayName)
        assertEquals(1, db.records().allEvents().size)
        assertEquals(3, db.records().allVersions().size)
    }
    @Test fun `an already collected profile flow refreshes after restore`() = runBlocking {
        seed()
        val snapshot = RecordSnapshot.capture(db, 123)
        val names = Channel<String?>(Channel.UNLIMITED)
        val collector = launch { repo.profile.collect { names.send(it?.displayName) } }
        suspend fun awaitName(expected: String) = withTimeout(5000) { while (names.receive() != expected) Unit }
        try {
            awaitName("Original")
            repo.saveProfile(ProfileBaseline(displayName = "Changed")); awaitName("Changed")
            RecordSnapshot.replace(db, snapshot); awaitName("Original")
        } finally { collector.cancel(); names.close() }
    }
    @Test fun `future format extra tables invalid enums and nested serialized arrays are rejected`() = runBlocking {
        seed()
        val snapshot = RecordSnapshot.capture(db, 123)
        snapshot.put("schema", 99)
        assertTrue(runCatching { RecordSnapshot.decode(RecordSnapshot.encode(snapshot)) }.isFailure)
        snapshot.put("schema", 2)
        snapshot.getJSONObject("tables").put("credentials", org.json.JSONArray())
        assertTrue(runCatching { RecordSnapshot.decode(RecordSnapshot.encode(snapshot)) }.isFailure)
        snapshot.getJSONObject("tables").remove("credentials")
        snapshot.getJSONObject("tables").getJSONArray("profiles").getJSONObject(0).put("ageBand", "NOT_AN_AGE")
        assertTrue(runCatching { RecordSnapshot.validate(context, snapshot) }.isFailure)
        snapshot.getJSONObject("tables").getJSONArray("profiles").getJSONObject(0).put("ageBand", "UNSPECIFIED")
        snapshot.getJSONObject("tables").getJSONArray("habit_versions").getJSONObject(0).put("tracking_items", "[".repeat(3000) + "0" + "]".repeat(3000))
        assertTrue(runCatching { RecordSnapshot.validate(context, snapshot) }.isFailure)
        assertEquals("Original", repo.profile.first()!!.displayName)
    }
    @Test fun `encrypted backup previews without changing live data and then restores records`() = runBlocking {
        seed()
        val vault = PersonalVault(context, db)
        val file = File(context.cacheDir, "unit-backup.aesthory")
        try {
            val pass = "test-only-backup-password".toCharArray()
            val prepared = vault.prepareBackup(pass)
            assertTrue(pass.all { it == '\u0000' })
            vault.saveBackup(Uri.fromFile(file), prepared.token)
            repo.saveProfile(ProfileBaseline(displayName = "Keep until confirmed"))
            assertTrue(runCatching { vault.previewRestore(Uri.fromFile(file), "wrong-test-password".toCharArray()) }.isFailure)
            assertEquals("Keep until confirmed", repo.profile.first()!!.displayName)
            val preview = vault.previewRestore(Uri.fromFile(file), "test-only-backup-password".toCharArray())
            assertEquals(1, preview.summary.checkIns)
            assertEquals("Keep until confirmed", repo.profile.first()!!.displayName)
            vault.restoreBackup(preview.token)
            assertEquals("Original", repo.profile.first()!!.displayName)
            assertEquals(1, db.records().allEvents().size)
            assertTrue(runCatching { vault.restoreBackup(preview.token) }.isFailure)
        } finally { file.delete() }
    }
}
