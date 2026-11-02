package app.aesthory.ui

import app.aesthory.data.HabitDraft
import app.aesthory.data.HabitVersionEntity
import app.aesthory.library.HabitLinks
import app.aesthory.library.ProductChoice
import app.aesthory.routines.*
import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

data class HabitEditorInput(
    val title: String = "", val categoryId: String = "", val instructions: String = "",
    val purpose: String = "", val tags: String = "", val shortInstructions: String = "", val minutes: String = "",
    val start: String = LocalDate.now().toString(), val end: String = "", val frequency: String = "daily",
    val interval: String = "2", val days: List<Int> = emptyList(),
    val kind: TrackingKind = TrackingKind.CHECKBOX, val target: String = "", val unit: String = "", val checklist: String = "",
    val productIds: List<String> = emptyList(), val usageNotes: Map<String, String> = emptyMap(), val referenceIds: List<String> = emptyList(),
) : Serializable {
    fun errors(): Map<String, String> = buildMap {
        if (title.isBlank() || title.trim().length > 120) put("title", "Give your action a name, up to 120 characters.")
        if (categoryId.isBlank()) put("category", "Choose a category.")
        if (minutes.isNotBlank() && minutes.toIntOrNull()?.let { it in 1..1440 } != true) put("minutes", "Enter whole minutes from 1 to 1,440, or leave it empty.")
        val from = runCatching { LocalDate.parse(start) }.getOrNull()
        val until = end.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (from == null || (end.isNotBlank() && (until == null || until < from))) put("schedule", "The end date must be on or after the start date.")
        if (frequency !in setOf("daily", "weekdays", "interval") || days.any { it !in 1..7 }) put("schedule", "Choose one of the available schedules.")
        if (frequency == "weekdays" && days.isEmpty()) put("schedule", "Pick at least one day.")
        if (frequency == "interval" && interval.toIntOrNull()?.let { it in 1..3650 } != true) put("schedule", "Enter a whole number of days from 1 to 3,650.")
        if (runCatching { tracking() }.isFailure) put("tracking", when (kind) {
            TrackingKind.CHECKLIST -> "Add 1–50 different steps, one per line (up to 240 characters each)."
            TrackingKind.COUNT -> "Enter a positive whole-number goal."
            TrackingKind.DURATION -> "Enter a positive goal in minutes, down to whole seconds."
            TrackingKind.VALUE -> "Add a unit, such as cm or kg, and a number for the goal if you want one."
            TrackingKind.RATING -> "Choose a whole-number scale from 2 to 100."
            else -> "Check the tracking details."
        })
        if (purpose.length > 1000 || instructions.length > 8000 || shortInstructions.length > 2000) put("instructions", "Shorten these notes: instructions allow 8,000 characters, your reason 1,000 and the busy-day version 2,000.")
        val labels = tagList()
        if (labels.size > 10 || labels.any { it.length > 40 } || labels.distinct().size != labels.size) put("tags", "Use up to 10 different tags, up to 40 characters each.")
    }
    private fun tagList() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun tracking(): TrackingSettings {
        val goal = if (target.isBlank()) null else requireNotNull(target.toDoubleOrNull()).also { require(it.isFinite()) }
        val items = checklist.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(unit.length <= 40 && items.size <= 50 && items.all { it.length <= 240 })
        return when (kind) {
            TrackingKind.CHECKBOX -> TrackingSettings()
            TrackingKind.COUNT -> TrackingSettings(kind, goal)
            TrackingKind.DURATION -> {
                // Reopening a whole-second goal as decimal minutes can introduce floating-point noise.
                val seconds = goal?.times(60.0)?.let { value ->
                    val rounded = kotlin.math.round(value)
                    if (kotlin.math.abs(value - rounded) < 0.0000001) rounded else value
                }
                TrackingSettings(kind, seconds)
            }
            TrackingKind.VALUE -> TrackingSettings(kind, goal, unit.trim().takeIf { it.isNotEmpty() })
            TrackingKind.RATING -> TrackingSettings(kind, goal)
            TrackingKind.CHECKLIST -> TrackingSettings(kind, items = items)
        }
    }
    fun toDraft(): HabitDraft {
        require(errors().isEmpty())
        val recurrence = when (frequency) {
            "daily" -> Recurrence.Daily
            "weekdays" -> Recurrence.Weekdays(days.map(DayOfWeek::of).toSet())
            "interval" -> Recurrence.EveryNDays(interval.toInt())
            else -> error("Unknown schedule")
        }
        return HabitDraft(title.trim(), categoryId, HabitSchedule(LocalDate.parse(start), recurrence, end.takeIf { it.isNotBlank() }?.let(LocalDate::parse)),
            instructions.trim(), minutes.toIntOrNull(), tracking(), purpose.trim(), tagList(), shortInstructions.trim(),
            productIds.map { ProductChoice(it, usageNotes[it].orEmpty()) }, referenceIds)
    }
    companion object {
        fun from(old: HabitVersionEntity?, links: HabitLinks): HabitEditorInput {
            if (old == null) return HabitEditorInput()
            val rule = old.schedule.toDomain().recurrence
            val settings = old.tracking
            return HabitEditorInput(old.title, old.categoryId, old.instructions, old.purpose, old.tags.joinToString(", "), old.shortInstructions, old.estimatedMinutes?.toString().orEmpty(),
                LocalDate.ofEpochDay(old.schedule.startDay).toString(), old.schedule.endDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(), old.schedule.recurrence,
                (rule as? Recurrence.EveryNDays)?.interval?.toString() ?: "2", (rule as? Recurrence.Weekdays)?.days?.map { it.value } ?: emptyList(),
                settings.kind, settings.target?.let { if (settings.kind == TrackingKind.DURATION) (it / 60.0).toString() else it.toString() }.orEmpty(), settings.unit.orEmpty(), settings.items.joinToString("\n"),
                links.products.map { it.productId }, links.products.associate { it.productId to it.usageNote }, links.references.map { it.referenceId })
        }
    }
}
