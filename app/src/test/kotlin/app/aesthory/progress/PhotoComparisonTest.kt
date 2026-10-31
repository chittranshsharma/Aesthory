package app.aesthory.progress

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PhotoComparisonTest {
    private fun photo(day: Long, area: PhotoArea = PhotoArea.FACE, view: PhotoView = PhotoView.FRONT) = ProgressPhoto(
        UUID.randomUUID().toString(), day = day, createdAtMillis = day, area = area, view = view, note = "", fileKey = UUID.randomUUID().toString(), sha256 = "a".repeat(64), byteCount = 10, width = 100, height = 100)
    @Test fun `matching pair sorts earlier and later and excludes deleted photos`() {
        val early = photo(10); val late = photo(20)
        assertEquals(listOf(early, late), comparisonPair(listOf(late, early)))
        assertTrue(runCatching { comparisonPair(listOf(early, late.copy(deletedAtMillis = 1))) }.isFailure)
        assertTrue(runCatching { comparisonPair(listOf(early, early)) }.isFailure)
    }
    @Test fun `comparison needs matching area and view and keys cannot contain paths`() {
        assertTrue(runCatching { comparisonPair(listOf(photo(10), photo(20, PhotoArea.BODY))) }.isFailure)
        assertTrue(runCatching { comparisonPair(listOf(photo(10), photo(20, view = PhotoView.LEFT))) }.isFailure)
        assertFalse(validPhotoKey("../outside"))
        assertTrue(runCatching { photo(10).copy(fileKey = "../../outside") }.isFailure)
    }
}
