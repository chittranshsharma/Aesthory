package app.aesthory.backup

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import app.aesthory.data.AesthoryDatabase
import app.aesthory.progress.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.aesthory.timing.AndroidTimingClock

data class PreparedBackup(val token: String, val summary: BackupSummary, val bytes: Long)
data class RestorePreview(val token: String, val summary: BackupSummary)
class VaultUserError(message: String) : Exception(message)

/** Coordinates photo files with transactional records; restore never overwrites live image files. */
class PersonalVault(private val context: Context, private val db: AesthoryDatabase) {
    private val dao = db.records()
    private val media = PhotoFiles(context)
    private val work = File(context.cacheDir, "backup-work")
    private val mutex = Mutex()
    private var initialized = false
    private var prepared: PreparedBackup? = null
    private var restore: Pair<RestorePreview, org.json.JSONObject>? = null
    private val lastSaved = MutableStateFlow<Long?>(null)
    val lastBackupMillis = lastSaved.asStateFlow()
    val photos = dao.observePhotos()

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!initialized) {
                check(work.isDirectory || work.mkdirs())
                work.listFiles().orEmpty().filter { validPhotoKey(it.name) }.forEach { it.deleteRecursively() }
                media.cleanup(dao.allPhotos().map { it.fileKey }.toSet())
                lastSaved.value = context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).getLong("lastSaved", 0).takeIf { it > 0 }
                initialized = true
            }
            block()
        }
    }
    suspend fun initialize() = locked { Unit }
    private suspend fun <T> withPassword(password: CharArray, block: suspend () -> T): T =
        try { locked(block) } finally { password.fill('\u0000') }
    suspend fun readPhoto(key: String, small: Boolean): Bitmap = withContext(Dispatchers.IO) { media.read(key, small) }
    suspend fun previewPhoto(uri: Uri): Bitmap = locked {
        val coroutine = currentCoroutineContext()
        media.preview(uri) { coroutine.ensureActive() }
    }

    suspend fun addPhoto(uri: Uri?, bitmap: Bitmap?, day: LocalDate, area: PhotoArea, view: PhotoView, note: String) = locked {
        require(note.length <= 500 && (uri != null || bitmap != null))
        requireNotNull(dao.getProfile())
        if (dao.allPhotos().size >= 500) throw VaultUserError("Your journal has reached 500 photos. Back up what you want to keep, then delete unwanted photos from Recently deleted to make room.")
        val coroutine = currentCoroutineContext()
        val stored = if (uri != null) media.save(uri) { coroutine.ensureActive() } else media.save(requireNotNull(bitmap)) { coroutine.ensureActive() }
        var committed = false
        try {
            coroutine.ensureActive()
            withContext(NonCancellable) {
                dao.insertPhoto(ProgressPhoto(UUID.randomUUID().toString(), day = day.toEpochDay(), createdAtMillis = System.currentTimeMillis(),
                    area = area, view = view, note = note.trim(), fileKey = stored.key, sha256 = stored.sha256, byteCount = stored.bytes, width = stored.width, height = stored.height))
                committed = true
            }
        } finally { if (!committed) media.remove(stored.key) }
    }
    suspend fun editPhoto(id: String, day: LocalDate, area: PhotoArea, view: PhotoView, note: String) = locked {
        val old = requireNotNull(dao.allPhotos().find { it.id == id })
        val updated = old.copy(day = day.toEpochDay(), area = area, view = view, note = note.trim())
        dao.updatePhoto(updated)
    }
    suspend fun binPhoto(id: String, undo: Boolean = false) = locked {
        require(dao.allPhotos().any { it.id == id })
        dao.setPhotoDeleted(id, if (undo) null else System.currentTimeMillis())
    }
    suspend fun deletePhoto(id: String) = locked {
        val photo = requireNotNull(dao.allPhotos().find { it.id == id && it.deletedAtMillis != null })
        withContext(NonCancellable) { dao.deletePhoto(id); media.remove(photo.fileKey) }
    }

    suspend fun prepareBackup(password: CharArray): PreparedBackup = withPassword(password) {
        discardPrepared()
        val token = UUID.randomUUID().toString()
        val folder = File(work, token).also { check(it.mkdirs()) }
        try {
            val moment = AndroidTimingClock(context).now()
            val snapshot = RecordSnapshot.capture(db, moment.wall, moment)
            RecordSnapshot.validate(context, snapshot)
            val records = RecordSnapshot.encode(snapshot)
            val photos = daoPhotos(snapshot)
            val estimated = records.size.toLong() + photos.sumOf { it.byteCount }
            if (estimated > MAX_BACKUP - 1024 * 1024) throw VaultUserError("This backup is larger than the 512 MB limit in this version. It hasn't been saved.")
            if (folder.usableSpace <= estimated + 32L * 1024 * 1024) throw VaultUserError("Your phone needs more free space to prepare a backup. Your records haven't changed.")
            val coroutine = currentCoroutineContext()
            File(folder, "backup.aesthory").outputStream().buffered().use { output ->
                BackupCipher.encrypt(output, password) { encrypted ->
                    ZipOutputStream(encrypted).use { zip ->
                        zip.setLevel(0)
                        zip.putNextEntry(ZipEntry("records.json")); zip.write(records); zip.closeEntry()
                        photos.forEach { photo ->
                            coroutine.ensureActive()
                            val original = media.original(photo.fileKey)
                            require(original.isFile && original.length() == photo.byteCount && PhotoFiles.hash(original) == photo.sha256) { "A saved photo is missing or damaged." }
                            zip.putNextEntry(ZipEntry("photos/${photo.fileKey}.jpg"))
                            original.inputStream().use { PhotoFiles.copyLimited(it, zip, 8L * 1024 * 1024) { coroutine.ensureActive() } }
                            zip.closeEntry()
                        }
                    }
                }
            }
            val result = PreparedBackup(token, RecordSnapshot.summary(snapshot), File(folder, "backup.aesthory").length())
            prepared = result
            result
        } catch (failure: Throwable) { folder.deleteRecursively(); throw failure }
        finally { password.fill('\u0000') }
    }
    suspend fun saveBackup(uri: Uri, token: String) = locked {
        require(prepared?.token == token)
        val coroutine = currentCoroutineContext()
        File(work, "$token/backup.aesthory").inputStream().use { input ->
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                PhotoFiles.copyLimited(input, output, MAX_BACKUP) { coroutine.ensureActive() }
            }
        }
        val at = System.currentTimeMillis()
        runCatching { context.getSharedPreferences("backup_status", Context.MODE_PRIVATE).edit().putLong("lastSaved", at).commit() }
        lastSaved.value = at
        discardPrepared()
    }
    suspend fun cancelBackup() = locked { discardPrepared() }
    private fun discardPrepared() { prepared?.let { File(work, it.token).deleteRecursively() }; prepared = null }

    suspend fun previewRestore(uri: Uri, password: CharArray): RestorePreview = withPassword(password) {
        discardRestore()
        val token = UUID.randomUUID().toString()
        val folder = File(work, token).also { check(it.mkdirs()) }
        try {
            val coroutine = currentCoroutineContext()
            val ciphertext = File(folder, "input.aesthory")
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> ciphertext.outputStream().use { PhotoFiles.copyLimited(input, it, MAX_BACKUP) { coroutine.ensureActive() } } }
            require(folder.usableSpace > ciphertext.length() * 2 + 32L * 1024 * 1024)
            val archive = File(folder, "payload.zip")
            ciphertext.inputStream().buffered().use { input ->
                BackupCipher.decrypt(input, password) { clear -> archive.outputStream().use { PhotoFiles.copyLimited(clear, it, MAX_BACKUP) { coroutine.ensureActive() } } }
            }
            // Authentication reached EOF before any archive parsing or live-record mutation.
            ciphertext.delete()
            val seen = hashSetOf<String>()
            var expanded = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    coroutine.ensureActive()
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && seen.add(entry.name) && seen.size <= 501)
                    val output: File
                    val limit: Long
                    if (entry.name == "records.json") { output = File(folder, "records.json"); limit = RecordSnapshot.MAX_BYTES.toLong() }
                    else {
                        require(entry.name.startsWith("photos/") && entry.name.endsWith(".jpg"))
                        val key = entry.name.removePrefix("photos/").removeSuffix(".jpg")
                        require(validPhotoKey(key))
                        output = File(folder, "$key.jpg"); limit = 8L * 1024 * 1024
                    }
                    expanded += output.outputStream().use { PhotoFiles.copyLimited(zip, it, limit) { coroutine.ensureActive() } }
                    require(expanded <= MAX_BACKUP)
                    zip.closeEntry()
                }
            }
            archive.delete()
            val snapshot = RecordSnapshot.decode(File(folder, "records.json").readBytes())
            RecordSnapshot.validate(context, snapshot)
            val photos = daoPhotos(snapshot)
            require(seen == setOf("records.json") + photos.map { "photos/${it.fileKey}.jpg" })
            photos.forEach { photo ->
                coroutine.ensureActive()
                val file = File(folder, "${photo.fileKey}.jpg")
                require(file.length() == photo.byteCount && PhotoFiles.hash(file) == photo.sha256)
                val bitmap = PhotoFiles.decode(file, 2048, rejectAnimated = true)
                try { require(bitmap.width == photo.width && bitmap.height == photo.height) } finally { bitmap.recycle() }
            }
            val preview = RestorePreview(token, RecordSnapshot.summary(snapshot))
            restore = preview to snapshot
            preview
        } catch (failure: Throwable) { folder.deleteRecursively(); throw failure }
        finally { password.fill('\u0000') }
    }
    suspend fun cancelRestore() = locked { discardRestore() }
    private fun discardRestore() { restore?.let { File(work, it.first.token).deleteRecursively() }; restore = null }

    suspend fun restoreBackup(token: String) = locked {
        val pending = requireNotNull(restore).also { require(it.first.token == token) }
        val snapshot = RecordSnapshot.decode(RecordSnapshot.encode(pending.second))
        val rows = snapshot.getJSONObject("tables").getJSONArray("progress_photos")
        val installed = mutableSetOf<String>()
        var committed = false
        try {
            repeat(rows.length()) { index ->
                currentCoroutineContext().ensureActive()
                val row = rows.getJSONObject(index)
                val newKey = UUID.randomUUID().toString()
                media.install(File(work, "$token/${row.getString("fileKey")}.jpg"), newKey)
                installed += newKey
                row.put("fileKey", newKey)
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { RecordSnapshot.replace(db, snapshot); committed = true }
        } finally {
            if (!committed) { installed.forEach(media::remove); discardRestore() }
            else withContext(NonCancellable) { runCatching { media.cleanup(dao.allPhotos().map { it.fileKey }.toSet()) }; discardRestore() }
        }
    }
    private fun daoPhotos(snapshot: org.json.JSONObject): List<PhotoReference> {
        val rows = snapshot.getJSONObject("tables").getJSONArray("progress_photos")
        return List(rows.length()) { index -> rows.getJSONObject(index).let {
            PhotoReference(it.getString("fileKey"), it.getString("sha256"), it.getLong("byteCount"), it.getInt("width"), it.getInt("height"))
        } }
    }
    private data class PhotoReference(val fileKey: String, val sha256: String, val byteCount: Long, val width: Int, val height: Int)
    companion object { const val MAX_BACKUP = 512L * 1024 * 1024 }
}
