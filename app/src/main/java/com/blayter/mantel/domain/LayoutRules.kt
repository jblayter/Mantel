package com.blayter.mantel.domain

/**
 * Pure rules shared by the NOC and Neon layouts, which show everything at
 * once instead of paging, and name each container's state in a word.
 */

/** Host CPU at or above this is trouble (the number turns coral). Unconfirmed, from the mockup. */
const val HOST_CPU_TROUBLE_PERCENT = 90f

/** A container's state as one word, worst first: what a status column says. */
enum class ContainerFlag(val word: String, val severity: Severity?) {
    Missing("MISSING", Severity.Wrong),
    NearCap("NEAR CAP", Severity.Wrong),
    Busy("BUSY", Severity.Busy),
    Ended("ENDED", null),
    Ok("OK", null),
}

fun flagOf(c: ContainerStatus, memoryNearCapPercent: Int): ContainerFlag = when {
    c.isMissing -> ContainerFlag.Missing
    c.isEnded -> ContainerFlag.Ended
    c.isMemoryNearCap(memoryNearCapPercent) -> ContainerFlag.NearCap
    c.isSustained -> ContainerFlag.Busy
    else -> ContainerFlag.Ok
}

/** How many containers are in trouble, busy, and fine. Ended ones are not counted. */
data class SeverityCounts(val wrong: Int, val busy: Int, val ok: Int)

fun severityCounts(containers: List<ContainerStatus>, memoryNearCapPercent: Int): SeverityCounts {
    val flags = containers.map { flagOf(it, memoryNearCapPercent) }
    return SeverityCounts(
        wrong = flags.count { it.severity == Severity.Wrong },
        busy = flags.count { it.severity == Severity.Busy },
        ok = flags.count { it == ContainerFlag.Ok },
    )
}

/**
 * Every run on one list, for layouts that show all repositories at once.
 * While deploys are active: every active run, still going first. Otherwise:
 * each repository's latest run, then each one's second, and so on, so a
 * list cut short still covers as many repositories as it can.
 */
fun deployRowsAtOnce(board: DeployBoard): List<DeployRun> {
    if (board.active) {
        val all = board.pages.flatMap { it.runs }
        return all.filter { it.outcome is DeployOutcome.InProgress } + all.filter { it.outcome !is DeployOutcome.InProgress }
    }
    val depth = board.pages.maxOfOrNull { it.runs.size } ?: 0
    return (0 until depth).flatMap { i -> board.pages.mapNotNull { it.runs.getOrNull(i) } }
}

/** How many of [total] lit segments [level] (0..1) lights, at least one for anything above zero. */
fun litSegments(level: Float, total: Int): Int = when {
    level <= 0f -> 0
    else -> (level * total).let { kotlin.math.round(it).toInt() }.coerceIn(1, total)
}
