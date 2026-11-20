package com.blayter.mantel.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.DeployStatus
import com.blayter.mantel.domain.MAX_WARNINGS
import com.blayter.mantel.domain.deployBoard
import com.blayter.mantel.ui.hive.HiveSection
import com.blayter.mantel.ui.panel.CountCard
import com.blayter.mantel.ui.panel.DeploysCard
import com.blayter.mantel.ui.panel.HostCharts
import com.blayter.mantel.ui.panel.ModeChangeMillis
import com.blayter.mantel.ui.theme.HiveTokens

internal val HostChartsHeight = 196.dp

/** The host cards without the line under each number, while deploys are active. */
private val HostChartsCompactHeight = 168.dp

/** The hive is drawn this much smaller when the machine shelf sits under it. */
private const val HIVE_SCALE_WITH_SHELF = 0.975f
private const val WARNINGS_WITH_SHELF = 2

/**
 * The main Hive screen. Fully readable with zero interaction. When the CI box
 * stops answering it keeps the last reading on screen, visibly stale.
 *
 * While deploys are active (see [deployBoard]) the count and host cards shrink
 * to their charts and the deploys card takes the room; the change is animated.
 *
 * With several machines, [shelf] is the row of machine plaques under both
 * columns and [machineKey] says which machine [state] belongs to. When the
 * key changes, the parts that belong to a machine (the hive, its warnings, the
 * count and host cards) cross-fade; the deploys card does not move.
 *
 * @param dataNote a short label beside the weekday while some of the data is not real.
 * @param repoNames the repositories the deploys card watches, for its "no recent runs" sentence.
 */
@Composable
fun DashboardScreen(
    state: DashboardState,
    dataNote: String? = null,
    repoNames: List<String> = emptyList(),
    machineKey: Int = 0,
    shelf: (@Composable () -> Unit)? = null,
) {
    val several = shelf != null
    val board = remember(state.deploys) { deployBoard(state.deploys) }
    // A token problem shows a notice, not runs, so it never takes the extra room.
    val active = board.active && state.deployStatus == DeployStatus.Live
    val hostChartsHeight by animateDpAsState(
        if (active) HostChartsCompactHeight else HostChartsHeight, tween(ModeChangeMillis), label = "host charts height",
    )
    // The machine that is fading out keeps showing its own last state.
    val seen = remember { HashMap<Int, DashboardState>() }
    seen[machineKey] = state
    // With several machines the header names none: each name is on its plaque, and
    // threads and memory are already on the CPU and memory cards.
    val subtitle = if (several) null else "${state.host.name} · ${state.host.threads} threads · ${state.host.memoryTotalGb} GB"
    HiveFrame(subtitle = subtitle, dayNote = dataNote) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            Crossfade(machineKey, Modifier.width(HiveTokens.Screen.hiveColumnWidth), tween(ModeChangeMillis), label = "machine hive") { key ->
                val machine = seen[key] ?: state
                HiveSection(
                    machine.containers, machine.host.name, machine.stale,
                    hiveScale = if (several) HIVE_SCALE_WITH_SHELF else 1f,
                    maxWarnings = if (several) WARNINGS_WITH_SHELF else MAX_WARNINGS,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                Crossfade(machineKey, animationSpec = tween(ModeChangeMillis), label = "machine cards") { key ->
                    val machine = seen[key] ?: state
                    val stale = machine.stale != null
                    Column(verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                        CountCard(machine.containers, machine.countTrend, stale = stale, compact = active)
                        HostCharts(machine.host, machine.hostTrends, Modifier.height(hostChartsHeight), stale = stale, compact = active)
                    }
                }
                // Deploys come from GitHub, a separate source, so they stay live and do not change with the machine.
                DeploysCard(
                    state.deploys, Modifier.weight(1f), status = state.deployStatus, repoNames = repoNames, board = board,
                    reachDown = !several,
                )
            }
        }
        shelf?.invoke()
    }
}
