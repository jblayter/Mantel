package com.blayter.mantel.domain

import kotlin.time.Duration

/** How one machine is doing, as its plaque on the shelf says it. */
sealed interface MachineCondition {
    data class Well(val containers: Int) : MachineCondition

    /** [sentence] is the machine's worst warning, a coral one before an amber one. */
    data class Warning(val sentence: String, val severity: Severity, val count: Int) : MachineCondition

    /** Stopped answering, or has never answered. [sentence] fits after the machine's name. */
    data class NotAnswering(val sentence: String) : MachineCondition
}

data class MachineSummary(val name: String, val condition: MachineCondition) {
    val isAnswering get() = condition !is MachineCondition.NotAnswering
    val needsAttention get() = condition !is MachineCondition.Well
}

/**
 * @param state the machine's latest dashboard state, or null if it has never answered.
 * @param address shown as the name until the machine has said what it is called.
 */
fun machineSummary(state: DashboardState?, address: String, memoryNearCapPercent: Int): MachineSummary {
    if (state == null) return MachineSummary(address, MachineCondition.NotAnswering("has not answered yet"))
    val stale = state.stale
    if (stale != null) {
        return MachineSummary(state.host.name, MachineCondition.NotAnswering("has not answered for ${formatAge(stale.unansweredFor)}"))
    }
    val warnings = allWarningsFor(state.containers, memoryNearCapPercent)
    val worst = warnings.firstOrNull { it.severity == Severity.Wrong } ?: warnings.firstOrNull()
    val condition = if (worst == null) {
        MachineCondition.Well(state.containers.count { it.isLive })
    } else {
        MachineCondition.Warning("${worst.who} ${worst.brief}", worst.severity, warnings.size)
    }
    return MachineSummary(state.host.name, condition)
}

/** The sentence on a plaque. */
fun plaqueSentence(summary: MachineSummary, onScreen: Boolean, paused: Boolean): String {
    val condition = summary.condition
    if (onScreen) {
        return "On screen · " + when {
            paused -> "paused"
            condition is MachineCondition.Warning -> if (condition.count == 1) "1 warning" else "${condition.count} warnings"
            condition is MachineCondition.NotAnswering -> "not answering"
            else -> "all well"
        }
    }
    return when (condition) {
        is MachineCondition.Well -> "All well · " + when (condition.containers) {
            0 -> "no containers"
            1 -> "1 container"
            else -> "${condition.containers} containers"
        }
        is MachineCondition.Warning -> condition.sentence
        is MachineCondition.NotAnswering -> condition.sentence
    }
}

/**
 * How long a machine stays on screen. One with a coral warning gets the
 * longer turn, so there is time to read what is wrong.
 */
fun turnLength(summary: MachineSummary, normal: Duration, trouble: Duration): Duration {
    val condition = summary.condition
    return if (condition is MachineCondition.Warning && condition.severity == Severity.Wrong) trouble else normal
}

/**
 * Whose turn is next. A machine that is not answering has nothing new to
 * show, so the turns pass over it; it stays visible on the shelf. With nobody
 * else answering, the current machine stays.
 */
fun nextTurn(current: Int, summaries: List<MachineSummary>): Int {
    for (step in 1 until summaries.size) {
        val i = (current + step) % summaries.size
        if (summaries[i].isAnswering) return i
    }
    return current
}

/** One step with the remote: any machine that has ever answered, in order, wrapping around. */
fun stepMachine(current: Int, hasReading: List<Boolean>, step: Int): Int {
    val n = hasReading.size
    for (k in 1 until n) {
        val i = ((current + step * k) % n + n) % n
        if (hasReading[i]) return i
    }
    return current
}

sealed interface Plaque {
    data class Machine(val index: Int) : Plaque

    /** The machines that did not get a plaque of their own. */
    data class More(val count: Int, val needAttention: Int) : Plaque
}

/** Plaques the shelf has room for. */
const val MAX_PLAQUES = 4

/**
 * Up to [MAX_PLAQUES] machines each get a plaque, in their own order. With
 * more, the shelf shows the machine on screen, then machines that need
 * attention, then one plaque for the rest.
 */
fun shelfPlaques(summaries: List<MachineSummary>, onScreen: Int): List<Plaque> {
    if (summaries.size <= MAX_PLAQUES) return summaries.indices.map { Plaque.Machine(it) }
    val others = summaries.indices.filter { it != onScreen }
    val shown = others.filter { summaries[it].needsAttention }.take(MAX_PLAQUES - 2)
    val rest = others - shown.toSet()
    return listOf(Plaque.Machine(onScreen)) + shown.map { Plaque.Machine(it) } +
        Plaque.More(rest.size, rest.count { summaries[it].needsAttention })
}
