package app.aesthory

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import app.aesthory.workouts.*
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

internal const val WORKOUT_CSV = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe\nPush,\"4 Nov 2026, 17:00\",\"4 Nov 2026, 18:00\",,Bench Press,,\"Slow, steady\nGood range\",0,normal,60,8,,,8"

@RunWith(AndroidJUnit4::class)
class WorkoutDeviceTest {
    @Test fun localFileImportReopensAndNeverDuplicatesWorkouts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "workout-native-test.db"
        context.deleteDatabase(name)
        val source = File(context.cacheDir, "workout-native-fixture.csv").apply { writeText(WORKOUT_CSV) }
        var db = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).build()
        try {
            val bytes = WorkoutFiles(context).read(Uri.fromFile(source))
            val workouts = HevyCsv.parse(bytes, ZoneId.of("Asia/Kolkata"))
            assertEquals("Slow, steady\nGood range", workouts.single().sets.single().notes)
            var repo = AesthoryRepository(db)
            assertNull(repo.saveWorkouts(repo.previewWorkouts(workouts), false))
            db.close()
            db = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).build()
            repo = AesthoryRepository(db)
            assertEquals(1, repo.previewWorkouts(workouts).skipped)
            val revised = HevyCsv.parse(WORKOUT_CSV.replace("60,8", "65,8").toByteArray(), ZoneId.of("Asia/Kolkata"))
            val review = repo.previewWorkouts(revised)
            assertEquals(1, review.changed)
            assertNotNull(repo.saveWorkouts(review, false))
            assertNull(repo.saveWorkouts(review, true))
            assertEquals(65.0, db.records().allWorkouts().single().sets.single().weight!!, 0.0)
            source.writeText(WORKOUT_CSV + "\nBroken,row")
            assertTrue(runCatching { HevyCsv.parse(WorkoutFiles(context).read(Uri.fromFile(source)), ZoneId.of("Asia/Kolkata")) }.isFailure)
            assertEquals(1, db.records().allWorkouts().size)
        } finally { db.close(); context.deleteDatabase(name); source.delete() }
    }
}
