package com.blayter.mantel.data.mock

import com.blayter.mantel.data.HostSnapshot
import com.blayter.mantel.data.HostSource
import com.blayter.mantel.data.RawContainer
import com.blayter.mantel.domain.HostInfo
import java.io.IOException
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * A small sample machine for working on the several-machines screen. Its
 * containers are fixed and their CPU wobbles a little.
 *
 * @param answersFor after this long the machine stops answering, or null for never.
 */
class MockMachineSource(
    private val host: HostInfo,
    private val containers: List<RawContainer>,
    private val elapsed: () -> Duration,
    private val answersFor: Duration? = null,
) : HostSource {
    private var tick = 0

    override suspend fun snapshot(): HostSnapshot {
        if (answersFor != null && elapsed() > answersFor) throw IOException("${host.name} is not answering")
        tick++
        val now = containers.map { it.copy(cpuPercent = it.cpuPercent * (1 + 0.2 * sin(tick * 0.6 + it.name.length))) }
        val cores = now.sumOf { it.cpuPercent } / 100.0
        val usedMb = now.sumOf { it.memoryMb } + 900
        return HostSnapshot(
            host = host,
            memoryTotalMb = host.memoryTotalGb * 1024,
            containers = now,
            cpuPercent = (cores / host.threads * 100).toFloat(),
            memoryUsedGb = usedMb / 1024f,
            load1 = cores.toFloat() * 1.1f,
            load5 = cores.toFloat(),
            load15 = cores.toFloat() * 0.9f,
        )
    }
}

/** The sample machines of the several-machines mockups, after the main one. */
object MockMachines {
    private fun container(name: String, image: String, cpu: Double, mb: Int, limitMb: Int, up: Duration) =
        RawContainer(name, image, cpu, mb, limitMb, up)

    /** Has one coral warning: backup-db is close to its 2 GB memory cap. */
    fun atticNas(elapsed: () -> Duration): HostSource {
        val host = HostInfo("attic-nas", threads = 8, memoryTotalGb = 15)
        val noCap = host.memoryTotalGb * 1024
        return MockMachineSource(
            host,
            listOf(
                container("plex", "plexinc/pms-docker", 62.0, 1240, noCap, 12.days),
                container("backup-db", "postgres:16", 28.0, 1884, 2048, 3.days),
                container("samba", "dperson/samba", 4.0, 96, noCap, 40.days),
                container("pihole", "pihole/pihole", 6.0, 210, noCap, 40.days),
                container("homeassistant", "homeassistant/home-assistant", 35.0, 780, noCap, 9.hours),
            ),
            elapsed,
        )
    }

    /** Answers a few times, then goes quiet. */
    fun printerPi(elapsed: () -> Duration): HostSource {
        val host = HostInfo("printer-pi", threads = 4, memoryTotalGb = 4)
        return MockMachineSource(
            host,
            listOf(container("octoprint", "octoprint/octoprint", 22.0, 310, host.memoryTotalGb * 1024, 2.days)),
            elapsed,
            answersFor = 20.seconds,
        )
    }

    /** [count] machines after the main one, cycling through the samples with numbered names past two. */
    fun extra(count: Int, elapsed: () -> Duration): List<Pair<HostSource, String>> = List(count) { i ->
        when (i) {
            0 -> atticNas(elapsed) to "192.168.1.61"
            1 -> printerPi(elapsed) to "192.168.1.77"
            else -> {
                val host = HostInfo("node-${i - 1}", threads = 4, memoryTotalGb = 8)
                MockMachineSource(
                    host,
                    listOf(container("worker", "busybox", 10.0 * i, 120, host.memoryTotalGb * 1024, i.hours)),
                    elapsed,
                ) to "192.168.1.${80 + i}"
            }
        }
    }
}
