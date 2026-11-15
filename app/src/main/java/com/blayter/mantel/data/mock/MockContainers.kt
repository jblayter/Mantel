package com.blayter.mantel.data.mock

import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.ContainerKind.Database
import com.blayter.mantel.domain.ContainerKind.Job
import com.blayter.mantel.domain.ContainerKind.Runner
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.Presence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The twelve sample containers from the approved mockup (Hive.dc.html). */
object MockContainers {
    private fun live(
        name: String, short: String, kind: ContainerKind, uptime: Duration,
        cores: Double, memoryMb: Int, capMb: Int? = null, sustainedFor: Duration? = null,
    ) = ContainerStatus(name, short, kind, Presence.Live(uptime), cores, cores, memoryMb, capMb, sustainedFor)

    val sample: List<ContainerStatus> = listOf(
        live("rxflow-runner-runner-1", "rxflow 1", Runner, 4.minutes, 3.2, 2870),
        live("rxflow-runner-runner-2", "rxflow 2", Runner, 14.minutes, 11.8, 6240, sustainedFor = 14.minutes),
        live("rxflow-runner-runner-3", "rxflow 3", Runner, 1.minutes, 0.1, 310),
        live("reach-runner-runner-1", "reach 1", Runner, 9.minutes, 1.9, 1430),
        ContainerStatus("reach-runner-runner-2", "reach 2", Runner, Presence.Missing(3.minutes)),
        live("krakentv-runner-runner-1", "krakentv 1", Runner, 2.hours, 0.8, 3690, capMb = 4096),
        live("rxflow-postgres-e2e", "postgres-e2e", Database, 6.days, 0.6, 412),
        live("magical_noyce", "magical_noyce", Database, 13.minutes, 1.4, 286),
        live("vigilant_hopper", "vigilant_hopper", Database, 4.minutes, 0.3, 198),
        live("rxflow-e2e-web-1", "e2e-web-1", Job, 3.minutes, 2.2, 1120),
        live("redis-test-7f3a", "redis-test", Job, 3.minutes, 0.1, 24),
        ContainerStatus("quirky_banach", "quirky_banach", Database, Presence.Ended(20.seconds)),
    )
}
