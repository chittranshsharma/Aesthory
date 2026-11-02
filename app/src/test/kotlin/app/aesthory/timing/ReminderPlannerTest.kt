package app.aesthory.timing

import app.aesthory.routines.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReminderPlannerTest {
    private fun moment(iso: String, zone: String = "Asia/Kolkata") = TimingMoment(Instant.parse(iso).toEpochMilli(), 1000, 1, ZoneId.of(zone))
    private fun time(slot: ReminderSlot, now: TimingMoment) = Instant.ofEpochMilli(slot.atMillis).atZone(now.zone)
    @Test fun weekdayAndLongIntervalRulesHonorDateBoundsWithoutWalkingEveryDay() {
        val now = moment("2026-11-02T05:00:00Z")
        val day = LocalDate.of(2026, 11, 2)
        val weekdays = HabitSchedule(day, Recurrence.Weekdays(setOf(DayOfWeek.FRIDAY)))
        assertEquals(LocalDate.of(2026, 11, 6), time(ReminderPlanner.next(weekdays, HabitReminder("id", 12 * 60), ReminderPreferences(), now)!!, now).toLocalDate())
        val interval = HabitSchedule(day, Recurrence.EveryNDays(3650))
        assertEquals(day.plusDays(3650), time(ReminderPlanner.next(interval, HabitReminder("id", 8 * 60), ReminderPreferences(), now)!!, now).toLocalDate())
        assertNull(ReminderPlanner.next(weekdays.copy(endDate = day.plusDays(2)), HabitReminder("id", 12 * 60), ReminderPreferences(), now))
    }
    @Test fun quietHoursPostponeOvernightAndDaytimeRequestsToTheirEnd() {
        val now = moment("2026-11-02T16:00:00Z")
        val schedule = HabitSchedule(LocalDate.of(2026, 11, 2))
        val slot = ReminderPlanner.next(schedule, HabitReminder("id", 23 * 60), ReminderPreferences(), now)!!
        assertEquals(LocalDate.of(2026, 11, 3), time(slot, now).toLocalDate())
        assertEquals(LocalTime.of(8, 0), time(slot, now).toLocalTime())
        assertEquals(LocalDate.of(2026, 11, 2).toEpochDay(), slot.day)
        val prefs = ReminderPreferences(quietStart = 12 * 60, quietEnd = 14 * 60)
        val at = moment("2026-11-02T07:30:00Z")
        assertEquals(LocalTime.of(14, 0), Instant.ofEpochMilli(ReminderPlanner.quietUntil(at.wall, prefs, at)).atZone(at.zone).toLocalTime())
    }
    @Test fun dstGapAndOverlapUseOneLocalOccurrenceAndTimezoneChangesKeepChosenLocalTime() {
        val zone = "America/New_York"
        val spring = moment("2026-03-08T05:00:00Z", zone)
        val prefs = ReminderPreferences(quietEnabled = false)
        val schedule = HabitSchedule(LocalDate.of(2026, 1, 1))
        assertEquals(LocalTime.of(3, 30), time(ReminderPlanner.next(schedule, HabitReminder("id", 150), prefs, spring)!!, spring).toLocalTime())
        val autumn = moment("2026-11-01T04:00:00Z", zone)
        val first = ReminderPlanner.next(schedule, HabitReminder("id", 90), prefs, autumn)!!
        assertEquals(ZoneOffset.ofHours(-4), time(first, autumn).offset)
        val afterFirst = autumn.copy(wall = first.atMillis + 1)
        val next = ReminderPlanner.next(schedule, HabitReminder("id", 90), prefs, afterFirst, setOf(first.day))!!
        assertEquals(LocalDate.of(2026, 11, 2), time(next, autumn).toLocalDate())
        val india = spring.copy(zone = ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalTime.of(9, 0), time(ReminderPlanner.next(schedule, HabitReminder("id", 540), prefs, india)!!, india).toLocalTime())
    }
    @Test fun pauseDisabledRulesAndCompletedDaysNeverPlanTheExcludedOccurrence() {
        val now = moment("2026-11-02T00:00:00Z")
        val day = LocalDate.of(2026, 11, 2)
        val schedule = HabitSchedule(day)
        val rule = HabitReminder("id", 600)
        assertNull(ReminderPlanner.next(schedule, rule.copy(enabled = false), ReminderPreferences(), now))
        assertNull(ReminderPlanner.next(schedule, rule, ReminderPreferences(paused = true), now))
        val next = ReminderPlanner.next(schedule, rule, ReminderPreferences(), now, setOf(day.toEpochDay()))!!
        assertEquals(day.plusDays(1).toEpochDay(), next.day)
    }
}
