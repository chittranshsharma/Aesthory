package app.aesthory.workouts

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class WorkoutFiles(private val context: Context) {
    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri)
        require(type == null || type.startsWith("text/") || type in setOf("application/octet-stream", "application/csv", "application/vnd.ms-excel"))
        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > HevyCsv.MAX_BYTES) throw WorkoutImportError("Choose a Hevy workout CSV under 5 MB.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}
