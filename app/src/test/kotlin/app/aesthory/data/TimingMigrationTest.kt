package app.aesthory.data

import android.content.Context
import android.database.Cursor
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
class TimingMigrationTest {
    @Test fun v3RecordsAndBackupsRetainHistoryWhileNewTimingFieldsStartEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "timing-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("app.aesthory.data.AesthoryDatabase/3.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val data = JSONObject()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                repeat(indices.length()) { old.execSQL(indices.getJSONObject(it).getString("createSql").replace("$" + "{TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO profiles VALUES (1, 'V3 profile', 'UNSPECIFIED', 'DIRECT', 'SKIN')")
            old.execSQL("INSERT INTO categories VALUES ('cat', 'Care')")
            old.execSQL("INSERT INTO routines VALUES ('routine', 'Original routine', 10, 0)")
            old.execSQL("INSERT INTO habits VALUES ('habit', 'routine', 0, 1, 'ACTIVE')")
            old.execSQL("INSERT INTO habit_versions (habitId, version, title, categoryId, instructions, estimatedMinutes, schedule_startDay, schedule_endDay, schedule_recurrence, schedule_interval, schedule_weekdays, tracking_kind, tracking_target, tracking_unit, tracking_items, purpose, tags, shortInstructions) VALUES ('habit', 1, 'Original habit', 'cat', 'Keep steps', 2, 20000, NULL, 'daily', NULL, '', 'CHECKBOX', NULL, NULL, '[]', 'Old reason', '[]', 'Short step')")
            old.execSQL("INSERT INTO completion_events VALUES (1, 'habit', 1, 20000, 10, 'DONE', NULL, '[]')")
            old.execSQL("INSERT INTO products VALUES ('product', 'Old product', '', '', '', 'Label', 'Note', '', NULL, NULL, NULL, 'READY', 0)")
            repeat(entities.length()) { index ->
                val table = entities.getJSONObject(index).getString("tableName")
                val rows = JSONArray()
                old.rawQuery("SELECT * FROM " + table, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { position, column -> row.put(column, when (cursor.getType(position)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(position)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(position)
                            else -> cursor.getString(position)
                        }) }
                        rows.put(row)
                    }
                }
                data.put(table, rows)
            }
            old.version = 3
        }
        val upgraded = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).addMigrations(AesthoryDatabase.MIGRATION_3_4, AesthoryDatabase.MIGRATION_4_5).build()
        try {
            val dao = upgraded.records()
            assertEquals("V3 profile", dao.getProfile()!!.displayName)
            val target = app.aesthory.progress.WeeklyTarget(java.time.LocalDate.of(2026,11,2).toEpochDay(), 5, 90)
            dao.saveWeeklyTarget(target)
            assertEquals(target, dao.allWeeklyTargets().single())
            val roundtrip = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(upgraded, 123)))
            RecordSnapshot.validate(context, roundtrip)
            RecordSnapshot.replace(upgraded, roundtrip)
            assertEquals(target, dao.allWeeklyTargets().single())
            assertEquals("Old reason", dao.allVersions().single().purpose)
            assertNull(dao.allEvents().single().timedSeconds)
            assertEquals("Old product", dao.allProducts().single().name)
            assertNull(dao.getTimer())
            assertTrue(dao.allReminders().isEmpty())
            val oldBackup = JSONObject().put("format", 1).put("schema", 3).put("createdAtMillis", 123).put("tables", data)
            val decoded = RecordSnapshot.decode(oldBackup.toString().toByteArray())
            RecordSnapshot.validate(context, decoded)
            RecordSnapshot.replace(upgraded, decoded)
            assertEquals("Keep steps", dao.allVersions().single().instructions)
            assertEquals(1, dao.allEvents().size)
            assertNull(dao.allEvents().single().timedSeconds)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
