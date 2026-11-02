package app.aesthory.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.aesthory.data.HabitVersionEntity
import java.net.URI
import java.time.LocalDate

enum class StockState(val label: String) { READY("Have it"), LOW("Running low"), FINISHED("Finished") }

fun safeWebUrl(value: String): Boolean = value.isEmpty() || runCatching {
    val uri = URI(value)
    value.length <= 2048 && uri.scheme?.lowercase() in setOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null
}.getOrDefault(false)

private fun checkDay(day: Long?) { day?.let { LocalDate.ofEpochDay(it) } }

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String = "",
    val kind: String = "",
    val size: String = "",
    val labelNotes: String = "",
    val notes: String = "",
    val url: String = "",
    val openedDay: Long? = null,
    val expiryDay: Long? = null,
    val periodMonths: Int? = null,
    val stock: StockState = StockState.READY,
    val archived: Boolean = false,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 120)
        require(brand.length <= 120 && kind.length <= 80 && size.length <= 120)
        require(labelNotes.length <= 8000 && notes.length <= 8000 && safeWebUrl(url))
        require(periodMonths == null || periodMonths in 1..120)
        checkDay(openedDay); checkDay(expiryDay)
        require(openedDay == null || expiryDay == null || expiryDay >= openedDay)
        if (openedDay != null && periodMonths != null) LocalDate.ofEpochDay(openedDay).plusMonths(periodMonths.toLong())
    }
}

@Entity(tableName = "personal_references")
data class ReferenceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String = "",
    val url: String = "",
    val sourceDay: Long? = null,
    val archived: Boolean = false,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && title.length <= 120)
        require(content.length <= 32000 && safeWebUrl(url))
        require(content.isNotBlank() || url.isNotBlank())
        checkDay(sourceDay)
    }
}

@Entity(tableName = "product_observations",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"])],
    indices = [Index("productId")])
data class ProductObservation(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val day: Long,
    val createdAtMillis: Long,
    val note: String,
) {
    init { require(id.isNotBlank() && productId.isNotBlank() && productName.isNotBlank() && productName.length <= 120 && createdAtMillis >= 0 && note.isNotBlank() && note.length <= 2000); checkDay(day) }
}

@Entity(tableName = "habit_product_links", primaryKeys = ["habitId", "version", "productId"],
    foreignKeys = [
        ForeignKey(entity = HabitVersionEntity::class, parentColumns = ["habitId", "version"], childColumns = ["habitId", "version"]),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"]),
    ], indices = [Index("productId")])
data class HabitProductLink(
    val habitId: String, val version: Int, val productId: String,
    val name: String, val brand: String, val labelNotes: String, val usageNote: String,
) {
    init { require(habitId.isNotBlank() && version > 0 && productId.isNotBlank() && name.isNotBlank() && name.length <= 120 && brand.length <= 120 && labelNotes.length <= 8000 && usageNote.length <= 2000) }
}

@Entity(tableName = "habit_reference_links", primaryKeys = ["habitId", "version", "referenceId"],
    foreignKeys = [
        ForeignKey(entity = HabitVersionEntity::class, parentColumns = ["habitId", "version"], childColumns = ["habitId", "version"]),
        ForeignKey(entity = ReferenceEntity::class, parentColumns = ["id"], childColumns = ["referenceId"]),
    ], indices = [Index("referenceId")])
data class HabitReferenceLink(
    val habitId: String, val version: Int, val referenceId: String,
    val title: String, val content: String, val url: String, val sourceDay: Long?,
) {
    init { require(habitId.isNotBlank() && version > 0 && referenceId.isNotBlank() && title.isNotBlank() && title.length <= 120 && content.length <= 32000 && safeWebUrl(url) && (content.isNotBlank() || url.isNotBlank())); checkDay(sourceDay) }
}

data class ProductChoice(val id: String, val usageNote: String = "")
data class HabitLinks(val products: List<HabitProductLink>, val references: List<HabitReferenceLink>)
