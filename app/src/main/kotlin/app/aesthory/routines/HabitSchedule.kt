package app.aesthory.routines

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Calendar intent stays local; the notification layer resolves it into zoned instants. */
data class HabitSchedule(
    val startDate: LocalDate,
    val recurrence: Recurrence = Recurrence.Daily,
    val endDate: LocalDate? = null,
) {
    init {
        require(endDate == null || !endDate.isBefore(startDate)) {
            "The end date must be on or after the start date."
        }
    }

    fun isDueOn(date: LocalDate): Boolean {
        if (date.isBefore(startDate) || (endDate != null && date.isAfter(endDate))) {
            return false
        }

        return when (val rule = recurrence) {
            Recurrence.Daily -> true
            is Recurrence.Weekdays -> date.dayOfWeek in rule.days
            is Recurrence.EveryNDays ->
                ChronoUnit.DAYS.between(startDate, date) % rule.interval.toLong() == 0L
        }
    }
}

sealed interface Recurrence {
    data object Daily : Recurrence

    data class Weekdays(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) { "Select at least one weekday." }
        }
    }

    data class EveryNDays(val interval: Int) : Recurrence {
        init {
            require(interval > 0) { "The day interval must be positive." }
        }
    }
}
