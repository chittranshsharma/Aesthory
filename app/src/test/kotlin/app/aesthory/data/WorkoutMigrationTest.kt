package app.aesthory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.backup.RecordSnapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutMigrationTest {
    @Test fun v6DatabaseAndBackupUpgradeWithoutLosingNutrition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "workout-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("app.aesthory.data.AesthoryDatabase/6.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val data = JSONObject()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                repeat(indices.length()) { old.execSQL(indices.getJSONObject(it).getString("createSql").replace("$" + "{TABLE_NAME}", table)) }
                if (table != "reminder_runtime") data.put(table, JSONArray())
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            val day = java.time.LocalDate.of(2026,11,2).toEpochDay()
            old.execSQL("INSERT INTO nutrition_days VALUES (20000, 1800, NULL, NULL, NULL, NULL, '00000000-0000-0000-0000-000000000001', 123)")
            old.execSQL("INSERT INTO weekly_targets VALUES (?, 5, 90)", arrayOf(day))
            data.getJSONArray("weekly_targets").put(JSONObject().put("startDay", day).put("completed", 5).put("minutes", 90))
            old.version = 6
        }
        val db = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).addMigrations(AesthoryDatabase.MIGRATION_6_7).build()
        try {
            assertEquals(5, db.records().allWeeklyTargets().single().completed)
            assertTrue(db.records().allWorkouts().isEmpty())
            assertEquals(1800.0, db.records().allNutrition().single().calories!!, 0.0)
            val oldBackup = JSONObject().put("format", 1).put("schema", 6).put("createdAtMillis", 123).put("tables", data)
            val decoded = RecordSnapshot.decode(oldBackup.toString().toByteArray())
            RecordSnapshot.validate(context, decoded)
            RecordSnapshot.replace(db, decoded)
            assertEquals(90, db.records().allWeeklyTargets().single().minutes)
            assertTrue(db.records().allNutritionImports().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
