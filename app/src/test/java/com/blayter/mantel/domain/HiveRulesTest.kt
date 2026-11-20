package com.blayter.mantel.domain

import com.blayter.mantel.data.mock.MockContainers
import kotlin.time.Duration
import com.blayter.mantel.ui.hive.hiveGeometry
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HiveRulesTest {
    private val sample = MockContainers.sample

    @Test
    fun busiestPinsMissingFirstAndEndedLast() {
        val names = sortForHive(sample, HiveSort.Busiest).map { it.shortName }
        assertEquals("reach 2", names.first())
        assertEquals("quirky_banach", names.last())
        assertEquals(listOf("rxflow 2", "rxflow 1", "e2e-web-1", "reach 1"), names.subList(1, 5))
    }

    @Test
    fun idleContainersHoldTheirOrder() {
        fun idle(name: String, average: Double) =
            ContainerStatus(name, name, ContainerKind.Runner, Presence.Live(Duration.ZERO), averageCores = average)
        val a = listOf(idle("b", 0.0004), idle("a", 0.0001), idle("c", 0.0))
        val b = listOf(idle("b", 0.0), idle("a", 0.0003), idle("c", 0.0009))
        assertEquals(listOf("a", "b", "c"), sortForHive(a, HiveSort.Busiest).map { it.name })
        assertEquals(listOf("a", "b", "c"), sortForHive(b, HiveSort.Busiest).map { it.name })
    }

    @Test
    fun memoryAndNameSortsKeepThePins() {
        val byMemory = sortForHive(sample, HiveSort.Memory).map { it.shortName }
        assertEquals(listOf("reach 2", "rxflow 2", "krakentv 1"), byMemory.take(3))
        val byName = sortForHive(sample, HiveSort.Name).map { it.name }
        assertEquals("reach-runner-runner-2", byName.first())
        assertEquals("krakentv-runner-runner-1", byName[1])
        assertEquals("quirky_banach", byName.last())
    }

    @Test
    fun warningsReadAsTheMockupSentences() {
        val warnings = warningsFor(sample, memoryNearCapPercent = 85)
        assertEquals(
            listOf(
                Warning("rxflow 2", "has held about 12 cores for 14 min", Severity.Busy),
                Warning("reach 2", "should be running but has been gone for 3 min", Severity.Wrong),
                Warning("krakentv 1", "is at 90% of its 4 GB memory cap", Severity.Wrong, brief = "is at 90% of its memory cap"),
            ),
            warnings,
        )
    }

    @Test
    fun coralWarningsTakeTheLinesWhenNotAllFit() {
        val two = warningsFor(sample, memoryNearCapPercent = 85, max = 2)
        assertEquals(listOf("reach 2", "krakentv 1"), two.map { it.who })
        assertEquals(listOf(Severity.Wrong, Severity.Wrong), two.map { it.severity })
        val one = warningsFor(sample, memoryNearCapPercent = 85, max = 1)
        assertEquals(listOf("reach 2"), one.map { it.who })
    }

    @Test
    fun noCapMeansNoNearCapWarning() {
        val uncapped = sample.first { it.shortName == "rxflow 2" }
        assertEquals(false, uncapped.isMemoryNearCap(85))
        assertEquals("6.1 GB", formatMemory(uncapped))
        assertEquals("3.6 of 4.0 GB", formatMemory(sample.first { it.shortName == "krakentv 1" }))
        assertEquals("310 MB", formatMemory(sample.first { it.shortName == "rxflow 3" }))
    }

    @Test
    fun agesAndGlow() {
        assertEquals("20 s", formatAge(20.seconds))
        assertEquals("14 min", formatAge(14.minutes))
        assertEquals("2 h", formatAge(2.hours))
        assertEquals("6 d", formatAge(6.days))
        assertEquals(listOf("20s", "14m", "2h", "6d"), listOf(20.seconds, 14.minutes, 2.hours, 6.days).map(::formatAgeShort))
        assertEquals(1f, glowLevel(12.0), 0.0001f)
        assertEquals(1f, glowLevel(20.0), 0.0001f)
        assertEquals(0.5f, glowLevel(3.0), 0.0001f)
    }

    private fun geometry(count: Int) = hiveGeometry(count, 208f, 240f, 10f, -51f, 109f, 4, 980f, 618f)

    @Test
    fun twelveCellsAreFullSizeAndMoreShrink() {
        assertEquals(4, geometry(12).perRow)
        assertEquals(1f, geometry(12).scale, 0.0001f)
        assertEquals(1f, geometry(1).scale, 0.0001f)
        val twenty = geometry(20)
        assert(twenty.scale < 1f && twenty.scale > 0.7f) { "unexpected scale ${twenty.scale}" }
    }
}
