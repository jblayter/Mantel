package com.blayter.mantel.domain

import kotlin.time.Duration

/** Container kinds, per DESIGN.md "Data rules". */
enum class ContainerKind { Runner, Database, Job }

sealed interface Presence {
    /** Running now. */
    data class Live(val uptime: Duration) : Presence

    /** An expected runner that is absent from the container list. */
    data class Missing(val goneFor: Duration) : Presence

    /** Recently exited; lingers briefly so cells do not jump. */
    data class Ended(val ago: Duration) : Presence
}

/** One hive cell's worth of data. */
data class ContainerStatus(
    val name: String,
    val shortName: String,
    val kind: ContainerKind,
    val presence: Presence,
    /** Cores in use right now (`cpu_percent / 100`). */
    val cores: Double = 0.0,
    /** Short moving average of [cores]; the sort key, so cells do not reshuffle on every sample. */
    val averageCores: Double = cores,
    val memoryMb: Int = 0,
    /** Null when the limit equals the host total, which means "no cap". */
    val memoryCapMb: Int? = null,
    /** Set while CPU has stayed above the sustained threshold. */
    val sustainedFor: Duration? = null,
) {
    val isLive get() = presence is Presence.Live
    val isMissing get() = presence is Presence.Missing
    val isEnded get() = presence is Presence.Ended
    val isSustained get() = isLive && sustainedFor != null

    /** Memory as a percentage of the cap, or null when there is no cap. */
    val memoryPercentOfCap: Double? get() = memoryCapMb?.let { memoryMb * 100.0 / it }

    fun isMemoryNearCap(thresholdPercent: Int) =
        isLive && (memoryPercentOfCap ?: 0.0) >= thresholdPercent
}
