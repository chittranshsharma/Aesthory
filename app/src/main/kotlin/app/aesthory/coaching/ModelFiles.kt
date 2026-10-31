package app.aesthory.coaching

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Streams to a sibling file, preserving the installed model if import fails. */
fun importLocalModel(
    source: InputStream,
    destination: File,
    maxBytes: Long = 5L * 1024 * 1024 * 1024,
    verify: (File) -> Unit = {},
    onProgress: (Long) -> Unit = {},
): String {
    require(maxBytes > 0)
    check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
    val temporary = File(destination.parentFile, destination.name + ".partial")
    val digest = MessageDigest.getInstance("SHA-256")
    try {
        var total = 0L
        temporary.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "The selected model exceeds the import size limit." }
                require(destination.parentFile!!.usableSpace > count + 64L * 1024 * 1024) { "Not enough free storage to import the model." }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                onProgress(total)
            }
        }
        require(total >= 512) { "The selected file is too small to be a compatible model." }
        val header = temporary.inputStream().use { input -> ByteArray(12).also { require(input.read(it) == it.size) } }
        require(header.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "LITERTLM" &&
            ByteBuffer.wrap(header, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int == 1) {
            "Choose a supported LiteRT-LM container, not a GGUF file or image."
        }
        verify(temporary)
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return digest.digest().joinToString("") { "%02x".format(it) }
    } finally {
        temporary.delete()
    }
}
