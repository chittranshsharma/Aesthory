package app.aesthory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoMigrationTest {
    @Test fun `v1 upgrade keeps profile dated measurements routines instructions and check ins`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "photo-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("app.aesthory.data.AesthoryDatabase/1.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                repeat(indices.length()) { old.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { old.execSQL(setup.getString(it)) }
            old.execSQL("INSERT INTO profiles VALUES (1, 'Old profile', 'UNSPECIFIED', 'DIRECT', 'SKIN')")
            old.execSQL("INSERT INTO measurements (id, profileId, recordedAtMillis, heightCm) VALUES (1, 1, 10, 180)")
            old.execSQL("INSERT INTO categories VALUES ('cat', 'Care')")
            old.execSQL("INSERT INTO routines VALUES ('routine', 'Original routine', 10, 0)")
            old.execSQL("INSERT INTO habits VALUES ('habit', 'routine', 0, 1, 'ACTIVE')")
            old.execSQL("INSERT INTO habit_versions VALUES ('habit', 1, 'Original habit', 'cat', 'Keep these instructions', 2, 20000, NULL, 'daily', NULL, '', 'CHECKBOX', NULL, NULL, '[]')")
            old.execSQL("INSERT INTO completion_events VALUES (1, 'habit', 1, 20000, 10, 'DONE', NULL, '[]')")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).addMigrations(AesthoryDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Old profile", upgraded.records().getProfile()!!.displayName)
            assertEquals(180.0, upgraded.records().allMeasurements().single().values.heightCm!!, 0.0)
            assertEquals("Keep these instructions", upgraded.records().allVersions().single().instructions)
            assertEquals(1, upgraded.records().allEvents().single().definitionVersion)
            assertTrue(upgraded.records().allPhotos().isEmpty())
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
