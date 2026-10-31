package app.aesthory.profile

import app.aesthory.coaching.CoachMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileBaselineTest {
    @Test
    fun `baseline can exist without identifiers photos or measurements`() {
        val baseline = ProfileBaseline(selectedCoaches = setOf(CoachMode.SKIN))

        assertNull(baseline.displayName)
        assertNull(baseline.measurements.heightCm)
        assertNull(baseline.measurements.weightKg)
        assertEquals(AgeBand.UNSPECIFIED, baseline.ageBand)
    }

    @Test
    fun `entered measurements preserve canonical units and leave unknown values empty`() {
        val measurements = BodyMeasurements(heightCm = 176.5, weightKg = 72.0, waistCm = 82.0)

        assertEquals(176.5, measurements.heightCm!!, 0.0)
        assertEquals(72.0, measurements.weightKg!!, 0.0)
        assertNull(measurements.bodyFatPercent)
        assertNull(measurements.shoulderCircumferenceCm)
    }

    @Test
    fun `zero negative and nonfinite measurements are rejected`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(heightCm = invalid) }
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(weightKg = invalid) }
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(waistCm = invalid) }
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(chestCm = invalid) }
                assertThrows(IllegalArgumentException::class.java) {
                    BodyMeasurements(shoulderCircumferenceCm = invalid)
                }
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(upperArmCm = invalid) }
                assertThrows(IllegalArgumentException::class.java) { BodyMeasurements(thighCm = invalid) }
            }
    }

    @Test
    fun `invalid body fat percentages are rejected rather than clamped`() {
        listOf(0.0, -1.0, 100.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                BodyMeasurements(bodyFatPercent = invalid)
            }
        }
    }
}
