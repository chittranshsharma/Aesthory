package app.aesthory.routines

import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoutinePacksTest {
    private val day = LocalDate.of(2026, 10, 31)
    private fun example() = RoutinePack("My routine", listOf(
        PackHabit("Care", "Skin", "Gentle care\nनमस्ते \"quoted\"", 3, HabitSchedule(day), TrackingSettings()),
        PackHabit("Check in", "Style", "One thing at a time", null, HabitSchedule(day, Recurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), day.plusDays(30)), TrackingSettings(TrackingKind.CHECKLIST, items = listOf("First", "Second"))),
        PackHabit("Count", "Movement", "", 5, HabitSchedule(day, Recurrence.EveryNDays(2)), TrackingSettings(TrackingKind.COUNT, target = 4.0)),
    ))
    @Test fun `routine roundtrip preserves ordered definitions schedules and tracking`() {
        val original = example()
        assertEquals(original, RoutinePacks.decode(RoutinePacks.encode(original)))
        assertEquals(original.fingerprint, RoutinePacks.decode(RoutinePacks.encode(original)).fingerprint)
    }
    @Test fun `full record fields and future versions are rejected rather than imported`() {
        val root = JSONObject(RoutinePacks.encode(example()).toString(Charsets.UTF_8))
        root.put("apiKey", "not-a-real-key")
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
        root.remove("apiKey")
        root.put("version", 2)
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
    }
    @Test fun `fractional integers and invalid recurrence are rejected`() {
        val root = JSONObject(RoutinePacks.encode(example()).toString(Charsets.UTF_8))
        root.getJSONArray("habits").getJSONObject(0).put("minutes", 1.5)
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
        root.getJSONArray("habits").getJSONObject(0).put("minutes", 3)
        root.getJSONArray("habits").getJSONObject(1).getJSONObject("schedule").put("days", org.json.JSONArray(listOf(1, 1)))
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
    }
    @Test fun `oversized malformed and non UTF8 inputs are rejected`() {
        listOf(ByteArray(RoutinePacks.MAX_BYTES + 1), byteArrayOf(0xC3.toByte(), 0x28), "{broken".toByteArray()).forEach {
            assertTrue(runCatching { RoutinePacks.decode(it) }.isFailure)
        }
    }
    @Test fun `deep nesting is rejected before recursive parsing but quoted braces remain data`() {
        val deep = ("[".repeat(3000) + "0" + "]".repeat(3000)).toByteArray()
        val failure = runCatching { RoutinePacks.decode(deep) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        val pack = example().let { it.copy(habits = listOf(it.habits.first().copy(instructions = "Literal brackets { [ \\\"quoted\\\" ] } ".repeat(100)))) }
        assertEquals(pack, RoutinePacks.decode(RoutinePacks.encode(pack)))
    }
    @Test fun `invalid tracking and reversed date bounds are rejected before persistence`() {
        val root = JSONObject(RoutinePacks.encode(example()).toString(Charsets.UTF_8))
        val item = root.getJSONArray("habits").getJSONObject(0)
        item.getJSONObject("tracking").put("target", 10)
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
        item.getJSONObject("tracking").put("target", JSONObject.NULL)
        item.getJSONObject("schedule").put("end", day.minusDays(1).toString())
        assertTrue(runCatching { RoutinePacks.decode(root.toString().toByteArray()) }.isFailure)
    }
    @Test fun `all other tracking kinds preserve units and targets`() {
        val settings = listOf(TrackingSettings(TrackingKind.DURATION, 60.0), TrackingSettings(TrackingKind.VALUE, unit = "cm"), TrackingSettings(TrackingKind.RATING, 5.0))
        settings.forEach { tracking ->
            val pack = RoutinePack("Example", listOf(example().habits.first().copy(tracking = tracking)))
            assertEquals(pack, RoutinePacks.decode(RoutinePacks.encode(pack)))
        }
    }
}
