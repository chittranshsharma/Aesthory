package app.aesthory.routines

import org.junit.Assert.assertThrows
import org.junit.Test

class TrackingTest {
    @Test fun `ratings reject fractions and out of scale values`() {
        val settings = TrackingSettings(TrackingKind.RATING, target = 5.0)
        listOf(0.0, 6.0, 2.5, Double.NaN).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { CompletionInput(CompletionStatus.DONE, value).validateFor(settings) }
        }
        CompletionInput(CompletionStatus.DONE, 4.0).validateFor(settings)
    }
    @Test fun `checklists reject unknown repeated and incomplete done selections`() {
        val settings = TrackingSettings(TrackingKind.CHECKLIST, items = listOf("one", "two"))
        listOf(listOf(0), listOf(0, 0), listOf(0, 2)).forEach { indices ->
            assertThrows(IllegalArgumentException::class.java) { CompletionInput(CompletionStatus.DONE, checkedItems = indices).validateFor(settings) }
        }
        CompletionInput(CompletionStatus.PARTIAL, checkedItems = listOf(0)).validateFor(settings)
        CompletionInput(CompletionStatus.DONE, checkedItems = listOf(0, 1)).validateFor(settings)
    }
}
