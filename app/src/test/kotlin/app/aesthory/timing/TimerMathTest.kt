package app.aesthory.timing

import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class TimerMathTest {
    private val start = TimingMoment(1_000_000, 100_000, 5, ZoneOffset.UTC)
    private fun timer() = ActiveTimer(sessionId = UUID.randomUUID().toString(), habitId = "habit", definitionVersion = 1, day = 20000,
        accumulatedMillis = 5000, startedElapsedMillis = start.elapsed, startedWallMillis = start.wall, boot = start.boot, goalMillis = 60000)
    @Test fun processRestartAndDeepSleepUseElapsedClockEvenIfWallTimeChanges() {
        val saved = timer()
        val later = start.copy(wall = 42, elapsed = start.elapsed + 120000)
        assertTrue(TimerMath.sameBoot(saved, later))
        assertEquals(125000L, TimerMath.elapsed(saved.copy(), later))
    }
    @Test fun pauseAndResumeExcludePausedTimeAndKeepSubsecondPrecision() {
        val paused = TimerMath.paused(timer(), start.copy(elapsed = start.elapsed + 1234))
        assertEquals(6234L, paused.accumulatedMillis)
        assertEquals(6234L, TimerMath.elapsed(paused, start.copy(elapsed = 999999)))
        val resumed = paused.copy(startedElapsedMillis = 500000, startedWallMillis = 2000000, boot = 5)
        assertEquals(9234L, TimerMath.elapsed(resumed, start.copy(elapsed = 503000)))
    }
    @Test fun rebootIsExplicitAndBackwardWallTimeCannotCreateNegativeElapsedTime() {
        val reboot = start.copy(wall = start.wall + 300000, elapsed = 1000, boot = 6)
        assertFalse(TimerMath.sameBoot(timer(), reboot))
        val recovered = TimerMath.paused(timer(), reboot, TimerRecovery.PHONE_RESTART)
        assertFalse(recovered.running)
        assertEquals(305000L, recovered.accumulatedMillis)
        assertEquals(TimerRecovery.PHONE_RESTART, recovered.recovery)
        assertEquals(5000L, TimerMath.elapsed(timer(), reboot.copy(wall = 1)))
    }
    @Test fun portableBackupFreezesTimeWithoutChangingLiveTimerAndCapsExtremeGaps() {
        val saved = timer()
        val portable = TimerMath.portable(saved, start.copy(elapsed = start.elapsed + 10000))
        assertFalse(portable.running)
        assertTrue(saved.running)
        assertEquals(TimerRecovery.BACKUP, portable.recovery)
        assertEquals(15000L, portable.accumulatedMillis)
        assertEquals(MAX_TIMER_MILLIS, TimerMath.elapsed(saved, start.copy(elapsed = Long.MAX_VALUE)))
    }
}
