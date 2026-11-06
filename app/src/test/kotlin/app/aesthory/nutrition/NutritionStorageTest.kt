package app.aesthory.nutrition

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
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NutritionStorageTest {
    private fun entry(day: Long = 20000, calories: Double = 1800.0, hash: String? = "a".repeat(64)) = NutritionDay(day, calories, 100.0, null, 50.0, hash, savedAtMillis = 123)
    @Test fun duplicateImportAndReplacementNeverAddTotals() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            val original = entry()
            assertEquals(NutritionSave.Saved, repo.saveNutrition(original, null))
            assertEquals(NutritionSave.Duplicate(original.day), repo.saveNutrition(entry(day = 20001), null))
            val next = entry(calories = 2100.0, hash = "b".repeat(64))
            assertEquals(NutritionSave.Replace(original), repo.saveNutrition(next, null))
            assertEquals(original, repo.nutritionOn(original.day))
            assertEquals(NutritionSave.Saved, repo.saveNutrition(next, original.revision))
            assertEquals(2100.0, repo.nutritionOn(original.day)!!.calories!!, 0.0)
            assertEquals(1, db.records().allNutrition().size)
            assertEquals(NutritionSave.Duplicate(original.day), repo.saveNutrition(original, null))
        } finally { db.close() }
    }
    @Test fun staleConfirmationAndUnknownFieldsCannotMergeOldData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            val original = entry(hash = null)
            repo.saveNutrition(original, null)
            val next = entry(calories = 2000.0, hash = null)
            repo.saveNutrition(next, original.revision)
            assertEquals(NutritionSave.Replace(next), repo.saveNutrition(original, original.revision))
            val onlyProtein = NutritionDay(original.day, null, 120.0, null, null, null, savedAtMillis = 124)
            repo.saveNutrition(onlyProtein, next.revision)
            assertNull(repo.nutritionOn(original.day)!!.calories)
        } finally { db.close() }
    }
    @Test fun backupKeepsReceiptsAndRejectsInvalidNutrition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            val repo = AesthoryRepository(db)
            val original = entry()
            repo.saveNutrition(original, null)
            val snapshot = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(db, 123)))
            RecordSnapshot.validate(context, snapshot)
            RecordSnapshot.replace(db, snapshot)
            assertEquals(original, repo.nutritionOn(original.day))
            assertEquals(NutritionSave.Duplicate(original.day), repo.saveNutrition(original, null))
            val invalid = JSONObject(snapshot.toString())
            invalid.getJSONObject("tables").getJSONArray("nutrition_days").getJSONObject(0).put("calories", -1)
            assertTrue(runCatching { RecordSnapshot.validate(context, invalid) }.isFailure)
            assertEquals(original, repo.nutritionOn(original.day))
        } finally { db.close() }
    }
}
