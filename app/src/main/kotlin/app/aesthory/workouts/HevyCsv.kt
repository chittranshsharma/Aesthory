package app.aesthory.workouts

import org.apache.commons.csv.CSVFormat
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale

class WorkoutImportError(message: String) : IllegalArgumentException(message)

object HevyCsv {
    const val MAX_BYTES = 5 * 1024 * 1024
    private val namedDate = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu, HH:mm[:ss]").toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    private val required = setOf("title", "start_time", "end_time", "exercise_title", "set_index", "set_type", "reps")
    private val optional = setOf("description", "exercise_notes", "superset_id", "weight_kg", "weight_lbs", "distance_km", "distance_miles", "duration_seconds", "rpe")

    fun parse(bytes: ByteArray, zone: ZoneId): List<WorkoutBundle> {
        if (bytes.size !in 1..MAX_BYTES) throw WorkoutImportError("Choose a Hevy workout CSV under 5 MB.")
        try {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            require(text.none { it.code < 32 && it !in "\n\r\t" })
            val sourceHash = digest(text)
            CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).get().parse(text.reader()).use { csv ->
                val rows = csv.iterator()
                require(rows.hasNext())
                val header = rows.next().toList().map { it.trim() }
                require(header.size == header.distinct().size && header.containsAll(required) && header.all { it in required + optional })
                require(header.count { it in setOf("weight_kg", "weight_lbs") } == 1 && header.count { it in setOf("distance_km", "distance_miles") } <= 1)
                val weightColumn = header.single { it == "weight_kg" || it == "weight_lbs" }
                val distanceColumn = header.singleOrNull { it == "distance_km" || it == "distance_miles" }
                val workouts = linkedMapOf<String, Workout>()
                val sets = linkedMapOf<String, MutableList<WorkoutSet>>()
                var count = 0
                while (rows.hasNext()) {
                    val row = rows.next()
                    count++
                    if (count > 20000) throw WorkoutImportError("This file has too many sets. Import up to 20,000 sets at a time.")
                    try {
                        require(row.size() == header.size)
                        fun field(name: String): String = header.indexOf(name).let { if (it < 0) "" else row[it].trim() }
                        fun number(name: String): Double? {
                            val raw = field(name)
                            if (raw.isEmpty()) return null
                            require(Regex("[0-9]+(?:[.,][0-9]+)?").matches(raw))
                            return raw.replace(',', '.').toDouble().also { require(it.isFinite()) }
                        }
                        fun date(name: String): LocalDateTime = field(name).let { raw ->
                            runCatching { LocalDateTime.parse(raw, namedDate) }.getOrElse { LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
                        }
                        val start = date("start_time")
                        val end = date("end_time")
                        val id = digest(start.toString())
                        val workout = Workout(id, field("title"), start.toString(), end.toString(), zone.id, start.atZone(zone).toInstant().toEpochMilli(), end.atZone(zone).toInstant().toEpochMilli(), field("description"), "0".repeat(64), sourceHash)
                        val prior = workouts[id]
                        require(prior == null || prior.copy(revision = workout.revision) == workout)
                        workouts[id] = prior ?: workout
                        require(workouts.size <= 1000)
                        val entries = sets.getOrPut(id) { mutableListOf() }
                        val reps = number("reps")?.also { require(it % 1.0 == 0.0 && it <= 100000) }?.toInt()
                        entries += WorkoutSet(id, entries.size, field("exercise_title"), field("set_index").toInt(), field("set_type"), number(weightColumn), if (weightColumn == "weight_kg") "kg" else "lb", reps,
                            distanceColumn?.let { number(it) }, if (distanceColumn == "distance_miles") "mi" else "km", number("duration_seconds"), number("rpe"), field("superset_id"), field("exercise_notes"))
                    } catch (_: Exception) {
                        throw WorkoutImportError("Workout row ${count + 1} has missing, conflicting or unsupported details. Nothing was imported. Export the original file again; dates must be English or ISO, with unambiguous local times.")
                    }
                }
                require(workouts.isNotEmpty())
                return workouts.values.map { workout ->
                    val entries = requireNotNull(sets[workout.id])
                    WorkoutBundle(workout.copy(contentHash = workoutContent(workout, entries)), entries).also(::validateWorkout)
                }
            }
        } catch (error: WorkoutImportError) { throw error }
        catch (_: Exception) { throw WorkoutImportError("That file isn't a supported Hevy workout export. Use the original English CSV with kg or lb columns. No workouts were saved.") }
    }
}
