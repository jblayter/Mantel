package com.blayter.mantel.data

import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.CountSample
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.ExpectedRunners
import com.blayter.mantel.domain.HostTrends
import com.blayter.mantel.domain.Presence
import com.blayter.mantel.domain.Thresholds
import com.blayter.mantel.domain.kindOf
import com.blayter.mantel.domain.shortNameOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** History to start from, so the mock opens on the mockup's sample screen. Empty for real sources. */
data class RepositorySeed(
    val countTrend: List<CountSample> = emptyList(),
    val cpuPercent: List<Float> = emptyList(),
    val memoryGb: List<Float> = emptyList(),
    val load: List<Float> = emptyList(),
    /** Container name to how long it has already been above the sustained-CPU threshold. */
    val sustainedFor: Map<String, Duration> = emptyMap(),
    /** Container name to its recent average cores, so a seeded busy spell survives the first noisy sample. */
    val averageCores: Map<String, Double> = emptyMap(),
    /** Expected runner name to how long it has already been gone. */
    val missingFor: Map<String, Duration> = emptyMap(),
    /** Containers that ended shortly before start, with how long ago. */
    val ended: List<Pair<ContainerStatus, Duration>> = emptyList(),
)

/**
 * Turns raw polls into the screen's [DashboardState]. Glances keeps no
 * history, so everything that needs time lives here: moving averages, how long
 * a container has been busy, missing or ended, and the trend series. Sources
 * are the only thing that changes between mock and real data.
 *
 * Not thread-safe: run [start] on a single-threaded scope (the main dispatcher).
 */
class DashboardRepository(
    private val hostSource: HostSource,
    private val deploySource: DeploySource,
    private val thresholds: Thresholds,
    /** Monotonic time since some fixed point. */
    private val now: () -> Duration,
    seed: RepositorySeed = RepositorySeed(),
    private val expectedRunners: List<String> = ExpectedRunners,
    private val hostPollEvery: Duration = 3.seconds,
    private val deployPollEvery: Duration = 30.seconds,
) {
    private class EndedEntry(val status: ContainerStatus, val at: Duration)

    private val _state = MutableStateFlow<DashboardState?>(null)

    /** Null until the first host poll has arrived. */
    val state: StateFlow<DashboardState?> = _state.asStateFlow()

    private var deploys: List<DeployRun> = emptyList()
    private var lastLive: Map<String, ContainerStatus> = emptyMap()
    private val averages = HashMap<String, ArrayDeque<Double>>()
    private val sustainedSince = HashMap<String, Duration>()
    private val missingSince = HashMap<String, Duration>()
    private val ended = LinkedHashMap<String, EndedEntry>()

    private val countTrend = ArrayDeque(seed.countTrend)
    private val cpuTrend = ArrayDeque(seed.cpuPercent)
    private val memoryTrend = ArrayDeque(seed.memoryGb)
    private val loadTrend = ArrayDeque(seed.load)
    private var lastCountSampleAt: Duration? = null

    init {
        val t = now()
        seed.sustainedFor.forEach { (name, d) -> sustainedSince[name] = t - d }
        seed.averageCores.forEach { (name, cores) -> averages[name] = ArrayDeque(List(AVERAGE_SAMPLES) { cores }) }
        seed.missingFor.forEach { (name, d) -> missingSince[name] = t - d }
        seed.ended.forEach { (status, ago) -> ended[status.name] = EndedEntry(status, t - ago) }
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                onDeploys(deploySource.recentRuns())
                delay(deployPollEvery)
            }
        }
        scope.launch {
            while (isActive) {
                onHostSnapshot(hostSource.snapshot())
                delay(hostPollEvery)
            }
        }
    }

    internal fun onDeploys(runs: List<DeployRun>) {
        deploys = runs
        _state.value = _state.value?.copy(deploys = runs)
    }

    internal fun onHostSnapshot(snapshot: HostSnapshot) {
        val t = now()
        val live = snapshot.containers.map { raw -> liveStatus(raw, snapshot.memoryTotalMb, t) }
        val liveNames = live.mapTo(HashSet()) { it.name }

        // A container that was here last poll and is gone now has ended. An
        // expected runner that is gone is "missing" instead, and never "ended".
        for ((name, previous) in lastLive) {
            if (name !in liveNames && name !in expectedRunners) ended[name] = EndedEntry(previous, t)
        }
        ended.entries.removeAll { (name, entry) -> name in liveNames || t - entry.at > thresholds.endedLinger }
        averages.keys.retainAll(liveNames)
        sustainedSince.keys.retainAll(liveNames)
        lastLive = live.associateBy { it.name }

        val missing = expectedRunners.filter { it !in liveNames }.map { name ->
            val since = missingSince.getOrPut(name) { t }
            ContainerStatus(name, shortNameOf(name, expectedRunners), ContainerKind.Runner, Presence.Missing(t - since))
        }
        missingSince.keys.retainAll { it !in liveNames }

        val endedNow = ended.values.map {
            it.status.copy(presence = Presence.Ended(t - it.at), cores = 0.0, averageCores = 0.0, sustainedFor = null)
        }

        cpuTrend.push(snapshot.cpuPercent)
        memoryTrend.push(snapshot.memoryUsedGb)
        loadTrend.push(snapshot.load1)
        val lastCount = lastCountSampleAt
        if (lastCount == null || t - lastCount >= COUNT_SAMPLE_EVERY) {
            // The seeded trend already ends "now", so the first poll only starts the clock.
            if (lastCount != null || countTrend.isEmpty()) {
                countTrend.push(
                    CountSample(
                        runners = live.count { it.kind == ContainerKind.Runner },
                        databases = live.count { it.kind == ContainerKind.Database },
                        jobs = live.count { it.kind == ContainerKind.Job },
                    ),
                )
            }
            lastCountSampleAt = t
        }

        _state.value = DashboardState(
            host = snapshot.host,
            containers = live + missing + endedNow,
            countTrend = countTrend.toList(),
            hostTrends = HostTrends(
                cpuTrend.toList(), memoryTrend.toList(), loadTrend.toList(), snapshot.load5, snapshot.load15,
            ),
            deploys = deploys,
        )
    }

    private fun liveStatus(raw: RawContainer, hostMemoryMb: Int, t: Duration): ContainerStatus {
        val cores = raw.cpuPercent / 100.0
        val recent = averages.getOrPut(raw.name) { ArrayDeque() }
        recent.addLast(cores)
        if (recent.size > AVERAGE_SAMPLES) recent.removeFirst()
        val average = recent.average()

        if (average >= thresholds.sustainedCores) sustainedSince.getOrPut(raw.name) { t } else sustainedSince.remove(raw.name)
        val busyFor = sustainedSince[raw.name]?.let { t - it }

        return ContainerStatus(
            name = raw.name,
            shortName = shortNameOf(raw.name, expectedRunners),
            kind = kindOf(raw.name, raw.image),
            presence = Presence.Live(raw.uptime),
            cores = cores,
            averageCores = average,
            memoryMb = raw.memoryMb,
            // A limit equal to the host total means "no cap".
            memoryCapMb = raw.memoryLimitMb.takeIf { it in 1 until hostMemoryMb },
            sustainedFor = busyFor?.takeIf { it >= thresholds.sustainedFor },
        )
    }

    private fun <T> ArrayDeque<T>.push(value: T) {
        addLast(value)
        while (size > TREND_SAMPLES) removeFirst()
    }

    companion object {
        /** Samples kept per trend series. */
        const val TREND_SAMPLES = 60

        /** Samples in the CPU moving average that orders the hive. */
        const val AVERAGE_SAMPLES = 10

        /** 60 samples at this spacing is the count card's "last 30 min". */
        val COUNT_SAMPLE_EVERY = 30.seconds
    }
}
