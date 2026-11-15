package com.blayter.mantel.domain

import kotlin.time.Duration

/** Alert thresholds. The values in tokens.json are proposals, not yet confirmed by the owner. */
data class Thresholds(
    val memoryNearCapPercent: Int,
    val sustainedCores: Double,
    val sustainedFor: Duration,
    val endedLinger: Duration,
)

/** Runners that should always be present (DESIGN.md, "Data rules"). One absent is "missing". */
val ExpectedRunners: List<String> = listOf(
    "rxflow-runner-runner-1",
    "rxflow-runner-runner-2",
    "rxflow-runner-runner-3",
    "reach-runner-runner-1",
    "reach-runner-runner-2",
    "krakentv-runner-runner-1",
)

private val RunnerName = Regex("""^(.+)-runner-runner-(\d+)$""")
private val HashSuffix = Regex("""-[0-9a-f]{4,}$""")

/** `*-runner-runner-N` is a runner, a `postgres` image is a job database, anything else is a job. */
fun kindOf(name: String, image: String): ContainerKind = when {
    RunnerName.matches(name) -> ContainerKind.Runner
    image.contains("postgres", ignoreCase = true) -> ContainerKind.Database
    else -> ContainerKind.Job
}

/**
 * Label for a hex cell. Runners become "rxflow 2". For the rest, the project
 * prefix and a trailing hash are dropped ("rxflow-postgres-e2e" -> "postgres-e2e",
 * "redis-test-7f3a" -> "redis-test"). This matches the mockup's sample names;
 * the rule itself is an assumption to confirm against the real container list.
 */
fun shortNameOf(name: String, expectedRunners: List<String> = ExpectedRunners): String {
    RunnerName.matchEntire(name)?.let { return "${it.groupValues[1]} ${it.groupValues[2]}" }
    val projects = expectedRunners.mapNotNull { RunnerName.matchEntire(it)?.groupValues?.get(1) }.distinct()
    val withoutProject = projects.firstOrNull { name.startsWith("$it-") }?.let { name.removePrefix("$it-") } ?: name
    return withoutProject.replace(HashSuffix, "")
}
