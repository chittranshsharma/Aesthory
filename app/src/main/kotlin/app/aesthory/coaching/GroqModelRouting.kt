package app.aesthory.coaching

/** Candidates checked against Groq's catalog; account access is supplied by a future connection check. */
enum class GroqModel(val id: String) {
    QUICK_TEXT("openai/gpt-oss-20b"),
    DETAILED_TEXT("openai/gpt-oss-120b"),
    PREVIEW_VISION("qwen/qwen3.8-27b"),
}

enum class CoachingTask {
    QUICK_QUESTION,
    DETAILED_PLAN,
    PHOTO_REVIEW,
}

data class AiPreferences(
    val enabled: Boolean = false,
    val keyConfigured: Boolean = false,
    val cloudProcessingAllowed: Boolean = false,
    val previewVisionAllowed: Boolean = false,
)

sealed interface ModelRoute {
    data class Selected(val model: GroqModel, val maxOutputTokens: Int) : ModelRoute
    data class Unavailable(val reason: Reason) : ModelRoute

    enum class Reason {
        AI_DISABLED,
        KEY_REQUIRED,
        CLOUD_CONSENT_REQUIRED,
        IMAGE_CONSENT_REQUIRED,
        PREVIEW_VISION_DISABLED,
        IMAGE_REQUIRED,
        INVALID_IMAGE_COUNT,
        MODEL_UNAVAILABLE,
    }
}

/** A local policy only: it makes no requests, stores no key, and cannot verify a billing plan. */
fun selectGroqModel(
    task: CoachingTask,
    preferences: AiPreferences,
    availableModelIds: Set<String>,
    imageCount: Int = 0,
    selectedImageSharingAllowed: Boolean = false,
): ModelRoute {
    fun unavailable(reason: ModelRoute.Reason) = ModelRoute.Unavailable(reason)

    if (!preferences.enabled) return unavailable(ModelRoute.Reason.AI_DISABLED)
    if (!preferences.keyConfigured) return unavailable(ModelRoute.Reason.KEY_REQUIRED)
    if (!preferences.cloudProcessingAllowed) {
        return unavailable(ModelRoute.Reason.CLOUD_CONSENT_REQUIRED)
    }

    if (imageCount < 0 || (task != CoachingTask.PHOTO_REVIEW && imageCount != 0)) {
        return unavailable(ModelRoute.Reason.INVALID_IMAGE_COUNT)
    }

    if (task == CoachingTask.PHOTO_REVIEW) {
        if (imageCount == 0) return unavailable(ModelRoute.Reason.IMAGE_REQUIRED)
        if (imageCount > 3) return unavailable(ModelRoute.Reason.INVALID_IMAGE_COUNT)
        if (!selectedImageSharingAllowed) {
            return unavailable(ModelRoute.Reason.IMAGE_CONSENT_REQUIRED)
        }
        if (!preferences.previewVisionAllowed) {
            return unavailable(ModelRoute.Reason.PREVIEW_VISION_DISABLED)
        }
        return if (GroqModel.PREVIEW_VISION.id in availableModelIds) {
            ModelRoute.Selected(GroqModel.PREVIEW_VISION, maxOutputTokens = 1024)
        } else {
            unavailable(ModelRoute.Reason.MODEL_UNAVAILABLE)
        }
    }

    val candidates = when (task) {
        CoachingTask.QUICK_QUESTION -> listOf(GroqModel.QUICK_TEXT, GroqModel.DETAILED_TEXT)
        CoachingTask.DETAILED_PLAN -> listOf(GroqModel.DETAILED_TEXT, GroqModel.QUICK_TEXT)
        CoachingTask.PHOTO_REVIEW -> error("Photo reviews are handled before text routing.")
    }
    val model = candidates.firstOrNull { it.id in availableModelIds }
        ?: return unavailable(ModelRoute.Reason.MODEL_UNAVAILABLE)
    return ModelRoute.Selected(
        model,
        maxOutputTokens = if (task == CoachingTask.DETAILED_PLAN) 2048 else 1024,
    )
}
