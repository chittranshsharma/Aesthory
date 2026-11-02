package app.aesthory.routines

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class RoutineFiles(private val context: Context) {
    suspend fun read(uri: Uri): RoutinePack = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= RoutinePacks.MAX_BYTES) { "Choose a routine file under 256 KB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        RoutinePacks.decode(bytes)
    }
    suspend fun write(uri: Uri, pack: RoutinePack, readable: Boolean = false) = withContext(Dispatchers.IO) {
        val bytes = if (readable) RoutinePacks.readableText(pack).toByteArray(Charsets.UTF_8) else RoutinePacks.encode(pack)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }
}
