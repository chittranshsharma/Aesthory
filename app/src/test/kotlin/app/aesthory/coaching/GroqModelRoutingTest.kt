package app.aesthory.coaching

import org.junit.Assert.assertEquals
import org.junit.Test

class GroqModelRoutingTest {
    private val allowed = AiPreferences(
        enabled = true,
        keyConfigured = true,
        cloudProcessingAllowed = true,
        previewVisionAllowed = true,
    )
    private val available = GroqModel.entries.map { it.id }.toSet()

    @Test
    fun `disabled AI missing key and missing cloud consent prevent selection`() {
        val cases = listOf(
            allowed.copy(enabled = false) to ModelRoute.Reason.AI_DISABLED,
            allowed.copy(keyConfigured = false) to ModelRoute.Reason.KEY_REQUIRED,
            allowed.copy(cloudProcessingAllowed = false) to ModelRoute.Reason.CLOUD_CONSENT_REQUIRED,
        )
        cases.forEach { (preferences, reason) ->
            assertEquals(
                ModelRoute.Unavailable(reason),
                selectGroqModel(CoachingTask.QUICK_QUESTION, preferences, available),
            )
        }
    }

    @Test
    fun `quick and detailed tasks select the intended available model and output budget`() {
        assertEquals(
            ModelRoute.Selected(GroqModel.QUICK_TEXT, 1024),
            selectGroqModel(CoachingTask.QUICK_QUESTION, allowed, available),
        )
        assertEquals(
            ModelRoute.Selected(GroqModel.DETAILED_TEXT, 2048),
            selectGroqModel(CoachingTask.DETAILED_PLAN, allowed, available),
        )
    }

    @Test
    fun `text planning falls back only to an available approved text model`() {
        assertEquals(
            ModelRoute.Selected(GroqModel.QUICK_TEXT, 2048),
            selectGroqModel(CoachingTask.DETAILED_PLAN, allowed, setOf(GroqModel.QUICK_TEXT.id)),
        )
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.MODEL_UNAVAILABLE),
            selectGroqModel(CoachingTask.DETAILED_PLAN, allowed, setOf("enterprise/unapproved")),
        )
    }

    @Test
    fun `photo review selects vision with explicit image sharing and preview opt in`() {
        assertEquals(
            ModelRoute.Selected(GroqModel.PREVIEW_VISION, 1024),
            selectGroqModel(
                CoachingTask.PHOTO_REVIEW, allowed, available,
                imageCount = 1, selectedImageSharingAllowed = true,
            ),
        )
    }

    @Test
    fun `general AI consent does not authorize image sharing`() {
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.IMAGE_CONSENT_REQUIRED),
            selectGroqModel(CoachingTask.PHOTO_REVIEW, allowed, available, imageCount = 1),
        )
    }

    @Test
    fun `preview disabled prevents use of the preview vision model`() {
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.PREVIEW_VISION_DISABLED),
            selectGroqModel(
                CoachingTask.PHOTO_REVIEW, allowed.copy(previewVisionAllowed = false), available,
                imageCount = 1, selectedImageSharingAllowed = true,
            ),
        )
    }

    @Test
    fun `missing vision never falls back to a text only model`() {
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.MODEL_UNAVAILABLE),
            selectGroqModel(
                CoachingTask.PHOTO_REVIEW, allowed,
                setOf(GroqModel.QUICK_TEXT.id, GroqModel.DETAILED_TEXT.id),
                imageCount = 1, selectedImageSharingAllowed = true,
            ),
        )
    }

    @Test
    fun `photo review without a photo is rejected`() {
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.IMAGE_REQUIRED),
            selectGroqModel(
                CoachingTask.PHOTO_REVIEW, allowed, available,
                selectedImageSharingAllowed = true,
            ),
        )
    }

    @Test
    fun `invalid image counts cannot reach a model`() {
        listOf(-1, 4).forEach { count ->
            assertEquals(
                ModelRoute.Unavailable(ModelRoute.Reason.INVALID_IMAGE_COUNT),
                selectGroqModel(
                    CoachingTask.PHOTO_REVIEW, allowed, available,
                    imageCount = count, selectedImageSharingAllowed = true,
                ),
            )
        }
    }

    @Test
    fun `an image on a text task is not silently discarded`() {
        assertEquals(
            ModelRoute.Unavailable(ModelRoute.Reason.INVALID_IMAGE_COUNT),
            selectGroqModel(
                CoachingTask.QUICK_QUESTION, allowed, available,
                imageCount = 1, selectedImageSharingAllowed = true,
            ),
        )
    }
}
