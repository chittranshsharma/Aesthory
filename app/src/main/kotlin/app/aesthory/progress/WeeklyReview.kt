package app.aesthory.progress

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.aesthory.routines.CompletionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@Entity(tableName = "weekly_targets")
data class WeeklyTarget(@PrimaryKey val startDay: Long, val completed: Int?, val minutes: Int?) {
    init {
        require(weekStart(LocalDate.ofEpochDay(startDay)).toEpochDay() == startDay)
        require(completed == null || completed in 1..10000)
        require(minutes == null || minutes in 1..10080)
    }
}

data class WeeklyLog(val id: Long, val habitId: String, val dateDay: Long, val status: CompletionStatus,
    val value: Double?, val timedSeconds: Long?, val kind: String)
data class WeekReview(val completed: Int, val partial: Int, val skipped: Int, val recordedSeconds: Double?, val days: Int)
fun reviewWeek(logs: List<WeeklyLog>, start: LocalDate, today: LocalDate): WeekReview {
    val latest = logs.filter { it.dateDay >= start.toEpochDay() && it.dateDay <= minOf(start.plusDays(6), today).toEpochDay() }
        .groupBy { it.habitId to it.dateDay }.values.map { rows -> rows.maxBy { it.id } }
    val worked = latest.filter { it.status == CompletionStatus.DONE || it.status == CompletionStatus.PARTIAL }
    val times = worked.mapNotNull { it.timedSeconds?.toDouble() ?: it.value?.takeIf { _ -> it.kind == "DURATION" } }
    return WeekReview(latest.count { it.status == CompletionStatus.DONE }, latest.count { it.status == CompletionStatus.PARTIAL },
        latest.count { it.status == CompletionStatus.SKIPPED }, times.takeIf { it.isNotEmpty() }?.sum(), worked.map { it.dateDay }.distinct().size)
}
