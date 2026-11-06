package app.aesthory.workouts

import androidx.room.*
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

internal fun digest(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String,
    val title: String, val startLocal: String, val endLocal: String, val zone: String,
    val startMillis: Long, val endMillis: Long, val description: String,
    val contentHash: String, val sourceHash: String, val revision: String = UUID.randomUUID().toString(),
) {
    init {
        val start = LocalDateTime.parse(startLocal)
        val end = LocalDateTime.parse(endLocal)
        val timezone = ZoneId.of(zone)
        require(id == digest(startLocal))
        require(title.isNotBlank() && title.length <= 240 && description.length <= 4000)
        require(start.year in 1970..2100 && end.year in 1970..2100)
        require(timezone.rules.getValidOffsets(start).size == 1 && timezone.rules.getValidOffsets(end).size == 1)
        require(start.atZone(timezone).toInstant().toEpochMilli() == startMillis && end.atZone(timezone).toInstant().toEpochMilli() == endMillis)
        require(endMillis >= startMillis && endMillis - startMillis <= 7 * 86400000L)
        require(listOf(contentHash, sourceHash).all { it.matches(Regex("[a-f0-9]{64}")) })
        require(UUID.fromString(revision).toString() == revision)
    }
}

@Entity(tableName = "workout_sets", primaryKeys = ["workoutId", "position"],
    foreignKeys = [ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)])
data class WorkoutSet(
    val workoutId: String, val position: Int, val exercise: String, val setIndex: Int, val type: String,
    val weight: Double?, val weightUnit: String, val reps: Int?, val distance: Double?, val distanceUnit: String,
    val seconds: Double?, val rpe: Double?, val superset: String, val notes: String,
) {
    init {
        require(workoutId.matches(Regex("[a-f0-9]{64}")) && position in 0..1999 && setIndex in 0..999)
        require(exercise.isNotBlank() && exercise.length <= 240 && type.isNotBlank() && type.length <= 40)
        require(weightUnit in setOf("kg", "lb") && distanceUnit in setOf("km", "mi"))
        require(weight == null || weight.isFinite() && weight in 0.0..10000.0)
        require(reps == null || reps in 0..100000)
        require(distance == null || distance.isFinite() && distance in 0.0..10000.0)
        require(seconds == null || seconds.isFinite() && seconds in 0.0..604800.0)
        require(rpe == null || rpe.isFinite() && rpe in 0.0..10.0)
        require(superset.length <= 40 && notes.length <= 4000)
    }
}

data class WorkoutBundle(@Embedded val workout: Workout, @Relation(parentColumn = "id", entityColumn = "workoutId") val sets: List<WorkoutSet>)
data class WorkoutCandidate(val incoming: WorkoutBundle, val existing: WorkoutBundle?) {
    val unchanged get() = existing?.workout?.contentHash == incoming.workout.contentHash
    val changed get() = existing != null && !unchanged
}
data class WorkoutPreview(val items: List<WorkoutCandidate>) {
    val added get() = items.count { it.existing == null }
    val changed get() = items.count { it.changed }
    val skipped get() = items.count { it.unchanged }
}

/** Length-prefixing keeps commas, newlines and arbitrary imported notes unambiguous. */
fun workoutContent(workout: Workout, sets: List<WorkoutSet>): String {
    val fields = listOf(workout.title, workout.startLocal, workout.endLocal, workout.zone, workout.description) +
        sets.sortedBy { it.position }.flatMap { listOf(it.position, it.exercise, it.setIndex, it.type, it.weight, it.weightUnit, it.reps, it.distance, it.distanceUnit, it.seconds, it.rpe, it.superset, it.notes).map { value -> value?.toString() ?: "<missing>" } }
    return digest(fields.joinToString("") { "${it.length}:$it" })
}

fun validateWorkout(bundle: WorkoutBundle) {
    val sets = bundle.sets.sortedBy { it.position }
    require(sets.size in 1..2000 && sets.map { it.position } == sets.indices.toList())
    require(sets.all { it.workoutId == bundle.workout.id })
    require(sets.map { it.exercise to it.setIndex }.distinct().size == sets.size)
    require(workoutContent(bundle.workout, sets) == bundle.workout.contentHash)
}
