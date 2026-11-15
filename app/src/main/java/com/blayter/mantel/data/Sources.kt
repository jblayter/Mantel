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

/** Recent workflow runs, newest first. Today a mock; later the GitHub Actions client. */
interface DeploySource {
    suspend fun recentRuns(): List<DeployRun>
}
