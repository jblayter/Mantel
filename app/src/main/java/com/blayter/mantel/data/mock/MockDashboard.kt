package com.blayter.mantel.data.mock

import com.blayter.mantel.data.DashboardRepository.Companion.TREND_SAMPLES
import com.blayter.mantel.data.RepositorySeed
import com.blayter.mantel.domain.CountSample
import com.blayter.mantel.domain.HostInfo
import com.blayter.mantel.domain.Presence
import kotlin.math.min
import kotlin.math.sin

/** History behind the approved mockup's sample screen (Hive.dc.html), value for value. */
object MockDashboard {
    val host = HostInfo(name = "ci-runner01", threads = 32, memoryTotalGb = 59)

    private val countTrend = List(TREND_SAMPLES) { i ->
        CountSample(
            runners = if (i >= 54) 5 else 6,
            databases = when {
                i < 20 -> 1
                i < 34 -> 0
                i < 44 -> 2
                i < 59 -> 4
                else -> 3
            },
            jobs = when {
                i < 12 -> 1
                i < 34 -> 0
                i < 40 -> 1
                i < 52 -> 3
                else -> 2
            },
        )
    }

    private fun busy(i: Int) = if (i > 32) 1f else 0f

    /** What the repository starts from so the first frame is the mockup's screen. */
    val seed = RepositorySeed(
        countTrend = countTrend,
        cpuPercent = List(TREND_SAMPLES) { i -> 14 + busy(i) * 44 + 8 * sin(i * 0.4f) + 5 * sin(i * 1.3f) },
        memoryGb = List(TREND_SAMPLES) { i -> 15.5f + busy(i) * 5.2f + 0.6f * sin(i * 0.3f) },
        load = List(TREND_SAMPLES) { i -> 2.2f + busy(i) * min(8.5f, (i - 32) * 0.7f) + 0.5f * sin(i * 0.6f) },
        sustainedFor = MockContainers.sample.filter { it.sustainedFor != null }.associate { it.name to it.sustainedFor!! },
        averageCores = MockContainers.sample.filter { it.isLive }.associate { it.name to it.averageCores },
        missingFor = MockContainers.sample.mapNotNull { c -> (c.presence as? Presence.Missing)?.let { c.name to it.goneFor } }.toMap(),
        ended = MockContainers.sample.mapNotNull { c -> (c.presence as? Presence.Ended)?.let { c to it.ago } },
    )
}
