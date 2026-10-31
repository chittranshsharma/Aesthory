package app.aesthory.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class AnalyzedPhoto(val bitmap: Bitmap, val review: FaceReview)

class LocalFaceAnalyzer(private val context: Context) {
    suspend fun analyze(uri: Uri, goal: PresentationGoal): AnalyzedPhoto = withContext(Dispatchers.Default) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 120_000_000) { "Choose a photo under 120 megapixels." }
            val scale = minOf(1.0, 1536.0 / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
        try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("models/face_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(2)
                .setOutputFaceBlendshapes(true)
                .build()
            val review = FaceLandmarker.createFromOptions(context, options).use { model ->
                // MPImage recycles its bitmap on close; the UI preview must keep separate ownership.
                val inferenceBitmap = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
                BitmapImageBuilder(inferenceBitmap).build().use { image ->
                    val result = model.detect(image)
                    val landmarks = result.faceLandmarks().firstOrNull().orEmpty()
                    val expressions = result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty()
                    val expression = expressions.filter { it.categoryName() in setOf("jawOpen", "mouthSmileLeft", "mouthSmileRight") }
                        .maxOfOrNull { it.score().toDouble() } ?: 0.0
                    reviewFaceGeometry(
                        landmarks.map { FacePoint(it.x().toDouble() * bitmap.width, it.y().toDouble() * bitmap.height) },
                        bitmap.width, bitmap.height, result.faceLandmarks().size, expression, goal,
                    )
                }
            }
            AnalyzedPhoto(bitmap, review)
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }
}
