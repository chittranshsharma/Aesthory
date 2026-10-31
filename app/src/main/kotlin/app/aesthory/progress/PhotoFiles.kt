package app.aesthory.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class StoredPhoto(val key: String, val sha256: String, val bytes: Long, val width: Int, val height: Int)

class PhotoFiles(private val context: Context) {
    private val root = File(context.filesDir, "progress-photos")
    fun original(key: String): File { require(validPhotoKey(key)); return File(root, "$key.jpg") }
    fun thumbnail(key: String): File { require(validPhotoKey(key)); return File(root, "$key.thumb.jpg") }

    fun save(uri: Uri, checkCancelled: () -> Unit): StoredPhoto {
        val bitmap = loadUri(uri, 2048, checkCancelled)
        return try { save(bitmap, checkCancelled) } finally { bitmap.recycle() }
    }
    fun preview(uri: Uri, checkCancelled: () -> Unit): Bitmap = loadUri(uri, 512, checkCancelled)
    private fun loadUri(uri: Uri, maxAxis: Int, checkCancelled: () -> Unit): Bitmap {
        root.mkdirs()
        val source = File.createTempFile("photo-source-", ".tmp", context.cacheDir)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> source.outputStream().use { output -> copyLimited(input, output, 50L * 1024 * 1024, checkCancelled) } }
            checkCancelled()
            return decode(source, maxAxis, rejectAnimated = true)
        } finally { source.delete() }
    }

    /** Does not take ownership of the analysis preview bitmap. */
    fun save(bitmap: Bitmap, checkCancelled: () -> Unit): StoredPhoto {
        root.mkdirs()
        val key = UUID.randomUUID().toString()
        val pending = File(root, "$key.partial")
        try {
            checkCancelled()
            pending.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)); it.fd.sync() }
            require(pending.length() in 1..8L * 1024 * 1024)
            checkCancelled()
            Files.move(pending.toPath(), original(key).toPath(), StandardCopyOption.ATOMIC_MOVE)
            makeThumbnail(key)
            return StoredPhoto(key, hash(original(key)), original(key).length(), bitmap.width, bitmap.height)
        } catch (failure: Throwable) {
            remove(key)
            throw failure
        } finally { pending.delete() }
    }

    fun install(source: File, key: String) {
        root.mkdirs()
        require(validPhotoKey(key) && !original(key).exists())
        Files.move(source.toPath(), original(key).toPath(), StandardCopyOption.ATOMIC_MOVE)
        try { makeThumbnail(key) } catch (failure: Throwable) { remove(key); throw failure }
    }
    fun read(key: String, small: Boolean): Bitmap = decode(if (small) thumbnail(key) else original(key), if (small) 320 else 1536)
    fun remove(key: String) { original(key).delete(); thumbnail(key).delete(); File(root, "$key.partial").delete() }
    fun cleanup(liveKeys: Set<String>) {
        context.cacheDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("photo-source-") && it.name.endsWith(".tmp") }.forEach { it.delete() }
        root.listFiles().orEmpty().forEach { file ->
            val key = file.name.substringBefore('.')
            if (validPhotoKey(key) && (key !in liveKeys || file.name.endsWith(".partial"))) file.delete()
        }
    }
    private fun makeThumbnail(key: String) {
        val bitmap = decode(original(key), 320)
        try { thumbnail(key).outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) } }
        finally { bitmap.recycle() }
    }
    companion object {
        fun decode(file: File, maxAxis: Int, rejectAnimated: Boolean = false): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 120_000_000)
            require(!rejectAnimated || !info.isAnimated) { "Choose a still photo." }
            val scale = minOf(1.0, maxAxis.toDouble() / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        fun hash(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun copyLimited(input: InputStream, output: OutputStream, limit: Long, checkCancelled: () -> Unit = {}): Long {
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "This file is too large." }
                output.write(buffer, 0, count)
            }
            return total
        }
    }
}
