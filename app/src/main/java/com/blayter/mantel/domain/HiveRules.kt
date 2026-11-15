package com.blayter.mantel.domain

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration

enum class HiveSort(val label: String) { Busiest("Busiest"), Memory("Memory"), Name("Name") }

/** Missing runners are pinned first and ended containers last; [sort] orders the rest. */
fun sortForHive(containers: List<ContainerStatus>, sort: HiveSort): List<ContainerStatus> {
    val pin = compareBy<ContainerStatus> { if (it.isMissing) 0 else if (it.isEnded) 2 else 1 }
    return containers.sortedWith(
        when (sort) {
            HiveSort.Busiest -> pin.thenByDescending { if (it.isLive) it.averageCores else -1.0 }
            HiveSort.Memory -> pin.thenByDescending { if (it.isLive) it.memoryMb else -1 }
            HiveSort.Name -> pin.thenBy { it.name }
        },
    )
}

/** Cores at which a cell is fully lit. From DESIGN.md: glow height = sqrt(cores / 12). */
private const val GLOW_FULL_CORES = 12.0

/** Glow height as a 0..1 fraction. The square root keeps idle containers visibly lit. */
fun glowLevel(cores: Double): Float = sqrt(cores.coerceAtLeast(0.0) / GLOW_FULL_CORES).coerceIn(0.0, 1.0).toFloat()

/** "20 s", "4 min", "2 h", "6 d". */
fun formatAge(d: Duration): String = when {
    d.inWholeSeconds < 60 -> "${d.inWholeSeconds} s"
    d.inWholeMinutes < 60 -> "${d.inWholeMinutes} min"
    d.inWholeHours < 24 -> "${d.inWholeHours} h"
    else -> "${d.inWholeDays} d"
}

/** Compact age for the tight kind line in a cell: "20s", "4m", "2h", "6d". */
fun formatAgeShort(d: Duration): String = when {
    d.inWholeSeconds < 60 -> "${d.inWholeSeconds}s"
    d.inWholeMinutes < 60 -> "${d.inWholeMinutes}m"
    d.inWholeHours < 24 -> "${d.inWholeHours}h"
    else -> "${d.inWholeDays}d"
}

private fun gb(mb: Int) = String.format(Locale.US, "%.1f", mb / 1024.0)

fun formatCores(cores: Double): String = String.format(Locale.US, "%.1f", cores)

/** "3.6 of 4.0 GB" with a real cap, otherwise "2.8 GB" or "310 MB". */
fun formatMemory(c: ContainerStatus): String = when {
    c.memoryCapMb != null -> "${gb(c.memoryMb)} of ${gb(c.memoryCapMb)} GB"
    c.memoryMb >= 1024 -> "${gb(c.memoryMb)} GB"
    else -> "${c.memoryMb} MB"
}

enum class Severity { Busy, Wrong }

/** A plain-sentence warning: "[who] [what]". */
data class Warning(val who: String, val what: String, val severity: Severity)

const val MAX_WARNINGS = 3

fun warningsFor(containers: List<ContainerStatus>, memoryNearCapPercent: Int): List<Warning> = buildList {
    for (c in containers) {
        val presence = c.presence
        if (presence is Presence.Missing) {
            add(Warning(c.shortName, "should be running but has been gone for ${formatAge(presence.goneFor)}", Severity.Wrong))
        }
        if (c.isSustained) {
            add(Warning(c.shortName, "has held about ${c.cores.roundToInt()} cores for ${formatAge(c.sustainedFor!!)}", Severity.Busy))
        }
        if (c.isMemoryNearCap(memoryNearCapPercent)) {
            val capGb = c.memoryCapMb!! / 1024.0
            val cap = if (capGb % 1.0 == 0.0) capGb.toInt().toString() else gb(c.memoryCapMb)
            add(Warning(c.shortName, "is at ${c.memoryPercentOfCap!!.roundToInt()}% of its $cap GB memory cap", Severity.Wrong))
        }
    }
}.take(MAX_WARNINGS)
