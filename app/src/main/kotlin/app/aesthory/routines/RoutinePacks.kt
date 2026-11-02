package app.aesthory.routines

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class PackHabit(val title: String, val category: String, val instructions: String, val minutes: Int?, val schedule: HabitSchedule, val tracking: TrackingSettings,
    val purpose: String = "", val tags: List<String> = emptyList(), val shortInstructions: String = "") {
    init {
        require(title.isNotBlank() && title.length <= 120)
        require(category.isNotBlank() && category.length <= 80)
        require(instructions.length <= 8000 && (minutes == null || minutes in 1..1440))
        require(tracking.items.size <= 50 && tracking.items.all { it.length <= 240 } && (tracking.unit?.length ?: 0) <= 40)
        require(purpose.length <= 1000 && shortInstructions.length <= 2000 && tags.size <= 10 && tags.all { it.isNotBlank() && it.length <= 40 } && tags.distinct().size == tags.size)
    }
}
data class RoutinePack(val name: String, val habits: List<PackHabit>) {
    init { require(name.isNotBlank() && name.length <= 120 && habits.size in 1..50) }
    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(RoutinePacks.encode(this)).joinToString("") { "%02x".format(it) }
}

object RoutinePacks {
    const val MAX_BYTES = 256 * 1024
    fun encode(pack: RoutinePack): ByteArray {
        val version = if (pack.habits.any { it.purpose.isNotEmpty() || it.tags.isNotEmpty() || it.shortInstructions.isNotEmpty() }) 2 else 1
        val habits = pack.habits.map { habit ->
            val schedule = JSONObject().put("start", habit.schedule.startDate.toString()).put("end", habit.schedule.endDate?.toString() ?: JSONObject.NULL)
            when (val rule = habit.schedule.recurrence) {
                Recurrence.Daily -> schedule.put("type", "daily")
                is Recurrence.EveryNDays -> schedule.put("type", "interval").put("interval", rule.interval)
                is Recurrence.Weekdays -> schedule.put("type", "weekdays").put("days", JSONArray(rule.days.sortedBy { it.value }.map { it.value }))
            }
            val item = JSONObject().put("title", habit.title).put("category", habit.category).put("instructions", habit.instructions)
                .put("minutes", habit.minutes ?: JSONObject.NULL).put("schedule", schedule)
                .put("tracking", JSONObject().put("kind", habit.tracking.kind.name).put("target", habit.tracking.target ?: JSONObject.NULL)
                    .put("unit", habit.tracking.unit ?: JSONObject.NULL).put("items", JSONArray(habit.tracking.items)))
            if (version == 2) item.put("purpose", habit.purpose).put("tags", JSONArray(habit.tags)).put("shortInstructions", habit.shortInstructions)
            item
        }
        val result = JSONObject().put("type", "aesthory.routine").put("version", version).put("name", pack.name).put("habits", JSONArray(habits)).toString().toByteArray(Charsets.UTF_8)
        require(result.size <= MAX_BYTES) { "This routine is too large to share in one file." }
        return result
    }

    fun decode(bytes: ByteArray): RoutinePack {
        require(bytes.size in 1..MAX_BYTES) { "Choose a routine file under 256 KB." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        checkNesting(text)
        val root = JSONObject(text)
        root.keysOnly(setOf("type", "version", "name", "habits"))
        val version = root.exactInt("version")
        require(root.string("type") == "aesthory.routine" && version in 1..2) { "This routine file version isn't supported." }
        val array = root.getJSONArray("habits")
        require(array.length() in 1..50)
        return RoutinePack(root.string("name"), List(array.length()) { index ->
            val item = array.getJSONObject(index)
            item.keysOnly(setOf("title", "category", "instructions", "minutes", "schedule", "tracking") + if (version == 2) setOf("purpose", "tags", "shortInstructions") else emptySet())
            val rule = item.getJSONObject("schedule")
            rule.keysOnly(setOf("start", "end", "type", "interval", "days"))
            val recurrence = when (rule.string("type")) {
                "daily" -> Recurrence.Daily
                "interval" -> Recurrence.EveryNDays(rule.exactInt("interval"))
                "weekdays" -> {
                    val days = rule.getJSONArray("days")
                    require(days.length() in 1..7)
                    val values = List(days.length()) { position ->
                        val value = days.get(position)
                        require(value is Number && value.toDouble() % 1.0 == 0.0 && value.toInt() in 1..7)
                        DayOfWeek.of(value.toInt())
                    }
                    require(values.distinct().size == values.size)
                    Recurrence.Weekdays(values.toSet())
                }
                else -> error("Unsupported schedule")
            }
            val tracking = item.getJSONObject("tracking")
            tracking.keysOnly(setOf("kind", "target", "unit", "items"))
            val checklist = tracking.getJSONArray("items")
            require(checklist.length() <= 50)
            val target = if (tracking.isNull("target")) null else (tracking.get("target") as? Number)?.toDouble() ?: error("Invalid target")
            PackHabit(item.string("title"), item.string("category"), item.string("instructions"), if (item.isNull("minutes")) null else item.exactInt("minutes"),
                HabitSchedule(startDate = LocalDate.parse(rule.string("start")), recurrence = recurrence, endDate = if (rule.isNull("end")) null else LocalDate.parse(rule.string("end"))),
                TrackingSettings(TrackingKind.valueOf(tracking.string("kind")), target, if (tracking.isNull("unit")) null else tracking.string("unit"),
                    List(checklist.length()) { checklist.get(it) as? String ?: error("Invalid checklist item") }),
                if (version == 2) item.string("purpose") else "",
                if (version == 2) item.getJSONArray("tags").let { tags -> require(tags.length() <= 10); List(tags.length()) { tags.get(it) as? String ?: error("Invalid tag") } } else emptyList(),
                if (version == 2) item.string("shortInstructions") else "")
        })
    }
    fun readableText(pack: RoutinePack): String = buildString {
        appendLine(pack.name)
        appendLine("Aesthory routine plan")
        appendLine("Products, personal references and private history are excluded.")
        pack.habits.forEachIndexed { index, habit ->
            appendLine(); appendLine((index + 1).toString() + ". " + habit.title)
            appendLine("Category: " + habit.category)
            val frequency = when (val rule = habit.schedule.recurrence) {
                Recurrence.Daily -> "Every day"
                is Recurrence.EveryNDays -> "Every " + rule.interval + " days"
                is Recurrence.Weekdays -> rule.days.sortedBy { it.value }.joinToString { it.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) }
            }
            appendLine(frequency + " · from " + habit.schedule.startDate + (habit.schedule.endDate?.let { " through " + it } ?: ""))
            habit.minutes?.let { appendLine("About " + it + " minutes") }
            if (habit.purpose.isNotBlank()) appendLine("Why: " + habit.purpose)
            if (habit.tags.isNotEmpty()) appendLine("Tags: " + habit.tags.joinToString())
            appendLine(habit.instructions.ifBlank { "No instructions added." })
            if (habit.shortInstructions.isNotBlank()) appendLine("Busy-day version: " + habit.shortInstructions)
            val tracking = habit.tracking
            appendLine(when (tracking.kind) {
                TrackingKind.CHECKBOX -> "Tracking: check it off"
                TrackingKind.COUNT -> "Tracking: count · target " + tracking.target
                TrackingKind.DURATION -> "Tracking: time in seconds · target " + tracking.target
                TrackingKind.VALUE -> "Tracking: " + tracking.unit + (tracking.target?.let { " · target " + it } ?: "")
                TrackingKind.RATING -> "Tracking: rating 1–" + tracking.target?.toInt()
                TrackingKind.CHECKLIST -> "Tracking checklist:\n" + tracking.items.joinToString("\n") { "[ ] " + it }
            })
        }
    }
    private fun checkNesting(text: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "This file is too deeply nested." } }
                '}', ']' -> { depth--; require(depth >= 0) }
            }
        }
        require(depth == 0 && !quoted)
    }
    private fun JSONObject.keysOnly(allowed: Set<String>) { require(keys().asSequence().all { it in allowed }) { "Unexpected private or unsupported fields in this routine file." } }
    private fun JSONObject.string(key: String) = get(key) as? String ?: error("Invalid $key")
    private fun JSONObject.exactInt(key: String): Int {
        val number = get(key) as? Number ?: error("Invalid $key")
        val value = number.toDouble()
        require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())
        return number.toInt()
    }
}
