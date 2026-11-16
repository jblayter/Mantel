package com.blayter.mantel.domain

import java.time.LocalTime
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
    /** [failedStep] is the job that failed, or null when GitHub did not say. */
    data class Failed(val failedStep: String?, val ago: Duration) : DeployOutcome
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

/** Where the deploys card's data stands. */
sealed interface DeployStatus {
    /** No answer from the deploy source yet. */
    data object Waiting : DeployStatus

    data object Live : DeployStatus

    /** GitHub rejected the token. The runs in the state are the last ones seen, at [lastSeenAt] if ever. */
    data class TokenExpired(val lastSeenAt: LocalTime?) : DeployStatus

    /** A token was pushed but could not be stored encrypted, so there is none. */
    data object TokenNotStored : DeployStatus
}

/**
 * The CI box has stopped answering. Everything else in the state is then the
 * last reading, and the screen shows it as stale.
 */
data class StaleReading(val unansweredFor: Duration, val lastReadingAt: LocalTime)

/** Everything the main screen shows. */
data class DashboardState(
    val host: HostInfo,
    val containers: List<ContainerStatus>,
    val countTrend: List<CountSample>,
    val hostTrends: HostTrends,
    val deploys: List<DeployRun>,
    val deployStatus: DeployStatus = DeployStatus.Live,
    /** Null while the CI box is answering. */
    val stale: StaleReading? = null,
)

/** "3:12" for a running timer. */
fun formatTimer(d: Duration): String =
    String.format(Locale.US, "%d:%02d", d.inWholeMinutes, d.inWholeSeconds % 60)

/** "2m 08s" for a finished run. */
fun formatTook(d: Duration): String =
    String.format(Locale.US, "%dm %02ds", d.inWholeMinutes, d.inWholeSeconds % 60)
