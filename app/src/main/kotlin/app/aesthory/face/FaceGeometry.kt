package app.aesthory.face

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class FacePoint(val x: Double, val y: Double) {
    operator fun minus(other: FacePoint) = FacePoint(x - other.x, y - other.y)
    operator fun plus(other: FacePoint) = FacePoint(x + other.x, y + other.y)
    operator fun div(value: Double) = FacePoint(x / value, y / value)
    fun length() = hypot(x, y)
    fun dot(other: FacePoint) = x * other.x + y * other.y
}

enum class PresentationGoal { DOCUMENT, MORE_LENGTH, MORE_WIDTH, SOFTER_PRESENTATION, MORE_DEFINITION }
enum class CaptureIssue { ONE_FACE_REQUIRED, INCOMPLETE_LANDMARKS, IMAGE_TOO_SMALL, CROPPED_FACE, FACE_TOO_SMALL, LEVEL_CAMERA, FACE_FORWARD, RELAX_EXPRESSION }

data class FaceMetrics(
    val faceWidthToHeight: Double,
    val jawToCheekWidth: Double,
    val innerToOuterEyeSpan: Double,
    val lowerFaceShare: Double,
    val imageLeftEyeTiltDegrees: Double,
    val imageRightEyeTiltDegrees: Double,
)

sealed interface FaceReview {
    data class Recapture(val issues: List<CaptureIssue>) : FaceReview
    data class Measured(val metrics: FaceMetrics, val priorities: List<String>) : FaceReview
}

/** All inputs are image-pixel coordinates, never independently scaled normalized x/y values. */
fun reviewFaceGeometry(
    points: List<FacePoint>,
    imageWidth: Int,
    imageHeight: Int,
    faceCount: Int = 1,
    expressionStrength: Double = 0.0,
    goal: PresentationGoal = PresentationGoal.DOCUMENT,
): FaceReview {
    if (faceCount != 1) return FaceReview.Recapture(listOf(CaptureIssue.ONE_FACE_REQUIRED))
    val required = listOf(10, 152, 234, 454, 172, 397, 33, 133, 362, 263, 2)
    if (points.size <= 454 || required.any { !points[it].x.isFinite() || !points[it].y.isFinite() }) {
        return FaceReview.Recapture(listOf(CaptureIssue.INCOMPLETE_LANDMARKS))
    }
    if (imageWidth < 512 || imageHeight < 512) return FaceReview.Recapture(listOf(CaptureIssue.IMAGE_TOO_SMALL))
    if (required.any { points[it].x !in 0.0..imageWidth.toDouble() || points[it].y !in 0.0..imageHeight.toDouble() }) {
        return FaceReview.Recapture(listOf(CaptureIssue.CROPPED_FACE))
    }
    fun distance(a: Int, b: Int) = (points[a] - points[b]).length()
    val height = distance(10, 152)
    val width = distance(234, 454)
    val eyeCenters = listOf((points[33] + points[133]) / 2.0, (points[263] + points[362]) / 2.0).sortedBy { it.x }
    val eyeAxis = eyeCenters[1] - eyeCenters[0]
    if (height < 200 || width < 140 || eyeAxis.length() < 20 || distance(33, 263) < 20 || distance(33, 133) < 5 || distance(263, 362) < 5) {
        return FaceReview.Recapture(listOf(CaptureIssue.FACE_TOO_SMALL))
    }
    val horizontal = eyeAxis / eyeAxis.length()
    val down = FacePoint(-horizontal.y, horizontal.x)
    val roll = Math.toDegrees(atan2(horizontal.y, horizontal.x))
    val cheekMidpoint = (points[234] + points[454]) / 2.0
    val poseSkew = abs((points[2] - cheekMidpoint).dot(horizontal)) / width
    // Capture heuristics, not clinical pose/appearance thresholds. Validate on representative photos.
    val issues = buildList {
        if (abs(roll) > 8) add(CaptureIssue.LEVEL_CAMERA)
        if (poseSkew > 0.15) add(CaptureIssue.FACE_FORWARD)
        if (!expressionStrength.isFinite() || expressionStrength > 0.35) add(CaptureIssue.RELAX_EXPRESSION)
    }
    if (issues.isNotEmpty()) return FaceReview.Recapture(issues)
    fun tilt(outer: Int, inner: Int): Double {
        val delta = points[outer] - points[inner]
        return Math.toDegrees(atan2(-delta.dot(down), abs(delta.dot(horizontal))))
    }
    val eyePairs = listOf(33 to 133, 263 to 362).sortedBy { points[it.first].x }
    val metrics = FaceMetrics(
        width / height, distance(172, 397) / width,
        distance(133, 362) / distance(33, 263), distance(2, 152) / height,
        tilt(eyePairs[0].first, eyePairs[0].second), tilt(eyePairs[1].first, eyePairs[1].second),
    )
    val styleOption = when (goal) {
        PresentationGoal.DOCUMENT -> "Use this as a baseline. Repeat the same lighting, distance and neutral expression before comparing proportions."
        PresentationGoal.MORE_LENGTH -> "For a visually longer presentation, compare crown volume and less side volume in your hairstyle. This is a styling preference, not a change to bone structure."
        PresentationGoal.MORE_WIDTH -> "For a visually wider presentation, compare side volume and wider frame silhouettes. Keep the camera setup identical when judging the result."
        PresentationGoal.SOFTER_PRESENTATION -> "Compare softer hair or facial-hair outlines and rounded frame shapes. Choose by your own preferred presentation, not an ideal-face score."
        PresentationGoal.MORE_DEFINITION -> "Compare a cleaner hair or facial-hair outline and consistent neutral lighting. Avoid interpreting shadows or weight changes as measured bone changes."
    }
    return FaceReview.Measured(metrics, listOf(
        styleOption,
        "Choose one reversible styling change at a time and record what you prefer in comparable photos.",
        "These are apparent 2D surface proportions. A photo cannot establish hidden facial bones, exact anatomy, or a scientifically objective PSL rating.",
    ))
}
