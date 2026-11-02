package app.aesthory.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import app.aesthory.profile.AgeBand
import app.aesthory.profile.BodyMeasurements
import app.aesthory.profile.CoachingTone
import app.aesthory.coaching.CoachMode
import app.aesthory.routines.CompletionStatus
import app.aesthory.routines.HabitSchedule
import app.aesthory.routines.HabitState
import app.aesthory.routines.Recurrence
import app.aesthory.routines.TrackingSettings
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val displayName: String?,
    val ageBand: AgeBand,
    val tone: CoachingTone,
    val coaches: Set<CoachMode>,
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"])],
    indices = [Index("profileId")],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val recordedAtMillis: Long,
    @Embedded val values: BodyMeasurements,
)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "habits",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"])],
    indices = [Index("routineId")],
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val position: Int,
    val currentVersion: Int,
    val state: HabitState = HabitState.ACTIVE,
)

data class StoredSchedule(
    val startDay: Long,
    val endDay: Long?,
    val recurrence: String,
    val interval: Int?,
    val weekdays: String,
) {
    fun toDomain() = HabitSchedule(
        startDate = LocalDate.ofEpochDay(startDay),
        endDate = endDay?.let(LocalDate::ofEpochDay),
        recurrence = when (recurrence) {
            "daily" -> Recurrence.Daily
            "interval" -> Recurrence.EveryNDays(requireNotNull(interval))
            "weekdays" -> Recurrence.Weekdays(weekdays.split(',').map { DayOfWeek.of(it.toInt()) }.toSet())
            else -> error("Unsupported stored recurrence: $recurrence")
        },
    )

    companion object {
        fun from(schedule: HabitSchedule): StoredSchedule {
            val rule = schedule.recurrence
            return StoredSchedule(
                schedule.startDate.toEpochDay(), schedule.endDate?.toEpochDay(),
                when (rule) { Recurrence.Daily -> "daily"; is Recurrence.Weekdays -> "weekdays"; is Recurrence.EveryNDays -> "interval" },
                (rule as? Recurrence.EveryNDays)?.interval,
                (rule as? Recurrence.Weekdays)?.days?.sortedBy { it.value }?.joinToString(",") { it.value.toString() } ?: "",
            )
        }
    }
}

@Entity(
    tableName = "habit_versions", primaryKeys = ["habitId", "version"],
    foreignKeys = [
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("categoryId")],
)
data class HabitVersionEntity(
    val habitId: String,
    val version: Int,
    val title: String,
    val categoryId: String,
    val instructions: String,
    val estimatedMinutes: Int?,
    @Embedded(prefix = "schedule_") val schedule: StoredSchedule,
    @Embedded(prefix = "tracking_") val tracking: TrackingSettings,
    @ColumnInfo(defaultValue = "''") val purpose: String = "",
    @ColumnInfo(defaultValue = "'[]'") val tags: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "''") val shortInstructions: String = "",
)

data class CurrentHabit(
    @Embedded val definition: HabitVersionEntity,
    val position: Int,
    val state: HabitState,
    val routineId: String = "",
)

@Entity(
    tableName = "completion_events",
    foreignKeys = [ForeignKey(
        entity = HabitVersionEntity::class,
        parentColumns = ["habitId", "version"], childColumns = ["habitId", "definitionVersion"],
    )],
    indices = [Index(value = ["habitId", "definitionVersion"]), Index(value = ["habitId", "dateDay"])],
)
data class CompletionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val definitionVersion: Int,
    val dateDay: Long,
    val recordedAtMillis: Long,
    val status: CompletionStatus,
    val value: Double?,
    val checkedItems: List<Int>,
)
