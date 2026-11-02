package app.aesthory.timing

import app.aesthory.routines.HabitSchedule
import app.aesthory.routines.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class ReminderSlot(val atMillis: Long, val day: Long)

object ReminderPlanner {
    fun quietUntil(instant: Long, prefs: ReminderPreferences, now: TimingMoment): Long {
        if (!prefs.quietEnabled) return instant
        val local = Instant.ofEpochMilli(instant).atZone(now.zone)
        val minute = local.hour * 60 + local.minute
        val inQuiet = if (prefs.quietStart < prefs.quietEnd) minute in prefs.quietStart until prefs.quietEnd
            else minute >= prefs.quietStart || minute < prefs.quietEnd
        if (!inQuiet) return instant
        val endDay = if (prefs.quietStart > prefs.quietEnd && minute >= prefs.quietStart) local.toLocalDate().plusDays(1) else local.toLocalDate()
        return endDay.atTime(LocalTime.of(prefs.quietEnd / 60, prefs.quietEnd % 60)).atZone(now.zone).toInstant().toEpochMilli()
    }

    fun next(schedule: HabitSchedule, reminder: HabitReminder, prefs: ReminderPreferences, now: TimingMoment, excludedDays: Set<Long> = emptySet()): ReminderSlot? {
        if (!reminder.enabled || prefs.paused) return null
        val today = Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate()
        // Yesterday can be postponed into this morning by overnight quiet hours.
        var day = dueOnOrAfter(schedule, maxOf(today.minusDays(1), schedule.startDate)) ?: return null
        repeat(excludedDays.size + 4) {
            val local = day.atTime(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60).atZone(now.zone)
            // atZone shifts DST gaps forward and chooses the earlier occurrence in an overlap: once per local day.
            val at = quietUntil(local.toInstant().toEpochMilli(), prefs, now)
            if (at > now.wall && day.toEpochDay() !in excludedDays) return ReminderSlot(at, day.toEpochDay())
            day = dueOnOrAfter(schedule, day.plusDays(1)) ?: return null
        }
        return null
    }
    private fun dueOnOrAfter(schedule: HabitSchedule, from: LocalDate): LocalDate? {
        val start = maxOf(from, schedule.startDate)
        val result = when (val rule = schedule.recurrence) {
            Recurrence.Daily -> start
            is Recurrence.Weekdays -> (0L..6L).map(start::plusDays).first { it.dayOfWeek in rule.days }
            is Recurrence.EveryNDays -> {
                val days = ChronoUnit.DAYS.between(schedule.startDate, start)
                val remainder = days % rule.interval
                start.plusDays(if (remainder == 0L) 0 else rule.interval - remainder)
            }
        }
        return result.takeIf { schedule.endDate == null || it <= schedule.endDate }
    }
}
