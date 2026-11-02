package app.aesthory.ui

import app.aesthory.data.HabitVersionEntity
import app.aesthory.data.StoredSchedule
import app.aesthory.library.HabitLinks
import app.aesthory.routines.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitEditorInputTest {
    private val day = LocalDate.of(2026, 10, 31)
    private fun input() = HabitEditorInput(title = "My action", categoryId = "category", start = day.toString())
    @Test fun basicActionNeedsOnlyNameAndCategoryAndDoesNotInventOptionalValues() {
        val draft = input().toDraft()
        assertEquals(TrackingSettings(), draft.tracking)
        assertNull(draft.estimatedMinutes)
        assertNull(draft.schedule.endDate)
        assertEquals("", draft.purpose)
    }
    @Test fun emptyDaysReversedDatesFractionalMinutesAndInvalidGoalsHaveSpecificErrors() {
        assertTrue("schedule" in input().copy(frequency = "weekdays").errors())
        assertTrue("schedule" in input().copy(end = day.minusDays(1).toString()).errors())
        assertTrue("schedule" in input().copy(frequency = "interval", interval = "0").errors())
        assertTrue("minutes" in input().copy(minutes = "2.5").errors())
        assertTrue("tracking" in input().copy(kind = TrackingKind.VALUE, unit = "").errors())
        assertTrue("tracking" in input().copy(kind = TrackingKind.COUNT, target = "1.5").errors())
        assertTrue("tracking" in input().copy(kind = TrackingKind.RATING, target = "101").errors())
        assertTrue("tracking" in input().copy(kind = TrackingKind.CHECKLIST, checklist = "Same\nSame").errors())
        assertTrue("tags" in input().copy(tags = "same, same").errors())
    }
    @Test fun chosenDaysAndMinuteGoalsBecomeCorrectCalendarAndSecondRecords() {
        val draft = input().copy(frequency = "weekdays", days = listOf(1, 5), kind = TrackingKind.DURATION, target = "2.5", tags = " morning, travel ").toDraft()
        assertEquals(150.0, draft.tracking.target!!, 0.0)
        assertEquals(listOf("morning", "travel"), draft.tags)
        assertFalse(draft.schedule.isDueOn(day))
        assertTrue(draft.schedule.isDueOn(day.plusDays(2)))
    }
    @Test fun openingAndSavingAnExistingDurationDoesNotChangeItsSecondGoal() {
        listOf(1, 20, 61, 125, 3599).forEach { seconds ->
            val definition = HabitVersionEntity("id", 1, "Time", "category", "", null, StoredSchedule.from(HabitSchedule(day)), TrackingSettings(TrackingKind.DURATION, seconds.toDouble()))
            val form = HabitEditorInput.from(definition, HabitLinks(emptyList(), emptyList()))
            assertTrue("Goal " + seconds + ": " + form.errors(), form.errors().isEmpty())
            assertEquals(seconds.toDouble(), form.toDraft().tracking.target!!, 0.0)
        }
    }
}
