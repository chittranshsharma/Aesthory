package app.aesthory.timing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aesthory.backup.RecordSnapshot
import app.aesthory.data.*
import app.aesthory.routines.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimingControllerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AesthoryDatabase
    private lateinit var repo: AesthoryRepository
    private lateinit var controller: TimingController
    private var now = TimingMoment(Instant.parse("2026-11-02T08:00:00Z").toEpochMilli(), 10000, 1, ZoneOffset.UTC)
    private val clock = TimingClock { now }
    private lateinit var delivery: FakeDelivery
    private lateinit var id: String
    private lateinit var category: String
    private val day = LocalDate.of(2026, 11, 2)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AesthoryDatabase::class.java).build()
        repo = AesthoryRepository(db)
        delivery = FakeDelivery()
        controller = TimingController(db, clock, delivery)
        val routine = repo.createRoutine("Routine")
        category = repo.createCategory("Care")
        id = repo.addHabit(routine, HabitDraft("Original", category, HabitSchedule(day), "Original steps", tracking = TrackingSettings(TrackingKind.DURATION, 60.0)))
        controller.refresh()
    }
    @After fun close() { db.close() }
    private fun advance(ms: Long) { now = now.copy(wall = now.wall + ms, elapsed = now.elapsed + ms) }
    @Test fun recreatedControllerRetainsRunningTimeAndPauseResumeSurvivesAnotherRecreation() = runBlocking {
        controller.start(id, day)
        advance(12345)
        controller = TimingController(db, clock, delivery)
        controller.refresh()
        assertEquals(12345L, TimerMath.elapsed(db.records().getTimer()!!, now))
        val session = db.records().getTimer()!!.sessionId
        controller.pause(session); advance(100000); controller = TimingController(db, clock, delivery)
        controller.refresh(); assertEquals(12345L, TimerMath.elapsed(db.records().getTimer()!!, now))
        controller.resume(session); advance(5000)
        assertEquals(17345L, TimerMath.elapsed(db.records().getTimer()!!, now))
    }
    @Test fun onlyOneTimerCanStartAndStaleControlsCannotReplaceOrRemoveIt() = runBlocking {
        controller.start(id, day)
        val saved = db.records().getTimer()!!
        val other = TimingController(db, clock, delivery)
        assertTrue(runCatching { other.start(id, day) }.isFailure)
        controller.pause("stale"); controller.discard("stale"); controller.resume("stale")
        assertEquals(saved, db.records().getTimer())
    }
    @Test fun finishUsesOriginalDefinitionAndIsAtomicAndCannotBeAppliedTwice() = runBlocking {
        controller.start(id, day)
        val session = db.records().getTimer()!!.sessionId
        repo.editHabit(id, HabitDraft("Changed", category, HabitSchedule(day), tracking = TrackingSettings(TrackingKind.COUNT, 2.0)))
        controller.finish(session, 35, day, CompletionInput(CompletionStatus.PARTIAL))
        val event = repo.history(id).single()
        assertEquals(1, event.definitionVersion)
        assertEquals(35.0, event.value!!, 0.0)
        assertEquals(35L, event.timedSeconds)
        assertNull(db.records().getTimer())
        assertTrue(runCatching { controller.finish(session, 35, day, CompletionInput(CompletionStatus.DONE)) }.isFailure)
        assertEquals(1, repo.history(id).size)
    }
    @Test fun invalidResultsKeepTimerAndGoalAlertIsSentOnlyOnceWithoutAutoLogging() = runBlocking {
        repo.editHabit(id, HabitDraft("Count", category, HabitSchedule(day), estimatedMinutes = 1, tracking = TrackingSettings(TrackingKind.COUNT, 2.0)))
        controller.start(id, day)
        val session = db.records().getTimer()!!.sessionId
        assertTrue(runCatching { controller.finish(session, 10, day, CompletionInput(CompletionStatus.DONE)) }.isFailure)
        assertNotNull(db.records().getTimer())
        advance(61000); controller.goalReached(session); controller.goalReached(session)
        assertEquals(1, delivery.signals)
        assertTrue(repo.history(id).isEmpty())
        controller.finish(session, 61, day, CompletionInput(CompletionStatus.DONE, 2.0))
        assertEquals(61L, repo.history(id).single().timedSeconds)
    }
    @Test fun rebootRecoveryAndBackupArePausedAndRestoredRemindersDoNotStartAutomatically() = runBlocking {
        controller.start(id, day); advance(10000)
        val snapshot = RecordSnapshot.capture(db, now.wall, now)
        RecordSnapshot.validate(context, snapshot)
        assertTrue(db.records().getTimer()!!.running)
        now = now.copy(wall = now.wall + 50000, elapsed = 100, boot = 2)
        controller.refresh()
        assertFalse(db.records().getTimer()!!.running)
        assertEquals(TimerRecovery.PHONE_RESTART, db.records().getTimer()!!.recovery)
        assertEquals(0, delivery.signals)
        controller.saveReminder(HabitReminder(id, 9 * 60))
        val exported = RecordSnapshot.decode(RecordSnapshot.encode(RecordSnapshot.capture(db, now.wall, now)))
        RecordSnapshot.validate(context, exported)
        RecordSnapshot.replace(db, exported); controller.refresh()
        assertFalse(db.records().getTimer()!!.running)
        assertEquals(TimerRecovery.BACKUP, db.records().getTimer()!!.recovery)
        assertTrue(db.records().getReminderPreferences()!!.paused)
        assertNull(db.records().reminderRuntime(id)!!.nextAtMillis)
    }
    @Test fun remindersDeduplicateStaleCallbacksSnoozeAndRespectCompletionAndPause() = runBlocking {
        controller.saveReminder(HabitReminder(id, 8 * 60 + 1))
        val planned = db.records().reminderRuntime(id)!!
        now = now.copy(wall = planned.nextAtMillis!!, elapsed = now.elapsed + 60000)
        controller.deliverReminder(id, planned.token)
        controller.deliverReminder(id, planned.token)
        assertEquals(1, delivery.reminderPosts)
        val delivered = db.records().reminderRuntime(id)!!
        controller.snooze(id, delivered.deliveredToken!!)
        val snoozed = db.records().reminderRuntime(id)!!
        assertTrue(snoozed.snoozed)
        assertEquals(now.wall + 600000, snoozed.nextAtMillis)
        controller.snooze(id, delivered.deliveredToken)
        assertEquals(snoozed, db.records().reminderRuntime(id))
        repo.recordCompletion(id, day, CompletionInput(CompletionStatus.DONE, 60.0))
        controller.refresh()
        controller.deliverReminder(id, snoozed.token)
        assertEquals(1, delivery.reminderPosts)
        repo.setHabitState(id, HabitState.PAUSED); controller.refresh()
        assertNull(db.records().reminderRuntime(id)!!.nextAtMillis)
    }
    @Test fun simultaneousRemindersBothDeliverInsteadOfInvalidatingEachOthersTokens() = runBlocking {
        val routine = db.records().getHabit(id)!!.routineId
        val secondId = repo.addHabit(routine, HabitDraft("Second", category, HabitSchedule(day)))
        controller.saveReminder(HabitReminder(id, 8 * 60 + 1))
        controller.saveReminder(HabitReminder(secondId, 8 * 60 + 1))
        val first = db.records().reminderRuntime(id)!!
        val second = db.records().reminderRuntime(secondId)!!
        now = now.copy(wall = first.nextAtMillis!!, elapsed = now.elapsed + 60000)
        controller.deliverReminder(id, first.token)
        controller.deliverReminder(secondId, second.token)
        assertEquals(2, delivery.reminderPosts)
        assertEquals(day.toEpochDay(), db.records().reminderRuntime(id)!!.deliveredDay)
        assertEquals(day.toEpochDay(), db.records().reminderRuntime(secondId)!!.deliveredDay)
    }
    @Test fun permissionDenialAndClockReplanningLeaveTimerUsableAndDoNotReplayOldReminder() = runBlocking {
        delivery.allowed = false
        controller.start(id, day); advance(1000)
        assertEquals(1000L, TimerMath.elapsed(db.records().getTimer()!!, now))
        controller.saveReminder(HabitReminder(id, 8 * 60 + 1))
        assertNull(db.records().reminderRuntime(id)!!.nextAtMillis)
        delivery.allowed = true; controller.refresh()
        val old = db.records().reminderRuntime(id)!!
        now = now.copy(wall = old.nextAtMillis!! + 120000, elapsed = now.elapsed + 180000)
        controller.refresh()
        controller.deliverReminder(id, old.token)
        assertEquals(0, delivery.reminderPosts)
        assertTrue(db.records().getTimer()!!.running)
    }
}

private class FakeDelivery : TimingDelivery {
    var allowed = true
    var signals = 0
    var reminderPosts = 0
    override fun status() = TimingStatus(allowed, allowed, allowed, false)
    override fun scheduleReminder(runtime: ReminderRuntime) {}
    override fun cancelReminder(id: String) {}
    override fun clearReminderNotice(id: String) {}
    override fun publishReminder(title: String, runtime: ReminderRuntime): Boolean { if (!allowed) return false; reminderPosts++; return true }
    override fun showTimer(timer: ActiveTimer?, title: String, now: TimingMoment, signal: Boolean) { if (signal) signals++ }
    override fun scheduleTimerGoal(timer: ActiveTimer, now: TimingMoment) {}
    override fun cancelTimerGoal() {}
}
