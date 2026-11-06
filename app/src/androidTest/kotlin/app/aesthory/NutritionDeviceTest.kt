package app.aesthory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import app.aesthory.nutrition.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionDeviceTest {
    @Test fun bundledOcrReadsGeneratedScreenshotAndReviewedSaveSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "nutrition-device-${UUID.randomUUID()}.db"
        val source = File(context.cacheDir, "nutrition-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(1080, 1400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f }
            listOf("Daily totals", "Calories: 1850 kcal", "Protein: 130 g", "Carbs: 210 g", "Fat: 60 g").forEachIndexed { index, line -> drawText(line, 60f, 150f + index * 180f, paint) }
        }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        var db = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).build()
        try {
            val scan = NutritionOcr(context).scan(Uri.fromFile(source))
            assertEquals(NutritionValues(1850.0, 130.0, 210.0, 60.0), scan.values)
            val corrected = NutritionDay(20000, 1900.0, scan.values.protein, scan.values.carbs, scan.values.fat, scan.hash, savedAtMillis = 123)
            assertEquals(NutritionSave.Saved, AesthoryRepository(db).saveNutrition(corrected, null))
            db.close()
            db = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).build()
            val repo = AesthoryRepository(db)
            assertEquals(corrected, repo.nutritionOn(20000))
            assertEquals(NutritionSave.Duplicate(20000), repo.saveNutrition(corrected, null))
            val second = corrected.copy(calories = 2000.0, sourceHash = "b".repeat(64), revision = UUID.randomUUID().toString())
            assertEquals(NutritionSave.Replace(corrected), repo.saveNutrition(second, null))
            assertEquals(NutritionSave.Saved, repo.saveNutrition(second, corrected.revision))
            assertEquals(2000.0, repo.nutritionOn(20000)!!.calories!!, 0.0)
            assertEquals(1, db.records().allNutrition().size)
            val bad = File(context.cacheDir, "bad-${UUID.randomUUID()}.png")
            try { bad.writeText("not an image"); assertTrue(runCatching { NutritionOcr(context).scan(Uri.fromFile(bad)) }.isFailure) } finally { bad.delete() }
        } finally { db.close(); context.deleteDatabase(name); source.delete() }
    }
}
