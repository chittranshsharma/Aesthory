package app.aesthory.library

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ReferenceFiles(private val context: Context) {
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 128 * 1024)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        decode(bytes)
    }
    companion object {
        fun decode(bytes: ByteArray): String {
            require(bytes.size in 1..128 * 1024)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            require(text.isNotBlank() && text.length <= 32000 && text.none { it.code < 32 && it !in "\n\r\t" })
            return text
        }
    }
}
