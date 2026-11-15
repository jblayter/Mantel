package com.blayter.mantel.domain

import java.util.Locale
import kotlin.time.Duration

/** The CI box itself: fixed facts for the header and chart captions. */
data class HostInfo(val name: String, val threads: Int, val memoryTotalGb: Int)

/** Live container count by kind at one moment; a series of these is the 30-minute trend. */
data class CountSample(val runners: Int, val databases: Int, val jobs: Int)

/** Recent host samples, oldest first. Glances keeps no history, so the app holds these. */
data class HostTrends(
    val cpuPercent: List<Float>,
    val memoryGb: List<Float>,
    val load: List<Float>,
    val load5: Float,
    val load15: Float,
)

sealed interface DeployOutcome {
    /** [progress] is an estimate in 0..1, or null when there is nothing to base one on. */
    data class InProgress(val elapsed: Duration, val progress: Float?) : DeployOutcome
    data class Failed(val failedStep: String, val ago: Duration) : DeployOutcome
    data class Passed(val took: Duration, val ago: Duration) : DeployOutcome
}

/** One GitHub Actions workflow run. */
data class DeployRun(
    val repo: String,
    val workflow: String,
    val commitTitle: String,
    val branch: String,
    val outcome: DeployOutcome,
)

/** Everything the main screen shows. */
data class DashboardState(
    val host: HostInfo,
    val containers: List<ContainerStatus>,
    val countTrend: List<CountSample>,
    val hostTrends: HostTrends,
    val deploys: List<DeployRun>,
)

/** "3:12" for a running timer. */
fun formatTimer(d: Duration): String =
    String.format(Locale.US, "%d:%02d", d.inWholeMinutes, d.inWholeSeconds % 60)

/** "2m 08s" for a finished run. */
fun formatTook(d: Duration): String =
    String.format(Locale.US, "%dm %02ds", d.inWholeMinutes, d.inWholeSeconds % 60)
