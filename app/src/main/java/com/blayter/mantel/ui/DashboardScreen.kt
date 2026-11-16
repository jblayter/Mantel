package com.blayter.mantel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.ui.hive.HiveSection
import com.blayter.mantel.ui.panel.CountCard
import com.blayter.mantel.ui.panel.DeploysCard
import com.blayter.mantel.ui.panel.HostCharts
import com.blayter.mantel.ui.theme.HiveTokens

internal val HostChartsHeight = 196.dp

/**
 * The main Hive screen. Fully readable with zero interaction. When the CI box
 * stops answering it keeps the last reading on screen, visibly stale.
 *
 * @param dataNote a short label beside the weekday while some of the data is not real.
 * @param repoNames the repositories the deploys card watches, for its "no recent runs" sentence.
 */
@Composable
fun DashboardScreen(state: DashboardState, dataNote: String? = null, repoNames: List<String> = emptyList()) {
    val host = state.host
    val stale = state.stale != null
    HiveFrame(subtitle = "${host.name} · ${host.threads} threads · ${host.memoryTotalGb} GB", dayNote = dataNote) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            HiveSection(state.containers, host.name, state.stale, Modifier.width(HiveTokens.Screen.hiveColumnWidth))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                CountCard(state.containers, state.countTrend, stale = stale)
                HostCharts(host, state.hostTrends, Modifier.height(HostChartsHeight), stale = stale)
                // Deploys come from GitHub, a separate source, so they stay live.
                DeploysCard(state.deploys, Modifier.weight(1f), status = state.deployStatus, repoNames = repoNames)
            }
        }
    }
}
