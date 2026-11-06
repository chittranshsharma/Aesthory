package app.aesthory.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import app.aesthory.data.AesthoryDatabase
import app.aesthory.routines.CompletionInput
import app.aesthory.timing.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class BackupSummary(val createdAtMillis: Long, val routines: Int, val checkIns: Int, val photos: Int, val binnedPhotos: Int, val products: Int = 0, val references: Int = 0, val reminders: Int = 0, val timers: Int = 0, val nutritionDays: Int = 0)

/** Exact allowlist: credentials, preferences and optional model files cannot enter this format. */
object RecordSnapshot {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val v2Tables = listOf("profiles", "measurements", "categories", "routines", "habits", "habit_versions", "completion_events", "progress_photos")
    private val v3Tables = listOf("profiles", "measurements", "categories", "routines", "products", "personal_references", "product_observations",
        "habits", "habit_versions", "habit_product_links", "habit_reference_links", "completion_events", "progress_photos")
    private val v4Tables = v3Tables + listOf("active_timer", "habit_reminders", "reminder_preferences")
    private val v5Tables = v4Tables + "weekly_targets"
    val tables = v5Tables + listOf("nutrition_days", "nutrition_imports")
    suspend fun capture(db: AesthoryDatabase, at: Long, moment: TimingMoment? = null): JSONObject = db.withTransaction {
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
        db.records().getTimer()?.let { timer ->
            val portable = TimerMath.portable(timer, moment ?: run { require(!timer.running); TimingMoment(at, 0, -1, java.time.ZoneOffset.UTC) })
            data.getJSONArray("active_timer").getJSONObject(0).put("accumulatedMillis", portable.accumulatedMillis)
                .put("startedElapsedMillis", JSONObject.NULL).put("startedWallMillis", JSONObject.NULL).put("recovery", TimerRecovery.BACKUP.name)
        }
        data.getJSONArray("reminder_preferences").let { rows -> repeat(rows.length()) { rows.getJSONObject(it).put("paused", 1) } }
        JSONObject().put("format", 1).put("schema", 6).put("createdAtMillis", at).put("tables", data)
    }
    fun encode(snapshot: JSONObject): ByteArray = snapshot.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    fun decode(bytes: ByteArray): JSONObject {
        require(bytes.size in 1..MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        checkDepth(text)
        val parser = JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid backup records")
        require(parser.nextClean().code == 0 && root.keys().asSequence().toSet() == setOf("format", "schema", "createdAtMillis", "tables"))
        if (root.get("format").toString() != "1" || root.get("schema").toString() !in setOf("2", "3", "4", "5", "6")) throw VaultUserError("This backup version isn't supported here. Keep the file and update Aesthory before trying again.")
        require(root.get("createdAtMillis").toString().toLong() >= 0)
        if (root.get("schema").toString() == "2") {
            val data = root.getJSONObject("tables")
            require(data.keys().asSequence().toSet() == v2Tables.toSet())
            val definitions = data.getJSONArray("habit_versions")
            repeat(definitions.length()) { index ->
                val row = definitions.getJSONObject(index)
                require(!row.has("purpose") && !row.has("tags") && !row.has("shortInstructions"))
                row.put("purpose", "").put("tags", "[]").put("shortInstructions", "")
            }
            (v3Tables - v2Tables.toSet()).forEach { data.put(it, JSONArray()) }
            root.put("schema", 3)
        }
        if (root.get("schema").toString() == "3") {
            val data = root.getJSONObject("tables")
            require(data.keys().asSequence().toSet() == v3Tables.toSet())
            val events = data.getJSONArray("completion_events")
            repeat(events.length()) { index -> val row = events.getJSONObject(index); require(!row.has("timedSeconds")); row.put("timedSeconds", JSONObject.NULL) }
            (v4Tables - v3Tables.toSet()).forEach { data.put(it, JSONArray()) }
            root.put("schema", 4)
        }
        if (root.get("schema").toString() == "4") {
            val data = root.getJSONObject("tables")
            require(data.keys().asSequence().toSet() == v4Tables.toSet())
            data.put("weekly_targets", JSONArray())
            root.put("schema", 5)
        }
        if (root.get("schema").toString() == "5") {
            val data = root.getJSONObject("tables")
            require(data.keys().asSequence().toSet() == v5Tables.toSet())
            data.put("nutrition_days", JSONArray()).put("nutrition_imports", JSONArray())
            root.put("schema", 6)
        }
        // A restored clock never runs by itself, and imported reminder schedules start paused.
        root.getJSONObject("tables").getJSONArray("reminder_preferences").let { rows -> repeat(rows.length()) {
            val row = rows.getJSONObject(it)
            require(row.get("paused") is Number && row.get("paused").toString().toLongOrNull() in setOf(0L, 1L))
            row.put("paused", 1)
        } }
        require(root.getJSONObject("tables").keys().asSequence().toSet() == tables.toSet())
        return root
    }
    fun summary(snapshot: JSONObject): BackupSummary {
        val data = snapshot.getJSONObject("tables")
        val photos = data.getJSONArray("progress_photos")
        val binned = (0 until photos.length()).count { !photos.getJSONObject(it).isNull("deletedAtMillis") }
        return BackupSummary(snapshot.getLong("createdAtMillis"), data.getJSONArray("routines").length(), data.getJSONArray("completion_events").length(), photos.length(), binned,
            data.getJSONArray("products").length(), data.getJSONArray("personal_references").length(), data.getJSONArray("habit_reminders").length(), data.getJSONArray("active_timer").length(), data.getJSONArray("nutrition_days").length())
    }
    suspend fun validate(context: Context, snapshot: JSONObject) {
        val staging = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        try {
            replace(staging, snapshot)
            val dao = staging.records()
            val nutrition = dao.allNutrition()
            val receipts = dao.allNutritionImports().associateBy { it.hash }
            nutrition.forEach { row -> row.sourceHash?.let { require(receipts[it]?.day == row.day) } }
            val nutritionDays = nutrition.map { it.day }.toSet()
            require(receipts.values.all { it.day in nutritionDays })
            dao.allWeeklyTargets() // Validate target ranges and Monday boundaries.
            val profiles = dao.allProfiles()
            require(profiles.size <= 1 && profiles.all { it.id == 1L })
            dao.allMeasurements().forEach { require(it.id > 0 && it.recordedAtMillis >= 0) } // Also forces validated measurement constructors.
            dao.allRoutines().forEach { require(it.name.isNotBlank() && it.createdAtMillis >= 0) }
            dao.getCategories().forEach { require(it.name.isNotBlank()) }
            val versions = dao.allVersions()
            versions.forEach { v -> require(v.version > 0 && v.title.isNotBlank() && (v.estimatedMinutes == null || v.estimatedMinutes > 0)); v.schedule.toDomain() }
            versions.forEach { v -> require(v.purpose.length <= 1000 && v.shortInstructions.length <= 2000 && v.tags.size <= 10 && v.tags.all { it.isNotBlank() && it.length <= 40 } && v.tags.distinct().size == v.tags.size) }
            dao.allProducts(); dao.allReferences(); dao.allObservations()
            require(dao.allProductLinks().groupingBy { it.habitId to it.version }.eachCount().values.all { it <= 20 })
            require(dao.allReferenceLinks().groupingBy { it.habitId to it.version }.eachCount().values.all { it <= 20 })
            val byVersion = versions.associateBy { it.habitId to it.version }
            dao.allHabits().forEach { require(it.position >= 0 && byVersion.containsKey(it.id to it.currentVersion)) }
            dao.allEvents().forEach { event ->
                require(event.id > 0 && event.recordedAtMillis >= 0)
                LocalDate.ofEpochDay(event.dateDay)
                CompletionInput(event.status, event.value, event.checkedItems).validateFor(requireNotNull(byVersion[event.habitId to event.definitionVersion]).tracking)
                event.timedSeconds?.let { seconds ->
                    require(seconds in 0..MAX_TIMER_MILLIS / 1000 && event.status in setOf(app.aesthory.routines.CompletionStatus.DONE, app.aesthory.routines.CompletionStatus.PARTIAL))
                    if (byVersion[event.habitId to event.definitionVersion]!!.tracking.kind == app.aesthory.routines.TrackingKind.DURATION) require(event.value == seconds.toDouble())
                }
            }
            require(dao.allTimers().size <= 1 && dao.allTimers().all { !it.running && it.recovery == TimerRecovery.BACKUP })
            require(dao.allReminders().count { it.enabled } <= 100)
            require(dao.allReminderPreferences().size <= 1 && dao.allReminderPreferences().all { it.paused })
            if (dao.allReminders().isNotEmpty()) require(dao.allReminderPreferences().size == 1)
            require(dao.allPhotos().size <= 500)
            staging.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
        } finally { staging.close() }
    }
    suspend fun replace(db: AesthoryDatabase, snapshot: JSONObject) = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        val data = snapshot.getJSONObject("tables")
        sql.execSQL("DELETE FROM reminder_runtime")
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
                            if (column in setOf("archived", "enabled", "paused", "quietEnabled", "goalAlerted")) require(number in 0..1)
                            if (column in setOf("minuteOfDay", "quietStart", "quietEnd")) require(number in 0..1439)
                            if (column == "boot") require(number in -1..Int.MAX_VALUE)
                            if (table == "weekly_targets" && column == "completed") require(number in 1..10000)
                            if (table == "weekly_targets" && column == "minutes") require(number in 1..10080)
                            values.put(column, number)
                        }
                        type == "REAL" -> { require(value is Number && value.toDouble().isFinite()); values.put(column, value.toDouble()) }
                        type == "TEXT" -> {
                            require(value is String && value.length <= 1_000_000)
                            if (column == "tracking_items" || column == "checkedItems" || column == "tags") {
                                checkDepth(value)
                                val array = JSONArray(value)
                                require(array.length() <= 1000)
                                repeat(array.length()) { position ->
                                    if (column != "checkedItems") require(array.get(position) is String)
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
