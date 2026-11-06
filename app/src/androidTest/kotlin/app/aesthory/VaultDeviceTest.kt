package app.aesthory

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aesthory.backup.PersonalVault
import app.aesthory.data.AesthoryDatabase
import app.aesthory.data.AesthoryRepository
import app.aesthory.data.HabitDraft
import app.aesthory.library.*
import app.aesthory.routines.HabitSchedule
import app.aesthory.timing.*
import kotlinx.coroutines.flow.first
import app.aesthory.profile.ProfileBaseline
import app.aesthory.progress.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDeviceTest {
    @Test fun photosAndEncryptedRecoveryPreservePrivacyAndSurviveFailedRestore() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val namespace = UUID.randomUUID().toString()
        val root = File(base.cacheDir, "vault-device-$namespace").also { it.mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").also { it.mkdirs() }
            override fun getCacheDir() = File(root, "cache").also { it.mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("vault_test_${namespace}_$name", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        val repo = AesthoryRepository(db)
        val vault = PersonalVault(context, db)
        val backup = File(root, "recovery.aesthory")
        val day = LocalDate.of(2026, 10, 31)
        try {
            repo.saveProfile(ProfileBaseline(displayName = "Original"))
            val nutrition = app.aesthory.nutrition.NutritionDay(day.toEpochDay(), 1800.0, 100.0, null, 60.0, "a".repeat(64), savedAtMillis = 123)
            repo.saveNutrition(nutrition, null)
            repo.saveProduct(ProductEntity("product", "Original product", labelNotes = "Label"))
            repo.saveReference(ReferenceEntity("reference", "Source", "Private reference text"))
            repo.addObservation("product", day, "Dated observation")
            val category = repo.createCategory("Care")
            val routine = repo.createRoutine("Routine")
            val habit = repo.addHabit(routine, HabitDraft("Action", category, HabitSchedule(day), products = listOf(ProductChoice("product", "Use notes")), referenceIds = listOf("reference")))
            val moment = AndroidTimingClock(context).now()
            db.records().saveTimer(ActiveTimer(sessionId = UUID.randomUUID().toString(), habitId = habit, definitionVersion = 1, day = day.toEpochDay(),
                accumulatedMillis = 4000, startedElapsedMillis = moment.elapsed, startedWallMillis = moment.wall, boot = moment.boot))
            db.records().saveReminder(HabitReminder(habit, 8 * 60))
            db.records().saveReminderPreferences(ReminderPreferences())
            val source = File(context.cacheDir, "source.jpg")
            val bitmap = Bitmap.createBitmap(512, 768, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,34/1,56/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                saveAttributes()
            }
            vault.addPhoto(Uri.fromFile(source), null, day, PhotoArea.FACE, PhotoView.FRONT, "First")
            source.delete()
            val first = db.records().allPhotos().single()
            assertEquals(768, first.width); assertEquals(512, first.height)
            val saved = File(context.filesDir, "progress-photos/${first.fileKey}.jpg")
            assertNull(ExifInterface(saved.absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            vault.readPhoto(first.fileKey, false).also { assertFalse(it.isRecycled); it.recycle() }
            vault.addPhoto(null, bitmap, day.plusDays(1), PhotoArea.FACE, PhotoView.FRONT, "Second")
            bitmap.recycle()
            vault.binPhoto(first.id)
            val original = db.records().allPhotos()
            val modelFile = File(context.filesDir, "local-coach/model.litertlm").also { it.parentFile!!.mkdirs(); it.writeText("test-only model placeholder") }
            val preferences = context.getSharedPreferences("encrypted_credentials_groq", Context.MODE_PRIVATE)
            preferences.edit().putString("groq", "test-only credential placeholder").commit()
            val prepared = vault.prepareBackup("test-only नमस्ते password".toCharArray())
            assertEquals(2, prepared.summary.photos); assertEquals(1, prepared.summary.binnedPhotos)
            assertEquals(1, prepared.summary.timers); assertEquals(1, prepared.summary.reminders)
            assertTrue(db.records().getTimer()!!.running)
            vault.saveBackup(Uri.fromFile(backup), prepared.token)
            repo.saveProfile(ProfileBaseline(displayName = "Current"))
            repo.saveProduct(ProductEntity("product", "Changed product"))
            vault.binPhoto(first.id, undo = true)
            val preview = vault.previewRestore(Uri.fromFile(backup), "test-only नमस्ते password".toCharArray())
            assertEquals("Current", db.records().getProfile()!!.displayName)
            vault.restoreBackup(preview.token)
            assertEquals(nutrition, repo.nutritionOn(day.toEpochDay()))
            assertEquals(app.aesthory.nutrition.NutritionSave.Duplicate(day.toEpochDay()), repo.saveNutrition(nutrition, null))
            assertEquals("Original", db.records().getProfile()!!.displayName)
            assertFalse(db.records().getTimer()!!.running)
            assertEquals(TimerRecovery.BACKUP, db.records().getTimer()!!.recovery)
            assertTrue(db.records().getTimer()!!.accumulatedMillis >= 4000)
            assertTrue(db.records().getReminderPreferences()!!.paused)
            assertEquals(8 * 60, db.records().allReminders().single().minuteOfDay)
            assertEquals("Original product", db.records().allProducts().single().name)
            assertEquals("Dated observation", db.records().allObservations().single().note)
            assertEquals("Private reference text", repo.links(habit, 1).first().references.single().content)
            assertEquals("Use notes", repo.links(habit, 1).first().products.single().usageNote)
            val restored = db.records().allPhotos()
            assertEquals(original.map { it.id }.toSet(), restored.map { it.id }.toSet())
            assertEquals(1, restored.count { it.deletedAtMillis != null })
            assertTrue(restored.all { photo -> original.none { it.fileKey == photo.fileKey } })
            restored.forEach { photo -> vault.readPhoto(photo.fileKey, true).also { assertFalse(it.isRecycled); it.recycle() } }
            assertEquals("test-only credential placeholder", preferences.getString("groq", null))
            assertEquals("test-only model placeholder", modelFile.readText())

            val again = vault.previewRestore(Uri.fromFile(backup), "test-only नमस्ते password".toCharArray())
            val missing = original.last().fileKey
            File(context.cacheDir, "backup-work/${again.token}/$missing.jpg").delete()
            assertTrue(runCatching { vault.restoreBackup(again.token) }.isFailure)
            assertEquals(restored.toSet(), db.records().allPhotos().toSet())
            assertEquals("Original product", db.records().allProducts().single().name)
            restored.forEach { photo -> assertTrue(File(context.filesDir, "progress-photos/${photo.fileKey}.jpg").isFile) }
            assertEquals(4, File(context.filesDir, "progress-photos").listFiles()!!.size)
            preferences.edit().clear().commit()
            context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).edit().clear().commit()
        } finally { db.close(); root.deleteRecursively() }
        Unit
    }
}
