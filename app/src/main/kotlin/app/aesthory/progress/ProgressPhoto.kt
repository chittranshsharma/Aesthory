package app.aesthory.progress

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aesthory.data.ProfileEntity
import java.time.LocalDate
import java.util.UUID

enum class PhotoArea(val label: String) { FACE("Face & style"), SKIN("Skin"), HAIR("Hair"), BODY("Body & fitness") }
enum class PhotoView(val label: String) { FRONT("Front"), LEFT("Left side"), RIGHT("Right side"), OTHER("Other") }
fun validPhotoKey(key: String): Boolean = runCatching { UUID.fromString(key).toString() == key }.getOrDefault(false)

@Entity(tableName = "progress_photos", foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"])], indices = [Index("profileId"), Index(value = ["fileKey"], unique = true)])
data class ProgressPhoto(
    @PrimaryKey val id: String,
    val profileId: Long = 1,
    val day: Long,
    val createdAtMillis: Long,
    val area: PhotoArea,
    val view: PhotoView,
    val note: String,
    val fileKey: String,
    val sha256: String,
    val byteCount: Long,
    val width: Int,
    val height: Int,
    val deletedAtMillis: Long? = null,
) {
    init {
        require(validPhotoKey(id) && validPhotoKey(fileKey) && profileId == 1L)
        LocalDate.ofEpochDay(day)
        require(note.length <= 500 && createdAtMillis >= 0 && (deletedAtMillis == null || deletedAtMillis >= 0))
        require(sha256.matches(Regex("[a-f0-9]{64}")) && byteCount in 1..8L * 1024 * 1024)
        require(width in 1..2048 && height in 1..2048)
    }
}

fun comparisonPair(photos: List<ProgressPhoto>): List<ProgressPhoto> {
    require(photos.size == 2 && photos.map { it.id }.distinct().size == 2)
    require(photos.all { it.deletedAtMillis == null } && photos.map { it.area to it.view }.distinct().size == 1) {
        "Choose two photos from the same area and view."
    }
    return photos.sortedWith(compareBy<ProgressPhoto> { it.day }.thenBy { it.createdAtMillis }.thenBy { it.id })
}
