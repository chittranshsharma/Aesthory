package app.aesthory.face

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceGeometryTest {
    private fun face(): List<FacePoint> = MutableList(478) { FacePoint(500.0, 500.0) }.apply {
        this[10] = FacePoint(500.0, 180.0); this[152] = FacePoint(500.0, 820.0)
        this[234] = FacePoint(260.0, 450.0); this[454] = FacePoint(740.0, 450.0)
        this[172] = FacePoint(320.0, 700.0); this[397] = FacePoint(680.0, 700.0)
        this[33] = FacePoint(340.0, 380.0); this[133] = FacePoint(440.0, 390.0)
        this[362] = FacePoint(560.0, 390.0); this[263] = FacePoint(660.0, 380.0)
        this[2] = FacePoint(500.0, 560.0)
    }

    @Test fun `ratios are derived from measured pixel geometry`() {
        val report = reviewFaceGeometry(face(), 1000, 1000) as FaceReview.Measured
        assertEquals(0.75, report.metrics.faceWidthToHeight, 0.00001)
        assertEquals(0.75, report.metrics.jawToCheekWidth, 0.00001)
        assertEquals(0.375, report.metrics.innerToOuterEyeSpan, 0.00001)
        assertEquals(260.0 / 640.0, report.metrics.lowerFaceShare, 0.00001)
        assertTrue(report.metrics.imageLeftEyeTiltDegrees > 0)
    }

    @Test fun `ratios are invariant to uniform scaling and translation`() {
        val original = (reviewFaceGeometry(face(), 1000, 1000) as FaceReview.Measured).metrics
        val scaled = face().map { FacePoint(it.x * 1.5 + 40, it.y * 1.5 + 80) }
        val result = (reviewFaceGeometry(scaled, 1600, 1700) as FaceReview.Measured).metrics
        assertEquals(original.faceWidthToHeight, result.faceWidthToHeight, 0.00001)
        assertEquals(original.jawToCheekWidth, result.jawToCheekWidth, 0.00001)
        assertEquals(original.innerToOuterEyeSpan, result.innerToOuterEyeSpan, 0.00001)
    }

    @Test fun `roll is corrected for eye tilt within the capture tolerance`() {
        val radians = Math.toRadians(5.0)
        val rotated = face().map {
            val x = it.x - 500; val y = it.y - 500
            FacePoint(500 + x * cos(radians) - y * sin(radians), 500 + x * sin(radians) + y * cos(radians))
        }
        val before = (reviewFaceGeometry(face(), 1000, 1000) as FaceReview.Measured).metrics
        val after = (reviewFaceGeometry(rotated, 1000, 1000) as FaceReview.Measured).metrics
        assertEquals(before.imageLeftEyeTiltDegrees, after.imageLeftEyeTiltDegrees, 0.00001)
        assertEquals(before.faceWidthToHeight, after.faceWidthToHeight, 0.00001)
    }

    @Test fun `multiple faces missing landmarks and nonfinite coordinates yield recapture`() {
        assertTrue(reviewFaceGeometry(face(), 1000, 1000, faceCount = 2) is FaceReview.Recapture)
        assertTrue(reviewFaceGeometry(emptyList(), 1000, 1000) is FaceReview.Recapture)
        val invalid = face().toMutableList().apply { this[10] = FacePoint(Double.NaN, 20.0) }
        assertTrue(reviewFaceGeometry(invalid, 1000, 1000) is FaceReview.Recapture)
    }

    @Test fun `small cropped rotated or expressive photos do not produce confident measurements`() {
        assertTrue(reviewFaceGeometry(face(), 256, 256) is FaceReview.Recapture)
        assertTrue(reviewFaceGeometry(face(), 1000, 1000, expressionStrength = 0.8) is FaceReview.Recapture)
        val cropped = face().toMutableList().apply { this[152] = FacePoint(500.0, 1200.0) }
        assertTrue(reviewFaceGeometry(cropped, 1000, 1000) is FaceReview.Recapture)
        val tilted = face().toMutableList().apply {
            this[263] = FacePoint(660.0, 490.0); this[362] = FacePoint(560.0, 500.0)
        }
        assertTrue(reviewFaceGeometry(tilted, 1000, 1000) is FaceReview.Recapture)
    }

    @Test fun `presentation preferences change suggestions without changing measured facts`() {
        val longer = reviewFaceGeometry(face(), 1000, 1000, goal = PresentationGoal.MORE_LENGTH) as FaceReview.Measured
        val wider = reviewFaceGeometry(face(), 1000, 1000, goal = PresentationGoal.MORE_WIDTH) as FaceReview.Measured
        assertEquals(longer.metrics, wider.metrics)
        assertNotEquals(longer.priorities.first(), wider.priorities.first())
    }
}
