package app.aesthory.workouts

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

internal const val HEVY_HEADER = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe"
internal fun hevyFixture(weight: String = "60", title: String = "Push", date: String = "4 Nov 2026"): String = "$HEVY_HEADER\n$title,\"$date, 17:00\",\"$date, 18:00\",,Bench Press,,,0,normal,$weight,8,,,8"

class HevyCsvTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun parse(csv: String) = HevyCsv.parse(csv.toByteArray(), zone)
    @Test fun quotedNotesCommaDecimalAndMissingValuesSurvive() {
        val csv = hevyFixture("\"60,5\"", "\"Push, pull\"").replace("Bench Press,,,", "Bench Press,1,\"Slow, then\ncontrolled\",")
        val bundle = parse("\uFEFF$csv\r\n").single()
        assertEquals("Push, pull", bundle.workout.title)
        assertEquals(60.5, bundle.sets.single().weight!!, 0.0)
        assertEquals("Slow, then\ncontrolled", bundle.sets.single().notes)
        assertNull(bundle.sets.single().distance)
        assertEquals(3600000L, bundle.workout.endMillis - bundle.workout.startMillis)
        assertEquals("Asia/Kolkata", bundle.workout.zone)
    }
    @Test fun imperialAndIsoDatesKeepSourceUnits() {
        val csv = hevyFixture("0").replace("weight_kg", "weight_lbs").replace("distance_km", "distance_miles").replace("4 Nov 2026, 17:00", "2026-11-04T17:00").replace("4 Nov 2026, 18:00", "2026-11-04T18:00")
        val set = parse(csv).single().sets.single()
        assertEquals("lb", set.weightUnit)
        assertEquals("mi", set.distanceUnit)
        assertEquals(0.0, set.weight!!, 0.0)
        assertNull(parse(hevyFixture("")).single().sets.single().weight)
    }
    @Test fun malformedPartialAndAmbiguousFilesFailEntirely() {
        val valid = hevyFixture()
        val cases = listOf(valid + "\nPush,bad", valid + "\n\"unterminated", valid.replace("60,8", "-60,8"), valid.replace("60,8", "NaN,8"), valid.replace("60,8", "60,1.5"), valid.replace("4 Nov 2026", "31 Feb 2026"), valid.replace("18:00", "16:00"), valid.replace("weight_kg", "weight"), valid.replace("title,start_time", "title,title"), valid + "\n" + valid.substringAfter('\n'))
        cases.forEach { assertTrue(runCatching { parse(it) }.exceptionOrNull() is WorkoutImportError) }
    }
    @Test fun dstGapAndRepeatedHourRequireUnambiguousDates() {
        listOf("2026-03-08T02:15", "2026-11-01T01:15").forEach { date ->
            val text = hevyFixture().replace("4 Nov 2026, 17:00", date).replace("4 Nov 2026, 18:00", date)
            assertTrue(runCatching { HevyCsv.parse(text.toByteArray(), ZoneId.of("America/New_York")) }.isFailure)
        }
    }
    @Test fun identitySurvivesRenameWhileChangedValuesChangeContent() {
        val original = parse(hevyFixture()).single()
        val renamed = parse(hevyFixture("65", "Evening")).single()
        assertEquals(original.workout.id, renamed.workout.id)
        assertNotEquals(original.workout.contentHash, renamed.workout.contentHash)
        assertEquals(original.workout.contentHash, parse(hevyFixture() + "\n").single().workout.contentHash)
    }
    @Test fun boundedInputRejectsInvalidEncodingAndExcessRows() {
        assertTrue(runCatching { HevyCsv.parse(ByteArray(HevyCsv.MAX_BYTES + 1), zone) }.isFailure)
        assertTrue(runCatching { HevyCsv.parse(byteArrayOf(0xc3.toByte(), 0x28), zone) }.isFailure)
        assertTrue(runCatching { parse(hevyFixture().replace("Bench Press", "x".repeat(241))) }.isFailure)
        assertTrue(runCatching { parse(HEVY_HEADER) }.isFailure)
    }
}
