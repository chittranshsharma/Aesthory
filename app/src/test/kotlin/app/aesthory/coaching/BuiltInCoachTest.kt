package app.aesthory.coaching

import app.aesthory.data.*
import app.aesthory.profile.AgeBand
import app.aesthory.routines.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BuiltInCoachTest {
    private val date = LocalDate.of(2026, 10, 31)
    private fun task(id: String, minutes: Int?, status: CompletionStatus? = null): TodayHabit {
        val definition = HabitVersionEntity(id, 1, id, "general", "", minutes, StoredSchedule.from(HabitSchedule(date)), TrackingSettings())
        return TodayHabit(CurrentHabit(definition, 0, HabitState.ACTIVE, routineId = id), status?.let {
            CompletionEventEntity(habitId = id, definitionVersion = 1, dateDay = date.toEpochDay(), recordedAtMillis = 0, status = it, value = null, checkedItems = emptyList())
        })
    }
    @Test fun `a shorter later step never jumps ahead of its routine's first unfinished step`() {
        val first = task("try-style", 3).let { it.copy(habit = it.habit.copy(routineId = "style", position = 0)) }
        val later = task("review-style", 1).let { it.copy(habit = it.habit.copy(routineId = "style", position = 1)) }
        assertEquals("try-style", guideToday(listOf(first, later)).next!!.habit.definition.habitId)
    }
    @Test fun `next action fits available time and excludes finished and skipped tasks`() {
        val report = guideToday(listOf(task("long", 15), task("short", 3), task("done", 1, CompletionStatus.DONE), task("skip", 1, CompletionStatus.SKIPPED)))
        assertEquals("short", report.next!!.habit.definition.habitId)
        assertEquals(1, report.done)
        assertEquals(4, report.total)
    }
    @Test fun `unknown duration is not invented and partial actions remain actionable`() {
        val report = guideToday(listOf(task("unknown", null, CompletionStatus.PARTIAL)))
        assertNotNull(report.next)
        assertTrue(report.detail.contains("No time estimate"))
        assertEquals(0, report.done)
    }
    @Test fun `skipped day is not described as fully completed`() {
        val report = guideToday(listOf(task("skip", 2, CompletionStatus.SKIPPED)))
        assertNull(report.next)
        assertEquals(0, report.done)
        assertFalse(report.detail.contains("Everything on your list is done"))
    }
    @Test fun `empty and completed plans have no next action and no invented streak`() {
        assertEquals(0, guideToday(emptyList()).total)
        val complete = guideToday(listOf(task("done", 2, CompletionStatus.DONE)))
        assertNull(complete.next)
        assertEquals(1, complete.done)
        assertTrue(complete.detail.contains("Everything on your list is done"))
    }
    @Test fun `long plans suggest reducing load without hiding tasks`() {
        val report = guideToday(listOf(task("a", 20), task("b", 10)))
        assertEquals(2, report.total)
        assertTrue(report.detail.contains("pause habits"))
    }
    @Test fun `search is case insensitive and adult procedural material stays age gated`() {
        assertTrue(CoachLibrary.find("MEWING", null, AgeBand.UNSPECIFIED).any { it.id == "mewing" })
        assertTrue(CoachLibrary.find("", GuideTopic.PROCEDURES, AgeBand.UNDER_18).isEmpty())
        assertTrue(CoachLibrary.find("", GuideTopic.PROCEDURES, AgeBand.UNSPECIFIED).isEmpty())
        assertFalse(CoachLibrary.find("", GuideTopic.PROCEDURES, AgeBand.ADULT).isEmpty())
        assertTrue(CoachLibrary.find("not-a-topic", null, AgeBand.ADULT).isEmpty())
    }
    @Test fun `starter suggestions respect selected focus without removing other choices`() {
        val result = StarterRoutines.suggested(setOf(CoachMode.PHYSIQUE))
        assertEquals(CoachMode.PHYSIQUE, result.first().focus)
        assertEquals(StarterRoutines.all.toSet(), result.toSet())
    }
}
