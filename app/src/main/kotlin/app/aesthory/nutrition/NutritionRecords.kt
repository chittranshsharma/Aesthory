package app.aesthory.nutrition

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "nutrition_days")
data class NutritionDay(
    @PrimaryKey val day: Long,
    val calories: Double?, val protein: Double?, val carbs: Double?, val fat: Double?,
    val sourceHash: String?, val revision: String = UUID.randomUUID().toString(), val savedAtMillis: Long,
) {
    init {
        LocalDate.ofEpochDay(day)
        require(listOf(calories, protein, carbs, fat).any { it != null })
        require(calories == null || calories.isFinite() && calories in 0.0..20000.0)
        require(listOf(protein, carbs, fat).all { it == null || it.isFinite() && it in 0.0..5000.0 })
        require(sourceHash == null || sourceHash.matches(Regex("[a-f0-9]{64}")))
        require(UUID.fromString(revision).toString() == revision && savedAtMillis >= 0)
    }
}

// Receipts outlive a replacement so an older screenshot cannot silently overwrite today's totals.
@Entity(tableName = "nutrition_imports")
data class NutritionImport(@PrimaryKey val hash: String, val day: Long) {
    init { require(hash.matches(Regex("[a-f0-9]{64}"))); LocalDate.ofEpochDay(day) }
}

sealed interface NutritionSave {
    data object Saved : NutritionSave
    data class Replace(val existing: NutritionDay) : NutritionSave
    data class Duplicate(val day: Long) : NutritionSave
}

data class NutritionValues(val calories: Double? = null, val protein: Double? = null, val carbs: Double? = null, val fat: Double? = null)

object NutritionParser {
    private val ambiguous = Regex("remaining|goals?|targets?|budget|burned|exercise|per[ -](serving|100)|%|/", RegexOption.IGNORE_CASE)
    private val explicit = Regex("consumed|eaten|intake", RegexOption.IGNORE_CASE)
    private val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)"
    fun parse(text: String): NutritionValues {
        require(text.length <= 32000)
        val contextAmbiguous = ambiguous.containsMatchIn(text)
        fun value(label: String, unit: String, maximum: Double): Double? {
            val pattern = Regex("^\\s*(?:consumed|eaten|intake)?\\s*(?:$label)\\s*(?:consumed|eaten|intake)?\\s*[:=]?\\s*($number)\\s*(?:$unit)?\\s*$", RegexOption.IGNORE_CASE)
            val candidates = text.lineSequence().filter { !ambiguous.containsMatchIn(it) && (!contextAmbiguous || explicit.containsMatchIn(it)) }
                .mapNotNull { line -> pattern.matchEntire(line)?.groupValues?.get(1) }
                .mapNotNull { raw ->
                    val normalized = if (Regex("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?").matches(raw)) raw.replace(",", "") else raw.replace(',', '.')
                    normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..maximum }
                }.toList()
            return candidates.singleOrNull()
        }
        return NutritionValues(value("calories|energy|kcal", "kcal|cal", 20000.0), value("protein", "g|grams", 5000.0),
            value("carbs|carbohydrates", "g|grams", 5000.0), value("fat|fats|total fat", "g|grams", 5000.0))
    }
    fun entered(text: String, maximum: Double): Double? {
        if (text.isBlank()) return null
        require(Regex("[0-9]+([.,][0-9]{1,2})?").matches(text.trim()))
        return requireNotNull(text.trim().replace(',', '.').toDoubleOrNull()).also { require(it.isFinite() && it in 0.0..maximum) }
    }
}
