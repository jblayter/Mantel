package com.blayter.mantel.data.glances

import com.blayter.mantel.data.DashboardRepository
import com.blayter.mantel.data.DeploySource
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.Thresholds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Runs the parser over replies captured from the real CI box (trimmed to the fields Mantel reads). */
class GlancesParserTest {
    private fun reply(name: String) =
        checkNotNull(javaClass.getResource("/glances/$name.json")) { "missing fixture $name" }.readText()

    private fun snapshot() = GlancesParser.snapshot(
        GlancesParser.hostName(reply("system")), reply("containers"), reply("quicklook"), reply("mem"), reply("load"),
    )

    @Test
    fun readsTheHost() {
        val s = snapshot()
        assertEquals("ci-runner01", s.host.name)
        assertEquals(32, s.host.threads)
        assertEquals(59, s.host.memoryTotalGb)
        assertEquals(60916, s.memoryTotalMb)
        assertTrue(s.memoryUsedGb in 1f..59f)
        assertTrue(s.cpuPercent in 0f..100f)
    }

    @Test
    fun readsEveryContainer() {
        val byName = snapshot().containers.associateBy { it.name }
        assertEquals(8, byName.size)

        val postgres = byName.getValue("quizzical_morse")
        assertEquals("postgres:16-alpine", postgres.image)
        assertEquals(8.7, postgres.cpuPercent, 0.001)
        assertEquals(3.days, postgres.uptime)

        // The rxflow runners report an empty image list.
        assertEquals("", byName.getValue("rxflow-runner-runner-1").image)
        assertEquals(6.hours, byName.getValue("rxflow-runner-runner-1").uptime)
        assertEquals(4096, byName.getValue("krakentv-runner-runner-1").memoryLimitMb)
        assertEquals(7.days, byName.getValue("krakentv-runner-runner-1").uptime)
    }

    @Test
    fun realRepliesGiveTheRightKindsAndCaps() = runTest {
        val source = GlancesHostSource("http://box:61208/") { url -> reply(url.substringAfterLast('/')) }
        val noDeploys = object : DeploySource {
            override suspend fun recentRuns() = emptyList<DeployRun>()
        }
        val repository = DashboardRepository(source, noDeploys, Thresholds(85, 8.0, 10.minutes, 60.seconds), { Duration.ZERO })
        repository.onHostSnapshot(source.snapshot())

        val cells = repository.state.value!!.containers.associateBy { it.name }
        assertEquals(8, cells.size)
        assertTrue("all six expected runners are present", cells.values.none { it.isMissing })
        assertEquals(6, cells.values.count { it.kind == ContainerKind.Runner })
        assertEquals(2, cells.values.count { it.kind == ContainerKind.Database })
        assertNull("a limit equal to the host total is no cap", cells.getValue("magical_noyce").memoryCapMb)
        assertEquals(4096, cells.getValue("krakentv-runner-runner-1").memoryCapMb)
    }

    @Test
    fun skipsStoppedContainersAndSurvivesAnEmptyReply() {
        val two = """[{"name":"a","status":"exited"},{"name":"b","status":"running","image":"redis:7"}]"""
        assertEquals(listOf("b"), GlancesParser.containers(two).map { it.name })
        assertEquals("redis:7", GlancesParser.containers(two).single().image)
        assertTrue(GlancesParser.containers("{}").isEmpty())
        assertTrue(GlancesParser.containers("[]").isEmpty())
    }

    @Test
    fun readsUptimeWords() {
        assertEquals(6.hours, GlancesParser.uptime("6 hours"))
        assertEquals(1.hours, GlancesParser.uptime("an hour"))
        assertEquals(7.days, GlancesParser.uptime("a week"))
        assertEquals(14.days, GlancesParser.uptime("2 weeks"))
        assertEquals(5.minutes, GlancesParser.uptime("5 mins"))
        assertEquals(40.seconds, GlancesParser.uptime("40 secs"))
        assertEquals(1.days, GlancesParser.uptime("yesterday"))
        assertEquals(Duration.ZERO, GlancesParser.uptime("just now"))
        assertEquals(Duration.ZERO, GlancesParser.uptime(""))
    }
}
