package app.aesthory.routines

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitScheduleTest {
    @Test
    fun `daily schedule includes start and end but excludes dates outside them`() {
        val start = LocalDate.of(2026, 10, 31)
        val end = start.plusDays(2)
        val schedule = HabitSchedule(startDate = start, endDate = end)

        assertFalse(schedule.isDueOn(start.minusDays(1)))
        assertTrue(schedule.isDueOn(start))
        assertTrue(schedule.isDueOn(start.plusDays(1)))
        assertTrue(schedule.isDueOn(end))
        assertFalse(schedule.isDueOn(end.plusDays(1)))
    }

    @Test
    fun `selected weekdays work across a year boundary`() {
        val schedule = HabitSchedule(
            startDate = LocalDate.of(2026, 12, 30),
            recurrence = Recurrence.Weekdays(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)),
        )

        assertFalse(schedule.isDueOn(LocalDate.of(2026, 12, 30)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 12, 31)))
        assertFalse(schedule.isDueOn(LocalDate.of(2027, 1, 1)))
        assertTrue(schedule.isDueOn(LocalDate.of(2027, 1, 4)))
    }

    @Test
    fun `matching weekday before start is not due`() {
        val schedule = HabitSchedule(
            startDate = LocalDate.of(2026, 11, 2),
            recurrence = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY)),
        )

        assertFalse(schedule.isDueOn(schedule.startDate.minusWeeks(1)))
    }

    @Test
    fun `day interval stays anchored across month boundaries`() {
        val schedule = HabitSchedule(
            startDate = LocalDate.of(2026, 10, 31),
            recurrence = Recurrence.EveryNDays(3),
        )

        assertTrue(schedule.isDueOn(LocalDate.of(2026, 10, 31)))
        assertFalse(schedule.isDueOn(LocalDate.of(2026, 11, 1)))
        assertFalse(schedule.isDueOn(LocalDate.of(2026, 11, 2)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 11, 3)))
        assertTrue(schedule.isDueOn(LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `day interval counts leap day`() {
        val schedule = HabitSchedule(
            startDate = LocalDate.of(2028, 2, 28),
            recurrence = Recurrence.EveryNDays(2),
        )

        assertFalse(schedule.isDueOn(LocalDate.of(2028, 2, 29)))
        assertTrue(schedule.isDueOn(LocalDate.of(2028, 3, 1)))
    }

    @Test
    fun `one day interval matches daily recurrence`() {
        val start = LocalDate.of(2026, 10, 31)
        val schedule = HabitSchedule(start, Recurrence.EveryNDays(1))

        assertTrue(schedule.isDueOn(start))
        assertTrue(schedule.isDueOn(start.plusYears(1)))
        assertFalse(schedule.isDueOn(start.minusDays(1)))
    }

    @Test
    fun `interval match after end is not due`() {
        val start = LocalDate.of(2026, 10, 31)
        val schedule = HabitSchedule(start, Recurrence.EveryNDays(2), start.plusDays(3))

        assertTrue(schedule.isDueOn(start.plusDays(2)))
        assertFalse(schedule.isDueOn(start.plusDays(4)))
    }

    @Test
    fun `calendar recurrence is unaffected by a daylight saving clock change`() {
        val zone = ZoneId.of("America/New_York")
        val beforeChange = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, zone)
        val afterChange = beforeChange.plusDays(1)
        val schedule = HabitSchedule(beforeChange.toLocalDate(), Recurrence.EveryNDays(1))

        assertTrue(schedule.isDueOn(beforeChange.toLocalDate()))
        assertTrue(schedule.isDueOn(afterChange.toLocalDate()))
    }

    @Test
    fun `end before start is rejected`() {
        val start = LocalDate.of(2026, 10, 31)

        assertThrows(IllegalArgumentException::class.java) {
            HabitSchedule(startDate = start, endDate = start.minusDays(1))
        }
    }

    @Test
    fun `empty weekdays and nonpositive intervals are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Recurrence.Weekdays(emptySet())
        }
        listOf(0, -1).forEach { interval ->
            assertThrows(IllegalArgumentException::class.java) {
                Recurrence.EveryNDays(interval)
            }
        }
    }
}
