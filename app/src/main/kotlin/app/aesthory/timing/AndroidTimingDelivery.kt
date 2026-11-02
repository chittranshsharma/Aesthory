package app.aesthory.timing

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.aesthory.AesthoryApplication
import app.aesthory.MainActivity
import app.aesthory.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AndroidTimingDelivery(private val context: Context) : TimingDelivery {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notices = context.getSystemService(NotificationManager::class.java)
    init {
        notices.createNotificationChannels(listOf(
            NotificationChannel(TIMERS, "Timers", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Timer controls and an alert when the goal is reached" },
            NotificationChannel(REMINDERS, "Routine reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Your chosen action reminders and snoozes" },
        ))
    }
    override fun status(): TimingStatus {
        val allowed = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && notices.areNotificationsEnabled()
        fun channel(id: String) = allowed && notices.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
        return TimingStatus(allowed, channel(TIMERS), channel(REMINDERS), Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms(),
            context.getSharedPreferences("timing_status", Context.MODE_PRIVATE).getBoolean("failed", false))
    }
    override fun clearIssue() { context.getSharedPreferences("timing_status", Context.MODE_PRIVATE).edit().putBoolean("failed", false).apply() }
    override fun sendTest(): Boolean {
        if (!status().reminders) return false
        return runCatching {
            notices.notify("reminder-test", 1, NotificationCompat.Builder(context, REMINDERS).setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Your reminders are ready").setContentText("This is your Aesthory test notification.")
                .setContentIntent(open()).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            true
        }.onFailure { issue() }.getOrDefault(false)
    }
    private fun issue() { context.getSharedPreferences("timing_status", Context.MODE_PRIVATE).edit().putBoolean("failed", true).apply() }
    private fun safe(block: () -> Unit) { runCatching(block).onFailure { issue() } }
    private fun broadcast(action: String, key: String, token: String? = null): PendingIntent {
        val intent = Intent(context, TimingReceiver::class.java).setAction(action).setData(Uri.parse("aesthory://timing/" + action + "/" + Uri.encode(key)))
            .putExtra("id", key).putExtra("token", token)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun open(timer: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(if (timer) "aesthory.OPEN_TIMER" else "aesthory.OPEN_TODAY")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, if (timer) 2 else 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    override fun scheduleReminder(runtime: ReminderRuntime) {
        safe { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, requireNotNull(runtime.nextAtMillis), broadcast(REMIND, runtime.habitId, runtime.token)) }
    }
    override fun cancelReminder(id: String) {
        safe { alarms.cancel(broadcast(REMIND, id)) }
        clearReminderNotice(id)
    }
    override fun clearReminderNotice(id: String) { safe { notices.cancel("reminder:" + id, 1); updateGroup() } }
    override fun publishReminder(title: String, runtime: ReminderRuntime): Boolean {
        if (!status().reminders) return false
        val builder = NotificationCompat.Builder(context, REMINDERS).setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Time for " + title).setContentText("Open Aesthory when you're ready.")
            .setContentIntent(open()).setAutoCancel(true).setOnlyAlertOnce(true).setGroup(GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, REMINDERS).setSmallIcon(R.drawable.ic_timer).setContentTitle("Your Aesthory reminder").build())
            .addAction(NotificationCompat.Action.Builder(0, "Snooze 10 min", broadcast(SNOOZE, runtime.habitId, runtime.token)).build())
        return runCatching { notices.notify("reminder:" + runtime.habitId, 1, builder.build()); updateGroup(); true }.onFailure { issue() }.getOrDefault(false)
    }
    private fun updateGroup() {
        if (!status().reminders) { notices.cancel("reminder-summary", 1); return }
        val count = notices.activeNotifications.count { it.tag?.startsWith("reminder:") == true }
        if (count < 2) { notices.cancel("reminder-summary", 1); return }
        notices.notify("reminder-summary", 1, NotificationCompat.Builder(context, REMINDERS).setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Your Aesthory plan").setContentText(count.toString() + " action reminders")
            .setGroup(GROUP).setGroupSummary(true).setSilent(true).setContentIntent(open()).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
    override fun showTimer(timer: ActiveTimer?, title: String, now: TimingMoment, signal: Boolean) {
        if (timer == null || !status().timers) { notices.cancel("active-timer", 1); return }
        val elapsed = TimerMath.elapsed(timer, now)
        val reached = timer.goalMillis?.let { elapsed >= it } == true
        val text = (if (timer.running) "Running" else "Paused") + " · " + formatTimer(elapsed) + if (reached) " · Goal reached" else ""
        val builder = NotificationCompat.Builder(context, TIMERS).setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title).setContentText(text).setContentIntent(open(timer = true))
            .setWhen(now.wall - elapsed).setUsesChronometer(timer.running).setShowWhen(timer.running)
            .setOngoing(timer.running).setSilent(!signal).setOnlyAlertOnce(!signal)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, TIMERS).setSmallIcon(R.drawable.ic_timer).setContentTitle("Your Aesthory timer").build())
            .addAction(NotificationCompat.Action.Builder(0, if (timer.running) "Pause" else "Resume", broadcast(if (timer.running) PAUSE else RESUME, timer.sessionId)).build())
        timer.goalMillis?.let { builder.setProgress((it / 1000).toInt().coerceAtLeast(1), (elapsed / 1000).toInt().coerceAtMost((it / 1000).toInt()), false) }
        safe { notices.notify("active-timer", 1, builder.build()) }
    }
    override fun scheduleTimerGoal(timer: ActiveTimer, now: TimingMoment) {
        val remaining = requireNotNull(timer.goalMillis) - TimerMath.elapsed(timer, now)
        if (remaining <= 0) return
        val pending = broadcast(GOAL, "current", timer.sessionId)
        val at = now.elapsed + remaining
        if (status().precise) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending); return }
            catch (_: SecurityException) { /* Access may be revoked between checking and scheduling. */ }
        }
        safe { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending) }
    }
    override fun cancelTimerGoal() { safe { alarms.cancel(broadcast(GOAL, "current")) } }
    companion object {
        const val TIMERS = "timers"
        const val REMINDERS = "routine-reminders"
        private const val GROUP = "aesthory-routines"
        const val REMIND = "aesthory.REMIND"
        const val SNOOZE = "aesthory.SNOOZE"
        const val PAUSE = "aesthory.PAUSE_TIMER"
        const val RESUME = "aesthory.RESUME_TIMER"
        const val GOAL = "aesthory.TIMER_GOAL"
    }
}

class TimingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val controller = (context.applicationContext as AesthoryApplication).timing
                val id = intent.getStringExtra("id")
                val token = intent.getStringExtra("token")
                when (intent.action) {
                    AndroidTimingDelivery.REMIND -> if (id != null && token != null) controller.deliverReminder(id, token)
                    AndroidTimingDelivery.SNOOZE -> if (id != null && token != null) controller.snooze(id, token)
                    AndroidTimingDelivery.PAUSE -> if (id != null) controller.pause(id)
                    AndroidTimingDelivery.RESUME -> if (id != null) controller.resume(id)
                    AndroidTimingDelivery.GOAL -> if (token != null) controller.goalReached(token)
                    else -> controller.refresh()
                }
            } catch (_: Exception) {
                context.getSharedPreferences("timing_status", Context.MODE_PRIVATE).edit().putBoolean("failed", true).apply()
            } finally { pending.finish() }
        }
    }
}

fun formatTimer(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
}
