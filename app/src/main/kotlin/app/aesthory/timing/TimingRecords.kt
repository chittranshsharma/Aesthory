package app.aesthory.timing

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import app.aesthory.data.CurrentHabit
import app.aesthory.data.HabitEntity
import app.aesthory.data.HabitVersionEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

const val MAX_TIMER_MILLIS = 7L * 24 * 60 * 60 * 1000
enum class TimerRecovery { NONE, PHONE_RESTART, BACKUP }
data class TimingMoment(val wall: Long, val elapsed: Long, val boot: Int, val zone: ZoneId)
fun interface TimingClock { fun now(): TimingMoment }
class AndroidTimingClock(context: Context) : TimingClock {
    private val boot = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
    override fun now() = TimingMoment(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot, ZoneId.systemDefault())
}

@Entity(tableName = "active_timer",
    foreignKeys = [ForeignKey(entity = HabitVersionEntity::class, parentColumns = ["habitId", "version"], childColumns = ["habitId", "definitionVersion"])],
    indices = [Index(value = ["habitId", "definitionVersion"])])
data class ActiveTimer(
    @PrimaryKey val id: Long = 1,
    val sessionId: String,
    val habitId: String,
    val definitionVersion: Int,
    val day: Long,
    val accumulatedMillis: Long = 0,
    val startedElapsedMillis: Long? = null,
    val startedWallMillis: Long? = null,
    val boot: Int = -1,
    val goalMillis: Long? = null,
    val goalAlerted: Boolean = false,
    val recovery: TimerRecovery = TimerRecovery.NONE,
) {
    val running: Boolean get() = startedElapsedMillis != null
    init {
        require(id == 1L && UUID.fromString(sessionId).toString() == sessionId && habitId.isNotBlank() && definitionVersion > 0)
        LocalDate.ofEpochDay(day)
        require(accumulatedMillis in 0..MAX_TIMER_MILLIS && (goalMillis == null || goalMillis in 1..MAX_TIMER_MILLIS))
        require((startedElapsedMillis == null) == (startedWallMillis == null))
        require(startedElapsedMillis == null || startedElapsedMillis >= 0)
        require(startedWallMillis == null || startedWallMillis >= 0)
    }
}

object TimerMath {
    fun sameBoot(timer: ActiveTimer, now: TimingMoment): Boolean {
        if (now.elapsed < (timer.startedElapsedMillis ?: 0)) return false
        if (timer.boot >= 0 && now.boot >= 0) return timer.boot == now.boot
        val anchoredBootWall = (timer.startedWallMillis ?: now.wall) - (timer.startedElapsedMillis ?: now.elapsed)
        return kotlin.math.abs((now.wall - now.elapsed) - anchoredBootWall) < 120_000
    }
    fun elapsed(timer: ActiveTimer, now: TimingMoment): Long {
        if (!timer.running) return timer.accumulatedMillis
        val delta = if (sameBoot(timer, now)) now.elapsed - timer.startedElapsedMillis!!
            else (now.wall - timer.startedWallMillis!!).coerceAtLeast(0)
        return timer.accumulatedMillis + delta.coerceIn(0, MAX_TIMER_MILLIS - timer.accumulatedMillis)
    }
    fun paused(timer: ActiveTimer, now: TimingMoment, recovery: TimerRecovery = timer.recovery) =
        timer.copy(accumulatedMillis = elapsed(timer, now), startedElapsedMillis = null, startedWallMillis = null, recovery = recovery)
    fun portable(timer: ActiveTimer, now: TimingMoment) = paused(timer, now, TimerRecovery.BACKUP)
}

@Entity(tableName = "habit_reminders",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"])])
data class HabitReminder(@PrimaryKey val habitId: String, val minuteOfDay: Int, val enabled: Boolean = true) {
    init { require(habitId.isNotBlank() && minuteOfDay in 0..1439) }
}
@Entity(tableName = "reminder_preferences")
data class ReminderPreferences(@PrimaryKey val id: Long = 1, val quietEnabled: Boolean = true,
    val quietStart: Int = 22 * 60, val quietEnd: Int = 8 * 60, val paused: Boolean = false) {
    init { require(id == 1L && quietStart in 0..1439 && quietEnd in 0..1439 && (!quietEnabled || quietStart != quietEnd)) }
}
data class ReminderAction(@Embedded val habit: CurrentHabit, val routineArchived: Boolean)

/** Device-only scheduling/deduplication cache. Never portable across restore. */
@Entity(tableName = "reminder_runtime",
    foreignKeys = [ForeignKey(entity = HabitReminder::class, parentColumns = ["habitId"], childColumns = ["habitId"])])
data class ReminderRuntime(
    @PrimaryKey val habitId: String,
    val nextAtMillis: Long? = null,
    val nextDay: Long? = null,
    val token: String = UUID.randomUUID().toString(),
    val snoozed: Boolean = false,
    val deliveredDay: Long? = null,
    val deliveredToken: String? = null,
)
