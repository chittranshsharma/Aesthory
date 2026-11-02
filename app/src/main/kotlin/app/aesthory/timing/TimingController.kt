package app.aesthory.timing

import androidx.room.withTransaction
import app.aesthory.backup.VaultUserError
import app.aesthory.data.*
import app.aesthory.routines.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TimingStatus(val notifications: Boolean, val timers: Boolean, val reminders: Boolean, val precise: Boolean, val backgroundIssue: Boolean = false)
interface TimingDelivery {
    fun status(): TimingStatus
    fun scheduleReminder(runtime: ReminderRuntime)
    fun cancelReminder(id: String)
    fun clearReminderNotice(id: String)
    fun publishReminder(title: String, runtime: ReminderRuntime): Boolean
    fun showTimer(timer: ActiveTimer?, title: String, now: TimingMoment, signal: Boolean = false)
    fun scheduleTimerGoal(timer: ActiveTimer, now: TimingMoment)
    fun cancelTimerGoal()
    fun clearIssue() {}
    fun sendTest(): Boolean = false
}

class TimingController(private val db: AesthoryDatabase, val clock: TimingClock, private val delivery: TimingDelivery) {
    private val dao = db.records()
    private val mutex = Mutex()
    val timer = dao.observeTimer()
    val reminders = dao.observeReminders()
    val preferences = dao.observeReminderPreferences()
    val habits = dao.observeActiveHabits()
    fun status() = delivery.status()
    fun sendTest() = delivery.sendTest()
    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }

    suspend fun refresh() = locked {
        delivery.clearIssue()
        recoverTimer()
        refreshTimer()
        refreshReminders()
    }
    private suspend fun recoverTimer() {
        val timer = dao.getTimer() ?: return
        if (!timer.running) return
        val now = clock.now()
        if (!TimerMath.sameBoot(timer, now)) dao.saveTimer(TimerMath.paused(timer, now, TimerRecovery.PHONE_RESTART))
        else if (TimerMath.elapsed(timer, now) == MAX_TIMER_MILLIS) dao.saveTimer(TimerMath.paused(timer, now))
    }
    suspend fun start(id: String, day: LocalDate) = locked {
        db.withTransaction {
            if (dao.getTimer() != null) throw VaultUserError("Finish or discard your current timer before starting another.")
            val habit = requireNotNull(dao.getHabit(id))
            require(habit.state == HabitState.ACTIVE && dao.getRoutine(habit.routineId)?.archived == false)
            val definition = requireNotNull(dao.getVersion(id, habit.currentVersion))
            val goal = if (definition.tracking.kind == TrackingKind.DURATION) definition.tracking.target?.times(1000.0)
                else definition.estimatedMinutes?.times(60_000.0)
            if (goal != null && (goal > MAX_TIMER_MILLIS || !goal.isFinite())) throw VaultUserError("This goal is longer than the seven-day timer limit. Reduce it or use manual time logging.")
            val now = clock.now()
            dao.saveTimer(ActiveTimer(sessionId = UUID.randomUUID().toString(), habitId = id, definitionVersion = habit.currentVersion,
                day = day.toEpochDay(), startedElapsedMillis = now.elapsed, startedWallMillis = now.wall, boot = now.boot, goalMillis = goal?.toLong()))
        }
        refreshTimer()
    }
    suspend fun pause(session: String) = locked {
        recoverTimer()
        db.withTransaction { dao.getTimer()?.takeIf { it.sessionId == session && it.running }?.let { dao.saveTimer(TimerMath.paused(it, clock.now())) } }
        refreshTimer()
    }
    suspend fun resume(session: String) = locked {
        db.withTransaction {
            dao.getTimer()?.takeIf { it.sessionId == session && !it.running }?.let {
                if (it.accumulatedMillis >= MAX_TIMER_MILLIS) throw VaultUserError("This timer has reached its seven-day limit. Review and save it, or discard it.")
                val now = clock.now()
                dao.saveTimer(it.copy(startedElapsedMillis = now.elapsed, startedWallMillis = now.wall, boot = now.boot))
            }
        }
        refreshTimer()
    }
    suspend fun discard(session: String) = locked {
        db.withTransaction { if (dao.getTimer()?.sessionId == session) dao.clearTimer() }
        refreshTimer()
    }
    suspend fun finish(session: String, seconds: Long, day: LocalDate, input: CompletionInput) = locked {
        require(seconds in 0..MAX_TIMER_MILLIS / 1000 && input.status in setOf(CompletionStatus.DONE, CompletionStatus.PARTIAL))
        db.withTransaction {
            val timer = dao.getTimer()?.takeIf { it.sessionId == session } ?: throw VaultUserError("That timer has already been saved or removed.")
            val definition = requireNotNull(dao.getVersion(timer.habitId, timer.definitionVersion))
            val completion = if (definition.tracking.kind == TrackingKind.DURATION) input.copy(value = seconds.toDouble()) else input
            completion.validateFor(definition.tracking)
            dao.insertEvent(CompletionEventEntity(habitId = timer.habitId, definitionVersion = timer.definitionVersion, dateDay = day.toEpochDay(),
                recordedAtMillis = clock.now().wall, status = completion.status, value = completion.value, checkedItems = completion.checkedItems.sorted(), timedSeconds = seconds))
            dao.clearTimer()
        }
        refreshTimer()
        refreshReminders()
    }
    suspend fun goalReached(session: String) = locked {
        recoverTimer()
        if (dao.getTimer()?.sessionId == session) refreshTimer()
    }
    private suspend fun refreshTimer() {
        val timer = dao.getTimer()
        if (timer == null) { delivery.cancelTimerGoal(); delivery.showTimer(null, "", clock.now()); return }
        val now = clock.now()
        val goalReached = timer.goalMillis?.let { TimerMath.elapsed(timer, now) >= it } == true
        val title = dao.getVersion(timer.habitId, timer.definitionVersion)?.title ?: "Your action"
        val signal = timer.running && goalReached && !timer.goalAlerted
        val updated = if (signal) timer.copy(goalAlerted = true).also { dao.saveTimer(it) } else timer
        delivery.showTimer(updated, title, now, signal)
        if (updated.running && !goalReached && delivery.status().timers) delivery.scheduleTimerGoal(updated, now) else delivery.cancelTimerGoal()
    }
    suspend fun saveReminder(rule: HabitReminder) = locked {
        requireNotNull(dao.getHabit(rule.habitId))
        if (rule.enabled && dao.allReminders().count { it.enabled && it.habitId != rule.habitId } >= 100) throw VaultUserError("You can turn on up to 100 action reminders. Turn another one off first.")
        db.withTransaction {
            dao.saveReminder(rule)
            val old = dao.reminderRuntime(rule.habitId)
            dao.saveReminderRuntime(ReminderRuntime(rule.habitId, deliveredDay = old?.deliveredDay))
        }
        val prefs = dao.getReminderPreferences() ?: ReminderPreferences().also { dao.saveReminderPreferences(it) }
        refreshReminder(rule, prefs, clock.now())
    }
    suspend fun savePreferences(prefs: ReminderPreferences) = locked { dao.saveReminderPreferences(prefs); refreshReminders() }
    private suspend fun eligible(rule: HabitReminder): HabitVersionEntity? {
        if (!rule.enabled) return null
        val habit = dao.getHabit(rule.habitId) ?: return null
        if (habit.state != HabitState.ACTIVE || dao.getRoutine(habit.routineId)?.archived != false) return null
        return dao.getVersion(habit.id, habit.currentVersion)
    }
    private suspend fun completedDays(id: String, today: Long): Set<Long> = dao.eventsBetween(id, today - 1, today + 7)
        .distinctBy { it.dateDay }.filter { it.status in setOf(CompletionStatus.DONE, CompletionStatus.SKIPPED) }.map { it.dateDay }.toSet()

    private suspend fun refreshReminders() {
        val prefs = dao.getReminderPreferences() ?: ReminderPreferences().also { dao.saveReminderPreferences(it) }
        val now = clock.now()
        // Cancel tags no longer backed by a rule after a restored snapshot.
        val rules = dao.allReminders()
        for (cached in dao.allReminderRuntime()) if (rules.none { it.habitId == cached.habitId }) delivery.cancelReminder(cached.habitId)
        for (rule in rules) refreshReminder(rule, prefs, now)
    }
    private suspend fun refreshReminder(rule: HabitReminder, prefs: ReminderPreferences, now: TimingMoment) {
            val today = Instant.ofEpochMilli(now.wall).atZone(now.zone).toLocalDate().toEpochDay()
            val old = dao.reminderRuntime(rule.habitId) ?: ReminderRuntime(rule.habitId)
            val definition = eligible(rule)
            val completed = completedDays(rule.habitId, today)
            val excluded = completed + listOfNotNull(old.deliveredDay)
            val slot = if (definition == null || prefs.paused || !delivery.status().reminders) null
                else if (old.snoozed && old.nextAtMillis != null && old.nextAtMillis > now.wall && old.nextDay !in completed) {
                    ReminderSlot(ReminderPlanner.quietUntil(old.nextAtMillis, prefs, now), requireNotNull(old.nextDay))
                } else runCatching { ReminderPlanner.next(definition.schedule.toDomain(), rule, prefs, now, excluded) }.getOrNull()
            val next = if (slot == null) old.copy(nextAtMillis = null, nextDay = null, snoozed = false)
                else old.copy(nextAtMillis = slot.atMillis, nextDay = slot.day, token = UUID.randomUUID().toString(),
                    snoozed = old.snoozed && old.nextDay == slot.day && old.nextAtMillis != null && old.nextAtMillis > now.wall)
            dao.saveReminderRuntime(next)
            if (next.nextAtMillis != null) delivery.scheduleReminder(next) else delivery.cancelReminder(rule.habitId)
            if (definition == null || prefs.paused || today in completed) delivery.clearReminderNotice(rule.habitId)
    }
    suspend fun deliverReminder(id: String, token: String) = locked {
        val runtime = dao.reminderRuntime(id) ?: return@locked
        if (runtime.token != token || runtime.nextAtMillis == null || runtime.nextDay == null) return@locked
        val now = clock.now()
        if (runtime.nextAtMillis > now.wall) { delivery.scheduleReminder(runtime); return@locked }
        val rule = dao.getReminder(id) ?: return@locked
        val definition = eligible(rule)
        val prefs = dao.getReminderPreferences() ?: ReminderPreferences()
        val quietEnd = ReminderPlanner.quietUntil(now.wall, prefs, now)
        if (quietEnd > now.wall && definition != null && !prefs.paused) {
            val later = runtime.copy(nextAtMillis = quietEnd, token = UUID.randomUUID().toString())
            dao.saveReminderRuntime(later); delivery.scheduleReminder(later); return@locked
        }
        val done = runtime.nextDay in completedDays(id, runtime.nextDay)
        if (definition != null && !prefs.paused && !done && definition.schedule.toDomain().isDueOn(LocalDate.ofEpochDay(runtime.nextDay)) &&
            now.wall - runtime.nextAtMillis <= 6 * 60 * 60_000L &&
            (runtime.snoozed || runtime.deliveredDay != runtime.nextDay) && delivery.publishReminder(definition.title, runtime)) {
            dao.saveReminderRuntime(runtime.copy(deliveredDay = runtime.nextDay, deliveredToken = runtime.token, nextAtMillis = null, nextDay = null, snoozed = false))
        }
        // Replan only this action: replacing other due tokens here would swallow simultaneous reminders.
        refreshReminder(rule, prefs, now)
    }
    suspend fun snooze(id: String, deliveredToken: String) = locked {
        val old = dao.reminderRuntime(id) ?: return@locked
        val rule = dao.getReminder(id) ?: return@locked
        val prefs = dao.getReminderPreferences() ?: ReminderPreferences()
        val now = clock.now()
        val day = old.deliveredDay ?: return@locked
        if (old.deliveredToken != deliveredToken || eligible(rule) == null || prefs.paused || day in completedDays(id, day)) return@locked
        val at = ReminderPlanner.quietUntil(now.wall + 10 * 60_000, prefs, now)
        val next = old.copy(nextAtMillis = at, nextDay = day, token = UUID.randomUUID().toString(), snoozed = true, deliveredToken = null)
        dao.saveReminderRuntime(next)
        delivery.cancelReminder(id)
        delivery.scheduleReminder(next)
    }
    suspend fun prepareForRestore() = locked {
        dao.allReminders().forEach { delivery.cancelReminder(it.habitId) }
        delivery.cancelTimerGoal()
        delivery.showTimer(null, "", clock.now())
    }
}
