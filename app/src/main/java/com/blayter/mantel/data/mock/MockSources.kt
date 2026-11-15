package com.blayter.mantel.data.mock

import com.blayter.mantel.data.DeploySource
import com.blayter.mantel.data.HostSnapshot
import com.blayter.mantel.data.HostSource
import com.blayter.mantel.data.RawContainer
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.Presence
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Stands in for Glances. Starts from the mockup's sample containers and moves
 * them the way the mockup's script does: CPU wobbles around each container's
 * base, and host CPU, memory and load follow. A short-lived job database also
 * comes and goes so the "ended" state and the count trend keep changing.
 */
class MockHostSource(private val elapsed: () -> Duration, private val random: Random = Random.Default) : HostSource {
    private val host = MockDashboard.host
    private val hostMemoryMb = host.memoryTotalGb * 1024
    private val base = MockContainers.sample.filter { it.isLive }
    private var tick = 0
    private var load = 0f

    override suspend fun snapshot(): HostSnapshot {
        tick++
        val t = elapsed()
        val containers = base.map { c ->
            val wobble = 1 + 0.22 * sin(tick * 0.7 + c.name.length) + (random.nextDouble() - 0.5) * 0.3
            RawContainer(
                name = c.name,
                image = imageFor(c.kind),
                cpuPercent = max(0.02, c.cores * wobble) * 100,
                memoryMb = c.memoryMb,
                memoryLimitMb = c.memoryCapMb ?: hostMemoryMb,
                uptime = (c.presence as Presence.Live).uptime + t,
            )
        } + listOfNotNull(visitor(t))

        val cores = containers.sumOf { it.cpuPercent }.toFloat() / 100
        if (tick == 1) load = cores * 0.45f
        load += (cores * 0.45f - load) * 0.08f
        return HostSnapshot(
            host = host,
            memoryTotalMb = hostMemoryMb,
            containers = containers,
            cpuPercent = min(100f, (cores + 0.8f) / host.threads * 100),
            memoryUsedGb = 21.4f + 0.3f * sin(tick * 0.2f),
            load1 = load,
            load5 = 7.2f,
            load15 = 5.1f,
        )
    }

    /** A job database that runs for [VISIT_LENGTH] out of every [VISIT_CYCLE], under a new name each time. */
    private fun visitor(t: Duration): RawContainer? {
        if (t < FIRST_VISIT) return null
        val sinceFirst = t - FIRST_VISIT
        val cycle = (sinceFirst / VISIT_CYCLE).toInt()
        val into = sinceFirst - VISIT_CYCLE * cycle
        if (into >= VISIT_LENGTH) return null
        return RawContainer(
            name = VISITOR_NAMES[cycle % VISITOR_NAMES.size],
            image = imageFor(ContainerKind.Database),
            cpuPercent = 50 + 20 * sin(tick * 0.5),
            memoryMb = 240,
            memoryLimitMb = hostMemoryMb,
            uptime = into,
        )
    }

    private fun imageFor(kind: ContainerKind) = when (kind) {
        ContainerKind.Runner -> "actions-runner:latest"
        ContainerKind.Database -> "postgres:16"
        ContainerKind.Job -> "job:latest"
    }

    private companion object {
        // The seeded "ended" cell lingers for the first 40 s; the visitor waits for it to clear.
        val FIRST_VISIT = 45.seconds
        val VISIT_LENGTH = 40.seconds
        val VISIT_CYCLE = 110.seconds
        val VISITOR_NAMES = listOf("eager_lovelace", "zen_kepler", "quirky_banach")
    }
}

/**
 * Stands in for the GitHub Actions client with the mockup's invented runs. The
 * rxflow deploy counts up, finishes, rests for a minute and goes out again.
 */
class MockDeploySource(private val elapsed: () -> Duration) : DeploySource {
    override suspend fun recentRuns(): List<DeployRun> {
        val t = elapsed()
        val sinceFirstStart = t + ALREADY_RUNNING
        val cycle = (sinceFirstStart / CYCLE).toInt()
        val into = sinceFirstStart - CYCLE * cycle
        fun rxflow(cycleIndex: Int, outcome: DeployOutcome) =
            DeployRun("rxflow", "Deploy production", COMMITS[cycleIndex % COMMITS.size], "main", outcome)

        return buildList {
            if (into < RUN_LENGTH) {
                add(rxflow(cycle, DeployOutcome.InProgress(into, (into / RUN_LENGTH).toFloat())))
                if (cycle > 0) add(rxflow(cycle - 1, DeployOutcome.Passed(RUN_LENGTH, into + CYCLE - RUN_LENGTH)))
            } else {
                add(rxflow(cycle, DeployOutcome.Passed(RUN_LENGTH, into - RUN_LENGTH)))
            }
            add(
                DeployRun(
                    "reach", "Deploy production", "Fix hydration mismatch", "main",
                    DeployOutcome.Failed(failedStep = "e2e-tests", ago = 6.minutes + t),
                ),
            )
            add(
                DeployRun(
                    "krakentv", "CI", "Add channel guide cache", "main",
                    DeployOutcome.Passed(took = 2.minutes + 8.seconds, ago = 18.minutes + t),
                ),
            )
        }.take(MAX_RUNS)
    }

    private companion object {
        /** The mockup opens with the deploy at 3:12, 64% of the way through. */
        val ALREADY_RUNNING = 3.minutes + 12.seconds
        val RUN_LENGTH = 5.minutes
        val CYCLE = 6.minutes
        const val MAX_RUNS = 3
        val COMMITS = listOf("Bump pgx to v5.6", "Tune autovacuum on jobs table", "Cache route manifest")
    }
}
