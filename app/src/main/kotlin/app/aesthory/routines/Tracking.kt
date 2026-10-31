package app.aesthory.routines

enum class TrackingKind { CHECKBOX, COUNT, DURATION, VALUE, RATING, CHECKLIST }
enum class CompletionStatus { DONE, PARTIAL, SKIPPED, UNDONE }
enum class HabitState { ACTIVE, PAUSED, ARCHIVED }

data class TrackingSettings(
    val kind: TrackingKind = TrackingKind.CHECKBOX,
    val target: Double? = null,
    val unit: String? = null,
    val items: List<String> = emptyList(),
) {
    init {
        require(target == null || target.isFinite()) { "Target must be finite." }
        when (kind) {
            TrackingKind.CHECKBOX -> require(target == null && unit == null && items.isEmpty())
            TrackingKind.COUNT, TrackingKind.DURATION ->
                require(target != null && target > 0 && target % 1.0 == 0.0 && items.isEmpty())
            TrackingKind.VALUE -> require(!unit.isNullOrBlank() && items.isEmpty())
            TrackingKind.RATING ->
                require(target != null && target in 2.0..100.0 && target % 1.0 == 0.0 && items.isEmpty())
            TrackingKind.CHECKLIST -> require(
                target == null && items.isNotEmpty() && items.all { it.isNotBlank() } &&
                    items.distinct().size == items.size,
            )
        }
    }
}

data class CompletionInput(
    val status: CompletionStatus,
    val value: Double? = null,
    val checkedItems: List<Int> = emptyList(),
) {
    fun validateFor(settings: TrackingSettings) {
        require(value == null || value.isFinite()) { "Recorded value must be finite." }
        require(checkedItems.distinct().size == checkedItems.size)
        if (status == CompletionStatus.SKIPPED || status == CompletionStatus.UNDONE) {
            require(value == null && checkedItems.isEmpty())
            return
        }
        when (settings.kind) {
            TrackingKind.CHECKBOX -> require(value == null && checkedItems.isEmpty())
            TrackingKind.COUNT, TrackingKind.DURATION ->
                require(value != null && value >= 0 && value % 1.0 == 0.0 && checkedItems.isEmpty())
            TrackingKind.VALUE -> require(value != null && checkedItems.isEmpty())
            TrackingKind.RATING ->
                require(value != null && value >= 1 && value <= settings.target!! &&
                    value % 1.0 == 0.0 && checkedItems.isEmpty())
            TrackingKind.CHECKLIST -> require(
                value == null && checkedItems.all { it in settings.items.indices } &&
                    (status != CompletionStatus.DONE || checkedItems.size == settings.items.size),
            )
        }
    }
}
