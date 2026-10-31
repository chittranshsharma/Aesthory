package app.aesthory.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import app.aesthory.data.AesthoryDatabase
import app.aesthory.routines.CompletionInput
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class BackupSummary(val createdAtMillis: Long, val routines: Int, val checkIns: Int, val photos: Int, val binnedPhotos: Int)

/** Exact allowlist: credentials, preferences and optional model files cannot enter this format. */
object RecordSnapshot {
    const val MAX_BYTES = 16 * 1024 * 1024
    val tables = listOf("profiles", "measurements", "categories", "routines", "habits", "habit_versions", "completion_events", "progress_photos")
    suspend fun capture(db: AesthoryDatabase, at: Long): JSONObject = db.withTransaction {
        val data = JSONObject()
        tables.forEach { table ->
            val rows = JSONArray()
            db.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                require(cursor.count <= 100_000)
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    cursor.columnNames.forEachIndexed { index, column ->
                        row.put(column, when (cursor.getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                            else -> error("Unexpected record type")
                        })
                    }
                    rows.put(row)
                }
            }
            data.put(table, rows)
        }
        JSONObject().put("format", 1).put("schema", 2).put("createdAtMillis", at).put("tables", data)
    }
    fun encode(snapshot: JSONObject): ByteArray = snapshot.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    fun decode(bytes: ByteArray): JSONObject {
        require(bytes.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        checkDepth(text)
        val parser = JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid backup records")
        require(parser.nextClean().code == 0 && root.keys().asSequence().toSet() == setOf("format", "schema", "createdAtMillis", "tables"))
        if (root.get("format").toString() != "1" || root.get("schema").toString() != "2") throw VaultUserError("This backup version isn't supported here. Keep the file and update Aesthory before trying again.")
        require(root.get("createdAtMillis").toString().toLong() >= 0)
        require(root.getJSONObject("tables").keys().asSequence().toSet() == tables.toSet())
        return root
    }
    fun summary(snapshot: JSONObject): BackupSummary {
        val data = snapshot.getJSONObject("tables")
        val photos = data.getJSONArray("progress_photos")
        val binned = (0 until photos.length()).count { !photos.getJSONObject(it).isNull("deletedAtMillis") }
        return BackupSummary(snapshot.getLong("createdAtMillis"), data.getJSONArray("routines").length(), data.getJSONArray("completion_events").length(), photos.length(), binned)
    }
    suspend fun validate(context: Context, snapshot: JSONObject) {
        val staging = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            replace(staging, snapshot)
            val dao = staging.records()
            val profiles = dao.allProfiles()
            require(profiles.size <= 1 && profiles.all { it.id == 1L })
            dao.allMeasurements().forEach { require(it.id > 0 && it.recordedAtMillis >= 0) } // Also forces validated measurement constructors.
            dao.allRoutines().forEach { require(it.name.isNotBlank() && it.createdAtMillis >= 0) }
            dao.getCategories().forEach { require(it.name.isNotBlank()) }
            val versions = dao.allVersions()
            versions.forEach { v -> require(v.version > 0 && v.title.isNotBlank() && (v.estimatedMinutes == null || v.estimatedMinutes > 0)); v.schedule.toDomain() }
            val byVersion = versions.associateBy { it.habitId to it.version }
            dao.allHabits().forEach { require(it.position >= 0 && byVersion.containsKey(it.id to it.currentVersion)) }
            dao.allEvents().forEach { event ->
                require(event.id > 0 && event.recordedAtMillis >= 0)
                LocalDate.ofEpochDay(event.dateDay)
                CompletionInput(event.status, event.value, event.checkedItems).validateFor(requireNotNull(byVersion[event.habitId to event.definitionVersion]).tracking)
            }
            require(dao.allPhotos().size <= 500)
            staging.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
        } finally { staging.close() }
    }
    suspend fun replace(db: AesthoryDatabase, snapshot: JSONObject) = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        val data = snapshot.getJSONObject("tables")
        tables.asReversed().forEach { sql.execSQL("DELETE FROM `$it`") }
        var total = 0
        tables.forEach { table ->
            val columns = linkedMapOf<String, String>()
            sql.query("PRAGMA table_info(`$table`)").use { cursor -> while (cursor.moveToNext()) columns[cursor.getString(1)] = cursor.getString(2) }
            val rows = data.getJSONArray(table)
            total += rows.length()
            require(total <= 100_000)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                require(row.keys().asSequence().toSet() == columns.keys)
                val values = ContentValues()
                columns.forEach { (column, type) ->
                    val value = row.get(column)
                    when {
                        value == JSONObject.NULL -> values.putNull(column)
                        type == "INTEGER" -> {
                            require(value is Number)
                            val number = value.toString().toLong()
                            if (column == "archived") require(number in 0..1)
                            values.put(column, number)
                        }
                        type == "REAL" -> { require(value is Number && value.toDouble().isFinite()); values.put(column, value.toDouble()) }
                        type == "TEXT" -> {
                            require(value is String && value.length <= 1_000_000)
                            if (column == "tracking_items" || column == "checkedItems") {
                                checkDepth(value)
                                val array = JSONArray(value)
                                require(array.length() <= 1000)
                                repeat(array.length()) { position ->
                                    if (column == "tracking_items") require(array.get(position) is String)
                                    else require(array.get(position) is Number && array.get(position).toString().toIntOrNull() != null)
                                }
                            }
                            values.put(column, value)
                        }
                        else -> error("Unsupported backup column")
                    }
                }
                sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }
    private fun checkDepth(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        text.forEach { character ->
            if (quoted) {
                when { escaped -> escaped = false; character == '\\' -> escaped = true; character == '"' -> quoted = false }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
}
