package com.blayter.mantel.data

import com.blayter.mantel.data.mock.MockDashboard
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.Presence
import com.blayter.mantel.domain.Thresholds
import com.blayter.mantel.domain.shortNameOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DashboardRepositoryTest {
    private var clock: Duration = Duration.ZERO
    private val hostMb = 59 * 1024
    private val thresholds = Thresholds(85, 8.0, 10.minutes, 60.seconds)
    private val runners = listOf("rxflow-runner-runner-1", "reach-runner-runner-1")

    private val unused = object : HostSource {
        override suspend fun snapshot() = error("not polled in tests")
    }
    private val noDeploys = object : DeploySource {
        override suspend fun recentRuns() = emptyList<com.blayter.mantel.domain.DeployRun>()
    }

    private fun repository() = DashboardRepository(unused, noDeploys, thresholds, { clock }, expectedRunners = runners)

    private fun raw(name: String, image: String = "x", cpu: Double = 50.0, mem: Int = 100, limit: Int = hostMb) =
        RawContainer(name, image, cpu, mem, limit, 1.minutes)

    private fun snapshot(vararg containers: RawContainer) =
        HostSnapshot(MockDashboard.host, hostMb, containers.toList(), 10f, 20f, 1f, 1f, 1f)

    private fun DashboardRepository.poll(vararg containers: RawContainer) =
        onHostSnapshot(snapshot(*containers)).let { state.value!!.containers.associateBy { it.name } }

    @Test
    fun classifiesKindsAndNames() {
        val seen = repository().poll(
            raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1"),
            raw("magical_noyce", image = "postgres:16"), raw("redis-test-7f3a"),
        )
        assertEquals(ContainerKind.Runner, seen.getValue("rxflow-runner-runner-1").kind)
        assertEquals("rxflow 1", seen.getValue("rxflow-runner-runner-1").shortName)
        assertEquals(ContainerKind.Database, seen.getValue("magical_noyce").kind)
        assertEquals(ContainerKind.Job, seen.getValue("redis-test-7f3a").kind)
        assertEquals("redis-test", seen.getValue("redis-test-7f3a").shortName)
        assertEquals("postgres-e2e", shortNameOf("rxflow-postgres-e2e"))
        assertEquals("e2e-web-1", shortNameOf("rxflow-e2e-web-1"))
    }

    @Test
    fun hostTotalLimitMeansNoCap() {
        val seen = repository().poll(
            raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1", mem = 3690, limit = 4096),
        )
        assertNull(seen.getValue("rxflow-runner-runner-1").memoryCapMb)
        assertEquals(4096, seen.getValue("reach-runner-runner-1").memoryCapMb)
        assertTrue(seen.getValue("reach-runner-runner-1").isMemoryNearCap(85))
    }

    @Test
    fun absentExpectedRunnerIsMissingAndCountsUp() {
        val repo = repository()
        repo.poll(raw("rxflow-runner-runner-1"))
        clock += 3.minutes
        val gone = repo.poll(raw("rxflow-runner-runner-1")).getValue("reach-runner-runner-1")
        assertEquals(Presence.Missing(3.minutes), gone.presence)
        // Back again: no longer missing, and a later absence starts from zero.
        assertTrue(repo.poll(raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1")).getValue("reach-runner-runner-1").isLive)
        clock += 1.minutes
        assertEquals(Presence.Missing(Duration.ZERO), repo.poll(raw("rxflow-runner-runner-1")).getValue("reach-runner-runner-1").presence)
    }

    @Test
    fun otherContainersEndLingerThenDisappear() {
        val repo = repository()
        val both = arrayOf(raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1"))
        repo.poll(*both, raw("quirky_banach", image = "postgres:16"))
        clock += 3.seconds
        assertEquals(Presence.Ended(Duration.ZERO), repo.poll(*both).getValue("quirky_banach").presence)
        clock += 20.seconds
        val lingering = repo.poll(*both).getValue("quirky_banach")
        assertEquals(Presence.Ended(20.seconds), lingering.presence)
        assertEquals(ContainerKind.Database, lingering.kind)
        clock += 41.seconds
        assertTrue("quirky_banach" !in repo.poll(*both))
    }

    @Test
    fun sustainedCpuNeedsTheFullDuration() {
        val repo = repository()
        val hot = arrayOf(raw("rxflow-runner-runner-1", cpu = 1180.0), raw("reach-runner-runner-1"))
        assertNull(repo.poll(*hot).getValue("rxflow-runner-runner-1").sustainedFor)
        clock += 9.minutes
        assertNull(repo.poll(*hot).getValue("rxflow-runner-runner-1").sustainedFor)
        clock += 1.minutes
        assertEquals(10.minutes, repo.poll(*hot).getValue("rxflow-runner-runner-1").sustainedFor)
        // Cooling down clears it once the moving average drops below the threshold.
        val cool = arrayOf(raw("rxflow-runner-runner-1", cpu = 10.0), raw("reach-runner-runner-1"))
        repeat(DashboardRepository.AVERAGE_SAMPLES) { repo.poll(*cool) }
        assertNull(repo.poll(*cool).getValue("rxflow-runner-runner-1").sustainedFor)
    }

    @Test
    fun seededMockOpensOnTheMockupScreen() {
        val repo = DashboardRepository(unused, noDeploys, thresholds, { clock }, seed = MockDashboard.seed)
        repo.onHostSnapshot(snapshot(raw("rxflow-runner-runner-2", cpu = 1180.0)))
        val state = repo.state.value!!
        val byName = state.containers.associateBy { it.name }
        assertEquals(14.minutes, byName.getValue("rxflow-runner-runner-2").sustainedFor)
        assertEquals(Presence.Missing(3.minutes), byName.getValue("reach-runner-runner-2").presence)
        assertEquals(Presence.Ended(20.seconds), byName.getValue("quirky_banach").presence)
        assertEquals(DashboardRepository.TREND_SAMPLES, state.countTrend.size)
        assertEquals(DashboardRepository.TREND_SAMPLES, state.hostTrends.cpuPercent.size)
    }

    @Test
    fun turnsStaleAfterThreeUnansweredPollsAndRecovers() {
        val repository = DashboardRepository(
            unused, noDeploys, thresholds, { clock }, expectedRunners = runners,
            wallClock = { java.time.LocalTime.of(14, 32) },
        )
        repository.onHostFailure()
        assertNull("nothing to show before the first reading", repository.state.value)

        repository.poll(raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1"))
        clock += 3.seconds
        repository.onHostFailure()
        clock += 3.seconds
        repository.onHostFailure()
        assertNull(repository.state.value!!.stale)

        clock += 114.seconds
        repository.onHostFailure()
        val stale = repository.state.value!!.stale!!
        assertEquals(2.minutes, stale.unansweredFor)
        assertEquals(java.time.LocalTime.of(14, 32), stale.lastReadingAt)
        assertEquals("the last reading stays on screen", 2, repository.state.value!!.containers.size)

        repository.poll(raw("rxflow-runner-runner-1"), raw("reach-runner-runner-1"))
        assertNull(repository.state.value!!.stale)
    }

    @Test
    fun aRejectedTokenKeepsTheLastRunsAndSaysWhenTheyWereSeen() {
        val seenAt = java.time.LocalTime.of(14, 14)
        val repository = DashboardRepository(unused, noDeploys, thresholds, { clock }, expectedRunners = runners, wallClock = { seenAt })
        repository.poll(raw("rxflow-runner-runner-1"))
        assertEquals(com.blayter.mantel.domain.DeployStatus.Waiting, repository.state.value!!.deployStatus)

        val run = com.blayter.mantel.domain.DeployRun(
            "rxflow", "Deploy", "Fix", "main", com.blayter.mantel.domain.DeployOutcome.Passed(2.minutes, 6.minutes),
        )
        repository.onDeploys(listOf(run))
        assertEquals(com.blayter.mantel.domain.DeployStatus.Live, repository.state.value!!.deployStatus)

        repository.onDeployFailure(DeployFailure.TokenRejected)
        assertEquals(com.blayter.mantel.domain.DeployStatus.TokenExpired(seenAt), repository.state.value!!.deployStatus)
        assertEquals(listOf(run), repository.state.value!!.deploys)

        // The status survives the next host poll.
        repository.poll(raw("rxflow-runner-runner-1"))
        assertEquals(com.blayter.mantel.domain.DeployStatus.TokenExpired(seenAt), repository.state.value!!.deployStatus)
    }
}
