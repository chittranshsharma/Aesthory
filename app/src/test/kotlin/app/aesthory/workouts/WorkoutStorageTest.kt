package app.aesthory.workouts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.backup.RecordSnapshot
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun parsed(weight: String = "60", date: String = "4 Nov 2026") = HevyCsv.parse(hevyFixture(weight, date = date).toByteArray(), ZoneId.of("Asia/Kolkata"))
    @Test fun repeatedImportsSkipAndCorrectionsReplaceOnlyAfterReview() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            assertNull(repo.saveWorkouts(repo.previewWorkouts(parsed()), false))
            assertEquals(1, repo.previewWorkouts(parsed()).skipped)
            val correction = repo.previewWorkouts(parsed("65"))
            assertEquals(1, correction.changed)
            assertNotNull(repo.saveWorkouts(correction, false))
            assertEquals(60.0, db.records().allWorkouts().single().sets.single().weight!!, 0.0)
            assertNull(repo.saveWorkouts(correction, true))
            assertEquals(65.0, db.records().allWorkouts().single().sets.single().weight!!, 0.0)
            assertEquals(1, repo.previewWorkouts(parsed()).changed)
            assertEquals(1, db.records().allWorkouts().size)
        } finally { db.close() }
    }
    @Test fun stalePreviewAndStorageFailureNeverPartiallySave() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            repo.saveWorkouts(repo.previewWorkouts(parsed()), false)
            val stale = repo.previewWorkouts(parsed("70") + parsed(date = "5 Nov 2026"))
            repo.saveWorkouts(repo.previewWorkouts(parsed("65")), true)
            assertNotNull(repo.saveWorkouts(stale, true))
            assertEquals(1, db.records().allWorkouts().size)
            val fresh = repo.previewWorkouts(parsed("70"))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_workout_set BEFORE INSERT ON workout_sets BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            assertTrue(runCatching { repo.saveWorkouts(fresh, true) }.isFailure)
            assertEquals(65.0, db.records().allWorkouts().single().sets.single().weight!!, 0.0)
        } finally { db.close() }
    }
    @Test fun snapshotKeepsWorkoutHistoryAndRejectsChangedContent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            repo.saveWorkouts(repo.previewWorkouts(parsed()), false)
            val snapshot = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(db, 123)))
            RecordSnapshot.validate(context, snapshot)
            RecordSnapshot.replace(db, snapshot)
            assertEquals(1, repo.previewWorkouts(parsed()).skipped)
            val set = snapshot.getJSONObject("tables").getJSONArray("workout_sets").getJSONObject(0)
            set.put("reps", 4294967304L) // Room must not narrow this to the original 8 reps.
            assertTrue(runCatching { RecordSnapshot.validate(context, snapshot) }.isFailure)
            set.put("reps", 8).put("weight", 99)
            assertTrue(runCatching { RecordSnapshot.validate(context, snapshot) }.isFailure)
            assertEquals(60.0, db.records().allWorkouts().single().sets.single().weight!!, 0.0)
        } finally { db.close() }
    }
}
