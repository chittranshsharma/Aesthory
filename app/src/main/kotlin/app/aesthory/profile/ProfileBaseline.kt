package app.aesthory.profile

import app.aesthory.coaching.CoachMode

enum class AgeBand { UNSPECIFIED, UNDER_18, ADULT }
enum class CoachingTone { SUPPORTIVE, DIRECT }

/** Optional, user-entered values in canonical units; dated updates belong in persistent history. */
data class BodyMeasurements(
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val shoulderCircumferenceCm: Double? = null,
    val upperArmCm: Double? = null,
    val thighCm: Double? = null,
    val bodyFatPercent: Double? = null,
) {
    init {
        val lengthsAndWeight = listOf(
            heightCm, weightKg, waistCm, chestCm,
            shoulderCircumferenceCm, upperArmCm, thighCm,
        )
        require(lengthsAndWeight.filterNotNull().all { it.isFinite() && it > 0 }) {
            "Entered measurements must be finite positive values; leave unknown values empty."
        }
        require(bodyFatPercent == null ||
            (bodyFatPercent.isFinite() && bodyFatPercent > 0 && bodyFatPercent < 100)) {
            "An entered body-fat percentage must be between 0 and 100, excluding the endpoints."
        }
    }
}

data class ProfileBaseline(
    val displayName: String? = null,
    val ageBand: AgeBand = AgeBand.UNSPECIFIED,
    val selectedCoaches: Set<CoachMode> = emptySet(),
    val tone: CoachingTone = CoachingTone.SUPPORTIVE,
    val measurements: BodyMeasurements = BodyMeasurements(),
)
