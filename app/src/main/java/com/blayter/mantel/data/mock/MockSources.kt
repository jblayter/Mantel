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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
 * Stands in for the GitHub Actions client with the mockup's invented runs, on
 * a loop that shows both states of the deploys card. It opens as the mockup
 * does, with the rxflow deploy going out and two other repositories busy in
 * the last hour. The deploy finishes, and for the last part of the loop the
 * mock pretends hours have gone by, so the card falls back to the maintenance
 * window. Then it starts again.
 */
class MockDeploySource(private val elapsed: () -> Duration) : DeploySource {
    override suspend fun recentRuns(): List<DeployRun> {
        val t = elapsed()
        val loop = (t / LOOP).toInt()
        val into = t - LOOP * loop
        val commit = COMMITS[loop % COMMITS.size]
        val running = into + ALREADY_RUNNING
        // In the quiet part every age is this much older.
        val aged = if (into >= QUIET_FROM) QUIET_AGE else Duration.ZERO
        fun passed(repo: String, workflow: String, title: String, took: Duration, ago: Duration) =
            DeployRun(repo, workflow, title, "main", DeployOutcome.Passed(took, ago + into + aged))
        fun failed(repo: String, workflow: String, title: String, step: String, ago: Duration) =
            DeployRun(repo, workflow, title, "main", DeployOutcome.Failed(step, ago + into + aged))

        return listOf(
            if (running < RUN_LENGTH) {
                DeployRun("rxflow", "Deploy production", commit, "main", DeployOutcome.InProgress(running, (running / RUN_LENGTH).toFloat()))
            } else {
                DeployRun("rxflow", "Deploy production", commit, "main", DeployOutcome.Passed(RUN_LENGTH, running - RUN_LENGTH + aged))
            },
            passed("rxflow", "CI", commit, 4.minutes + 41.seconds, 9.minutes),
            passed("rxflow", "CI", "Drop unused job indexes", 4.minutes + 36.seconds, 47.minutes),
            failed("rxflow", "Nightly integration", "Drop unused job indexes", "integration", 9.hours),
            passed("rxflow", "Deploy production", "Drop unused job indexes", 5.minutes + 2.seconds, 1.days),

            failed("reach", "Deploy production", "Fix hydration mismatch", "e2e-tests", 6.minutes),
            passed("reach", "CI", "Fix hydration mismatch", 3.minutes + 15.seconds, 14.minutes),
            passed("reach", "Deploy production", "Lazy-load the map tiles", 6.minutes + 20.seconds, 2.days),
            passed("reach", "CI", "Lazy-load the map tiles", 3.minutes + 9.seconds, 2.days),

            passed("krakentv", "CI", "Add channel guide cache", 2.minutes + 8.seconds, 18.minutes),
            passed("krakentv", "Release", "Version 1.4", 7.minutes + 30.seconds, 3.days),
            passed("krakentv", "CI", "Version 1.4", 2.minutes + 11.seconds, 3.days),
            failed("krakentv", "CI", "Rework the focus ring", "lint", 5.days),
        )
    }

    private companion object {
        /** The mockup opens with the deploy at 3:12, 64% of the way through. */
        val ALREADY_RUNNING = 3.minutes + 12.seconds
        val RUN_LENGTH = 5.minutes

        /** Active until here, quiet from here to the end of the loop. */
        val QUIET_FROM = 3.minutes
        val LOOP = 5.minutes
        val QUIET_AGE = 3.hours
        val COMMITS = listOf("Bump pgx to v5.6", "Tune autovacuum on jobs table", "Cache route manifest")
    }
}
