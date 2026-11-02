package app.aesthory

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aesthory.data.*
import app.aesthory.routines.*
import app.aesthory.timing.*
import java.io.FileInputStream
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimingDeviceTest {
    @Test fun persistedTimerAndRealNotificationActionsReachTheReceiverAndSnoozeSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as AesthoryApplication
        val db = AesthoryDatabase.open(context)
        var habitId: String? = null
        var routineId: String? = null
        var categoryId: String? = null
        var session: String? = null
        val oldPrefs = db.records().getReminderPreferences()
        val notices = context.getSystemService(NotificationManager::class.java)
        try {
            assumeTrue("Keep any existing manual timer intact", db.records().getTimer() == null)
            if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant " + context.packageName + " android.permission.POST_NOTIFICATIONS").use { fd -> FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
            val delivery = AndroidTimingDelivery(context)
            assertTrue(delivery.status().notifications)
            app.timing.savePreferences(ReminderPreferences(quietEnabled = false))
            categoryId = app.repository.createCategory("Native timer fixture")
            routineId = app.repository.createRoutine("Native timer fixture")
            val day = LocalDate.now()
            habitId = app.repository.addHabit(routineId, HabitDraft("Native timed action", categoryId, HabitSchedule(day),
                tracking = TrackingSettings(TrackingKind.DURATION, 1.0)))
            val id = habitId
            app.timing.start(id, day)
            session = db.records().getTimer()!!.sessionId
            waitUntil { notices.activeNotifications.any { it.tag == "active-timer" } }
            assertTrue(notices.activeNotifications.first { it.tag == "active-timer" }.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            delay(1200)
            app.timing.goalReached(session)
            assertTrue(db.records().getTimer()!!.goalAlerted)
            notices.activeNotifications.first { it.tag == "active-timer" }.notification.actions.first().actionIntent.send()
            waitUntil { db.records().getTimer()?.running == false }
            waitUntil { notices.activeNotifications.first { it.tag == "active-timer" }.notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER).not() }
            val paused = db.records().getTimer()!!
            delay(250)
            val recreated = TimingController(db, AndroidTimingClock(context), delivery)
            recreated.refresh()
            assertEquals(paused.accumulatedMillis, db.records().getTimer()!!.accumulatedMillis)
            notices.activeNotifications.first { it.tag == "active-timer" }.notification.actions.first().actionIntent.send()
            waitUntil { db.records().getTimer()?.running == true }
            app.repository.editHabit(id, HabitDraft("Native edited action", categoryId, HabitSchedule(day), tracking = TrackingSettings(TrackingKind.COUNT, 2.0)))
            app.timing.finish(session, 2, day, CompletionInput(CompletionStatus.DONE))
            assertNull(db.records().getTimer())
            val event = app.repository.history(id).single()
            assertEquals(1, event.definitionVersion)
            assertEquals(2L, event.timedSeconds)
            app.repository.undoCompletion(id, day)
            val minute = java.time.LocalTime.now().plusMinutes(1).let { it.hour * 60 + it.minute }
            app.timing.saveReminder(HabitReminder(id, minute))
            val scheduled = db.records().reminderRuntime(id)!!
            assertNotNull(scheduled.nextAtMillis)
            val due = scheduled.copy(nextAtMillis = System.currentTimeMillis() - 1000, nextDay = day.toEpochDay())
            db.records().saveReminderRuntime(due)
            val intent = Intent(context, TimingReceiver::class.java).setAction(AndroidTimingDelivery.REMIND)
                .setData(Uri.parse("aesthory://timing/" + AndroidTimingDelivery.REMIND + "/" + Uri.encode(id)))
                .putExtra("id", id).putExtra("token", due.token)
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).send()
            waitUntil { notices.activeNotifications.any { it.tag == "reminder:" + id } }
            notices.activeNotifications.first { it.tag == "reminder:" + id }.notification.actions.first().actionIntent.send()
            waitUntil { db.records().reminderRuntime(id)?.snoozed == true }
            val snoozed = db.records().reminderRuntime(id)!!
            assertTrue(snoozed.nextAtMillis!! - System.currentTimeMillis() in 9 * 60_000L..11 * 60_000L)
            app.timing.deliverReminder(id, due.token)
            assertFalse(notices.activeNotifications.any { it.tag == "reminder:" + id })
        } finally {
            session?.let { if (db.records().getTimer()?.sessionId == it) app.timing.discard(it) }
            habitId?.let { id ->
                AndroidTimingDelivery(context).cancelReminder(id)
                db.withTransaction {
                    val sql = db.openHelper.writableDatabase
                    listOf("reminder_runtime", "habit_reminders", "completion_events", "habit_product_links", "habit_reference_links", "habit_versions").forEach { table ->
                        sql.execSQL("DELETE FROM " + table + " WHERE habitId = ?", arrayOf(id))
                    }
                    sql.execSQL("DELETE FROM habits WHERE id = ?", arrayOf(id))
                }
            }
            routineId?.let { db.openHelper.writableDatabase.execSQL("DELETE FROM routines WHERE id = ?", arrayOf(it)) }
            categoryId?.let { db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?", arrayOf(it)) }
            if (habitId != null) app.timing.savePreferences(oldPrefs ?: ReminderPreferences())
            db.close()
        }
        Unit
    }
    private suspend fun waitUntil(predicate: suspend () -> Boolean) {
        repeat(100) { if (predicate()) return; delay(100) }
        throw AssertionError("Timed out waiting for Android notification action")
    }
}
