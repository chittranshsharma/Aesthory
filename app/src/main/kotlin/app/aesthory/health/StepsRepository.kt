package app.aesthory.health

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class StepsAccess { AVAILABLE, PERMISSION, UPDATE, UNAVAILABLE }
data class StepTotal(val count: Long?, val sources: Set<String> = emptySet()) {
    init { require(count == null || count >= 0) }
}
data class StepDay(val date: LocalDate, val total: StepTotal)
data class StepsState(val access: StepsAccess = StepsAccess.PERMISSION, val loading: Boolean = false,
    val days: List<StepDay> = emptyList(), val checked: Instant? = null, val zone: String = "", val error: Boolean = false)
interface StepsSource {
    suspend fun access(): StepsAccess
    suspend fun total(start: Instant, end: Instant): StepTotal
}

class StepsRepository(private val source: StepsSource, private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private val mutable = MutableStateFlow(StepsState())
    val state = mutable.asStateFlow()
    private val refreshLock = Mutex()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            mutable.value = StepsState(loading = true)
            try {
                val access = source.access()
                if (access != StepsAccess.AVAILABLE) {
                    mutable.value = StepsState(access = access)
                    return@withLock
                }
                val now = clock.instant()
                val timezone = zone()
                val today = now.atZone(timezone).toLocalDate()
                val days = (0L..6L).map { offset ->
                    val date = today.minusDays(offset)
                    val start = date.atStartOfDay(timezone).toInstant()
                    val end = if (offset == 0L) now else date.plusDays(1).atStartOfDay(timezone).toInstant()
                    StepDay(date, if (start == end) StepTotal(null) else source.total(start, end))
                }
                // A refresh replaces the complete view, including deleted or reduced source totals.
                val finalAccess = source.access()
                mutable.value = if (finalAccess == StepsAccess.AVAILABLE) StepsState(finalAccess, days = days, checked = now, zone = timezone.id)
                    else StepsState(access = finalAccess)
            } catch (cancelled: CancellationException) {
                mutable.value = StepsState()
                throw cancelled
            } catch (_: SecurityException) {
                mutable.value = StepsState(access = StepsAccess.PERMISSION)
            } catch (_: Exception) {
                mutable.value = StepsState(error = true)
            }
        }
    }
    fun clearView() { mutable.value = StepsState() }
}
