package app.aesthory.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class NutritionScan(val bitmap: Bitmap, val hash: String, val text: String, val values: NutritionValues)

class NutritionOcr(private val context: Context) {
    suspend fun scan(uri: Uri): NutritionScan = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri)
        require(type == null || type.startsWith("image/"))
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                require(output.size() + count <= 20 * 1024 * 1024)
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray() }
        require(bytes.size in 1..20 * 1024 * 1024)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 40_000_000)
            require(info.mimeType in setOf("image/png", "image/jpeg", "image/webp"))
            val scale = minOf(1.0, 2400.0 / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val task = try { recognizer.process(InputImage.fromBitmap(bitmap, 0)) } catch (failure: Exception) { recognizer.close(); throw failure }
        task.addOnCompleteListener { recognizer.close() }
        val result = Tasks.await(task, 30, TimeUnit.SECONDS)
        val text = result.textBlocks.flatMap { it.lines }.joinToString("\n") { it.text }.take(32000)
        NutritionScan(bitmap, hash, text, NutritionParser.parse(text))
    }
}
