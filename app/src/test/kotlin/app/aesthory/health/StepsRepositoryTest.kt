package app.aesthory.health

import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StepsRepositoryTest {
    private class Source : StepsSource {
        var permission = StepsAccess.AVAILABLE
        var count: Long? = 1234
        var failure = false
        val ranges = mutableListOf<Pair<Instant, Instant>>()
        override suspend fun access() = permission
        override suspend fun total(start: Instant, end: Instant): StepTotal {
            if (failure) throw SecurityException()
            ranges += start to end
            return StepTotal(count, setOf("watch", "phone"))
        }
    }
    private fun repository(source: Source, instant: String = "2026-11-07T06:00:00Z", zone: String = "Asia/Kolkata") =
        StepsRepository(source, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)) { ZoneId.of(zone) }

    @Test fun aggregatesAreUsedOncePerDayAndRefreshReplacesReducedTotals() = runBlocking {
        val source = Source()
        val repo = repository(source)
        repo.refresh()
        assertEquals(7, source.ranges.size)
        assertEquals(1234L, repo.state.value.days.first().total.count)
        source.count = 80
        repo.refresh()
        assertEquals(80L, repo.state.value.days.first().total.count)
        assertEquals(7, repo.state.value.days.size)
        assertEquals(setOf("watch", "phone"), repo.state.value.days.first().total.sources)
    }
    @Test fun noDataAndZeroStayDistinct() = runBlocking {
        val source = Source().apply { count = null }
        val repo = repository(source)
        repo.refresh()
        assertNull(repo.state.value.days.first().total.count)
        source.count = 0
        repo.refresh()
        assertEquals(0L, repo.state.value.days.first().total.count)
    }
    @Test fun revocationAndFailuresClearPreviouslyVisibleData() = runBlocking {
        val source = Source()
        val repo = repository(source)
        repo.refresh()
        source.permission = StepsAccess.PERMISSION
        repo.refresh()
        assertTrue(repo.state.value.days.isEmpty())
        assertNull(repo.state.value.checked)
        source.permission = StepsAccess.AVAILABLE
        source.failure = true
        repo.refresh()
        assertEquals(StepsAccess.PERMISSION, repo.state.value.access)
        assertTrue(repo.state.value.days.isEmpty())
    }
    @Test fun unavailableProviderDoesNotReadAnyRecords() = runBlocking {
        val source = Source().apply { permission = StepsAccess.UPDATE }
        val repo = repository(source)
        repo.refresh()
        assertTrue(source.ranges.isEmpty())
        assertEquals(StepsAccess.UPDATE, repo.state.value.access)
    }
    @Test fun dayBoundariesFollowDstAndTodayEndsAtRefreshTime() = runBlocking {
        val source = Source()
        val repo = repository(source, "2026-11-02T12:00:00Z", "America/New_York")
        repo.refresh()
        assertEquals(Instant.parse("2026-11-02T12:00:00Z"), source.ranges.first().second)
        val yesterday = source.ranges[1]
        assertEquals(25L, Duration.between(yesterday.first, yesterday.second).toHours())
        assertEquals(yesterday.second, source.ranges.first().first)
    }
    @Test fun permissionLostDuringReadDiscardsWholeResult() = runBlocking {
        val source = object : StepsSource {
            var checks = 0
            override suspend fun access() = if (checks++ == 0) StepsAccess.AVAILABLE else StepsAccess.PERMISSION
            override suspend fun total(start: Instant, end: Instant) = StepTotal(9)
        }
        val repo = StepsRepository(source)
        repo.refresh()
        assertTrue(repo.state.value.days.isEmpty())
        assertEquals(StepsAccess.PERMISSION, repo.state.value.access)
    }
}
