package app.aesthory.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.backup.RecordSnapshot
import app.aesthory.library.ProductEntity
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
class LibraryMigrationTest {
    @Test fun exportedV2DatabaseAndItsBackupUpgradeWithoutLosingHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "library-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("app.aesthory.data.AesthoryDatabase/2.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
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
            old.execSQL("INSERT INTO profiles VALUES (1, 'V2 profile', 'UNSPECIFIED', 'DIRECT', 'SKIN')")
            old.execSQL("INSERT INTO categories VALUES ('cat', 'Care')")
            old.execSQL("INSERT INTO routines VALUES ('routine', 'Original routine', 10, 0)")
            old.execSQL("INSERT INTO habits VALUES ('habit', 'routine', 0, 1, 'ACTIVE')")
            old.execSQL("INSERT INTO habit_versions VALUES ('habit', 1, 'Original habit', 'cat', 'Keep instructions', 2, 20000, NULL, 'daily', NULL, '', 'CHECKBOX', NULL, NULL, '[]')")
            old.execSQL("INSERT INTO completion_events VALUES (1, 'habit', 1, 20000, 10, 'DONE', NULL, '[]')")
            repeat(entities.length()) { index ->
                val table = entities.getJSONObject(index).getString("tableName")
                val rows = JSONArray()
                old.rawQuery("SELECT * FROM " + table, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { position, column ->
                            row.put(column, when (cursor.getType(position)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(position)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(position)
                                else -> cursor.getString(position)
                            })
                        }
                        rows.put(row)
                    }
                }
                data.put(table, rows)
            }
            old.version = 2
        }
        val upgraded = Room.databaseBuilder(context, AesthoryDatabase::class.java, name).addMigrations(AesthoryDatabase.MIGRATION_2_3, AesthoryDatabase.MIGRATION_3_4, AesthoryDatabase.MIGRATION_4_5, AesthoryDatabase.MIGRATION_5_6).build()
        try {
            val dao = upgraded.records()
            assertEquals("V2 profile", dao.getProfile()!!.displayName)
            assertEquals("", dao.allVersions().single().purpose)
            assertTrue(dao.allVersions().single().tags.isEmpty())
            assertEquals(1, dao.allEvents().size)
            val oldBackup = JSONObject().put("format", 1).put("schema", 2).put("createdAtMillis", 123).put("tables", data)
            val decoded = RecordSnapshot.decode(oldBackup.toString().toByteArray())
            RecordSnapshot.validate(context, decoded)
            dao.saveProduct(ProductEntity("new", "New record"))
            RecordSnapshot.replace(upgraded, decoded)
            assertTrue(dao.allProducts().isEmpty())
            assertEquals("Keep instructions", dao.allVersions().single().instructions)
            assertEquals(1, dao.allEvents().single().definitionVersion)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
