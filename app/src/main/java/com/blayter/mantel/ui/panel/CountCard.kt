package com.blayter.mantel.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.DashboardRepository.Companion.TREND_SAMPLES
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.CountSample
import com.blayter.mantel.ui.hive.color
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved mockup that are not in tokens.json.
private val ContentHeight = 216.dp
private val KindLineGap = 2.dp
private val DotSize = 18.dp
private val TrendRadius = 10.dp
private val TrendLabelLift = (-6).dp
private const val COUNT_LINE_HEIGHT_EM = 0.85f

/** Count of containers the trend's vertical scale tops out at. */
private const val TREND_SCALE = 14f

/** The big container count, the breakdown by kind, and the 30-minute stacked trend. */
@Composable
fun CountCard(
    containers: List<ContainerStatus>,
    trend: List<CountSample>,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
) {
    val live = containers.filter { it.isLive }
    fun count(kind: ContainerKind) = live.count { it.kind == kind }
    val missingRunners = containers.count { it.isMissing && it.kind == ContainerKind.Runner }
    HiveCard(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = HiveTokens.Card.paddingX, vertical = HiveTokens.Card.paddingY)
                .height(ContentHeight),
            verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HiveTokens.Card.paddingX),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    live.size.toString(),
                    style = HiveType.displayXl.copy(lineHeight = COUNT_LINE_HEIGHT_EM.em),
                    color = if (stale) HiveTokens.Color.textDim else HiveTokens.Color.textBright,
                )
                Column(verticalArrangement = Arrangement.spacedBy(KindLineGap)) {
                    Text(if (stale) "containers at the last reading" else "containers right now", style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
                    KindLine(ContainerKind.Runner, count(ContainerKind.Runner), "runners", if (missingRunners > 0) "· $missingRunners missing" else "")
                    KindLine(ContainerKind.Database, count(ContainerKind.Database), "job databases")
                    KindLine(ContainerKind.Job, count(ContainerKind.Job), "job containers")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                CountTrend(trend, Modifier.fillMaxSize())
                Text(
                    "last 30 min",
                    Modifier.offset(y = TrendLabelLift),
                    style = HiveType.caption,
                    color = HiveTokens.Color.textMuted,
                )
            }
        }
    }
}

/** The count card before the first reading: a dash where the number will be. */
@Composable
fun CountCardPlaceholder(modifier: Modifier = Modifier) {
    HiveCard(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = HiveTokens.Card.paddingX, vertical = HiveTokens.Card.paddingY)
                .height(ContentHeight),
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Card.paddingX),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("—", style = HiveType.displayXl.copy(lineHeight = COUNT_LINE_HEIGHT_EM.em), color = HiveTokens.Color.textDim)
            Text("containers right now", style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
        }
    }
}

@Composable
private fun KindLine(kind: ContainerKind, n: Int, label: String, note: String = "") {
    Row(horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s10), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(DotSize).background(kind.color(), CircleShape))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(n.toString()) }
                append(" $label")
                if (note.isNotEmpty()) {
                    withStyle(SpanStyle(color = HiveTokens.Color.Status.trouble, fontWeight = FontWeight.Bold)) { append(" $note") }
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Stacked step chart: runners at the bottom, then job databases, then job containers. */
@Composable
private fun CountTrend(trend: List<CountSample>, modifier: Modifier = Modifier) {
    val runner = ContainerKind.Runner.color()
    val database = ContainerKind.Database.color()
    val job = ContainerKind.Job.color()
    Canvas(
        modifier
            .clip(RoundedCornerShape(TrendRadius))
            .semantics { contentDescription = "Container count by kind, last 30 minutes" },
    ) {
        if (trend.isEmpty()) return@Canvas
        // The chart is always the full window wide, newest at the right edge,
        // so a display that has just started shows a short strip, not a full one.
        val stepX = size.width / TREND_SAMPLES
        val first = TREND_SAMPLES - trend.size
        val unit = size.height / TREND_SCALE
        fun band(i: Int, from: Int, to: Int, color: Color) {
            if (to > from) {
                // One extra pixel of width hides hairline seams between steps.
                drawRect(color, Offset((first + i) * stepX, size.height - to * unit), Size(stepX + 1f, (to - from) * unit))
            }
        }
        trend.forEachIndexed { i, s ->
            band(i, 0, s.runners, runner)
            band(i, s.runners, s.runners + s.databases, database)
            band(i, s.runners + s.databases, s.runners + s.databases + s.jobs, job)
        }
    }
}
