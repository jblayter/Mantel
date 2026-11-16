package com.blayter.mantel.data

import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.HostInfo
import kotlin.time.Duration

/** One container as Glances reports it (`/api/4/containers`), before any Mantel rules. */
data class RawContainer(
    val name: String,
    val image: String,
    val cpuPercent: Double,
    val memoryMb: Int,
    /** Equal to the host total when the container has no cap. */
    val memoryLimitMb: Int,
    val uptime: Duration,
)

/** One poll of the CI box: containers plus `/quicklook`, `/mem` and `/load`. */
data class HostSnapshot(
    val host: HostInfo,
    val memoryTotalMb: Int,
    val containers: List<RawContainer>,
    val cpuPercent: Float,
    val memoryUsedGb: Float,
    val load1: Float,
    val load5: Float,
    val load15: Float,
)

/** The CI box. Today a mock; later the Glances REST client. */
interface HostSource {
    suspend fun snapshot(): HostSnapshot
}

/** Why deploy status cannot be read at all, as opposed to a passing network failure. */
enum class DeployFailure {
    /** GitHub answered 401: the token has expired or been revoked. */
    TokenRejected,

    /** A token was pushed, but this TV could not encrypt it, so it was not kept. */
    TokenNotStored,
}

class DeploySourceException(val failure: DeployFailure) : Exception(failure.name)

/**
 * Recent workflow runs, the ones to show, in display order. Called every
 * second so timers tick; a real source answers from its last fetch and only
 * goes to the network at its own poll interval. Throws [DeploySourceException]
 * when the token is the problem, anything else for a failure worth retrying.
 */
interface DeploySource {
    suspend fun recentRuns(): List<DeployRun>
}
