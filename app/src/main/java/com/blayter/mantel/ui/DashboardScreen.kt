package com.blayter.mantel.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.Text
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.HostInfo
import com.blayter.mantel.ui.panel.CountCard
import com.blayter.mantel.ui.panel.DeploysCard
import com.blayter.mantel.ui.panel.HostCharts
import androidx.compose.ui.unit.dp
import com.blayter.mantel.ui.hive.HiveSection
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Gaps between baseline-aligned header items, from the approved mockup.
private val HeaderLeftGap = HiveTokens.Space.s20
private val HeaderRightGap = HiveTokens.Space.s24
private val HostChartsHeight = 196.dp

/** The main Hive screen. Fully readable with zero interaction. */
@Composable
fun DashboardScreen(state: DashboardState) {
    val drift by rememberInfiniteTransition(label = "burn-in").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(HiveTokens.Motion.driftSeconds * 1000, easing = EaseInOut),
            RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = HiveTokens.Screen.overscanX, vertical = HiveTokens.Screen.overscanY)
            .graphicsLayer {
                translationX = HiveTokens.Motion.driftPx[0].toPx() * drift
                translationY = HiveTokens.Motion.driftPx[1].toPx() * drift
            },
        verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s24),
    ) {
        Header(state.host)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            HiveSection(state.containers, Modifier.width(HiveTokens.Screen.hiveColumnWidth))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                CountCard(state.containers, state.countTrend)
                HostCharts(state.host, state.hostTrends, Modifier.height(HostChartsHeight))
                DeploysCard(state.deploys, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Header(host: HostInfo) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(HiveTokens.Screen.headerHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(HeaderLeftGap)) {
            Text("Mantel", Modifier.alignByBaseline(), style = HiveType.wordmark, color = HiveTokens.Color.amber)
            Text(
                "${host.name} · ${host.threads} threads · ${host.memoryTotalGb} GB",
                Modifier.alignByBaseline(),
                style = HiveType.lead,
                color = HiveTokens.Color.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HeaderRightGap)) {
            Text(
                "${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} · mock data",
                Modifier.alignByBaseline(),
                color = HiveTokens.Color.textMuted,
            )
            Text(now.format(ClockFormat), Modifier.alignByBaseline(), style = HiveType.clock)
        }
    }
}

private val ClockFormat = DateTimeFormatter.ofPattern("h:mm")
