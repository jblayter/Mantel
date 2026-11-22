package com.blayter.mantel.ui.layout

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.blayter.mantel.domain.ContainerFlag
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.HiveSort
import com.blayter.mantel.domain.Presence
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.domain.allWarningsFor
import com.blayter.mantel.domain.deployBoard
import com.blayter.mantel.domain.deployRowsAtOnce
import com.blayter.mantel.domain.flagOf
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.formatAgeShort
import com.blayter.mantel.domain.formatCores
import com.blayter.mantel.domain.formatTimer
import com.blayter.mantel.domain.glowLevel
import com.blayter.mantel.domain.severityCounts
import com.blayter.mantel.domain.sortForHive
import com.blayter.mantel.ui.ScreenFrame
import com.blayter.mantel.ui.panel.ModeChangeMillis
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import java.time.format.DateTimeFormatter
import java.util.Locale

// Sizes of the NOC layout. Not designed in a mockup; see LayoutParts.
private val TableWidth = 1040.dp
private val PanelRadius = 4.dp
private val PanelBorder = 2.dp
private val PanelPadX = 20.dp
private val PanelPadY = 14.dp
private val TableRow = 50.dp
private val EventRow = 42.dp
private val DeployRow = 50.dp
private val TilesHeight = 232.dp
private val SparkHeight = 46.dp
private val CpuBarWidth = 64.dp
private val CpuBarHeight = 12.dp
private val RunBarWidth = 96.dp
private val TagWidth = 92.dp
private val GapS = 8.dp
private val GapM = 16.dp
private const val CPU_BAR_SEGMENTS = 6
private const val RUN_BAR_SEGMENTS = 8
private const val EVENTS_SHARE = 0.34f

/** With the machine shelf there is less room, and the events of the machine on screen come first. */
private const val EVENTS_SHARE_WITH_SHELF = 0.5f

private val Clock = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ReadingTime = DateTimeFormatter.ofPattern("HH:mm")
private val Day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private val Label = mono(22.sp, bold = true, spacing = 2.sp)
private val Cell = mono(24.sp)
private val CellBold = mono(24.sp, bold = true)
private val Number = mono(48.sp, bold = true)

/**
 * The NOC layout: everything at once, as a network-operations wall shows it.
 * A header with counts by severity and a clock with seconds; a table of
 * every container, worst first; the host numbers as tiles; an event list of
 * every warning; and every deploy run in one table, no carousel.
 */
@Composable
fun NocDashboard(
    state: DashboardState,
    dataNote: String?,
    repoNames: List<String>,
    machineKey: Int,
    shelf: (@Composable () -> Unit)?,
) {
    val nearCap = HiveTokens.ThresholdsProposed.memoryNearCapPercent
    val seen = remember { HashMap<Int, DashboardState>() }
    seen[machineKey] = state
    val board = remember(state.deploys) { deployBoard(state.deploys) }
    val eventsShare = if (shelf != null) EVENTS_SHARE_WITH_SHELF else EVENTS_SHARE
    ScreenFrame(gap = HiveTokens.Space.s20) {
        Header(state, dataNote, nearCap)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            Crossfade(machineKey, Modifier.width(TableWidth).fillMaxHeight(), tween(ModeChangeMillis), label = "noc table") { key ->
                val m = seen[key] ?: state
                ContainerTable(m.containers, nearCap, Modifier.fillMaxSize().alpha(if (m.stale != null) STALE_ALPHA else 1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                Crossfade(machineKey, Modifier.height(TilesHeight), tween(ModeChangeMillis), label = "noc tiles") { key ->
                    val m = seen[key] ?: state
                    MetricTiles(m, Modifier.alpha(if (m.stale != null) STALE_ALPHA else 1f))
                }
                Crossfade(machineKey, Modifier.weight(eventsShare), tween(ModeChangeMillis), label = "noc events") { key ->
                    Events(seen[key] ?: state, nearCap)
                }
                Deploys(state, board.active, deployRowsAtOnce(board), repoNames, Modifier.weight(1f - eventsShare))
            }
        }
        shelf?.invoke()
    }
}

@Composable
private fun Header(state: DashboardState, dataNote: String?, nearCap: Int) {
    val c = HiveTokens.Color
    val now = rememberNow()
    val counts = remember(state.containers, nearCap) { severityCounts(state.containers, nearCap) }
    Row(
        Modifier.fillMaxWidth().height(HiveTokens.Screen.headerHeight),
        horizontalArrangement = Arrangement.spacedBy(GapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MANTEL", style = mono(40.sp, bold = true, spacing = 4.sp), color = c.amber)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.textBright, fontWeight = FontWeight.Bold)) { append(state.host.name) }
                append("  ${state.host.threads} threads · ${state.host.memoryTotalGb} GB")
            },
            Modifier.weight(1f),
            style = HiveType.lead,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val stale = state.stale
        if (stale != null) {
            Pill("✕", "NO SIGNAL ${formatAge(stale.unansweredFor)}", HiveTokens.Color.Status.trouble)
        } else {
            Pill("●", "${counts.wrong} CRITICAL", if (counts.wrong > 0) HiveTokens.Color.Status.trouble else c.textDim)
            Pill("▲", "${counts.busy} BUSY", if (counts.busy > 0) HiveTokens.Color.Status.inProgress else c.textDim)
            Pill("✓", "${counts.ok} OK", HiveTokens.Color.Status.success)
        }
        Spacer(Modifier.width(GapS))
        Text(
            (now.format(Day) + (dataNote?.let { " · $it" } ?: "")).uppercase(),
            style = mono(24.sp),
            color = c.textMuted,
            maxLines = 1,
        )
        Text(now.format(Clock), style = mono(44.sp, bold = true), color = c.textBright)
    }
}

/** A count by severity: a mark, a number and a word, so it never relies on color. */
@Composable
private fun Pill(mark: String, words: String, color: Color) {
    val shape = RoundedCornerShape(PanelRadius)
    Row(
        Modifier
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(PanelBorder, color, shape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, style = CellBold, color = color)
        Text(words, style = CellBold, color = HiveTokens.Color.text)
    }
}

/** A square-cornered panel with a title bar in letter-spaced capitals. */
@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PanelRadius)
    Column(
        modifier
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(PanelBorder, HiveTokens.Color.surfaceBorder, shape)
            .padding(horizontal = PanelPadX, vertical = PanelPadY),
        verticalArrangement = Arrangement.spacedBy(GapS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = Label, color = HiveTokens.Color.textMuted)
            if (trailing != null) Text(trailing, style = HiveType.body, color = HiveTokens.Color.textMuted, maxLines = 1)
        }
        content()
    }
}

// ---- Containers ----------------------------------------------------------

private val ColStatus = 0.19f
private val ColName = 0.29f
private val ColKind = 0.14f
private val ColCpu = 0.15f
private val ColMemory = 0.16f
private val ColUp = 0.07f

private fun flagColor(flag: ContainerFlag): Color = when (flag) {
    ContainerFlag.Missing, ContainerFlag.NearCap -> HiveTokens.Color.Status.trouble
    ContainerFlag.Busy -> HiveTokens.Color.Status.inProgress
    ContainerFlag.Ended -> HiveTokens.Color.textDim
    ContainerFlag.Ok -> HiveTokens.Color.Status.success
}

private fun kindColor(kind: ContainerKind): Color = when (kind) {
    ContainerKind.Runner -> HiveTokens.Color.Kind.runner
    ContainerKind.Database -> HiveTokens.Color.Kind.database
    ContainerKind.Job -> HiveTokens.Color.Kind.job
}

/** "3.6/4.0 GB" against a cap, else "2.8 GB" or "310 MB": short enough for a table column. */
private fun shortMemory(c: ContainerStatus): String {
    fun gb(mb: Int) = String.format(Locale.US, "%.1f", mb / 1024.0)
    return when {
        c.memoryCapMb != null -> "${gb(c.memoryMb)}/${gb(c.memoryCapMb)} GB"
        c.memoryMb >= 1024 -> "${gb(c.memoryMb)} GB"
        else -> "${c.memoryMb} MB"
    }
}

@Composable
private fun ContainerTable(containers: List<ContainerStatus>, nearCap: Int, modifier: Modifier = Modifier) {
    val rows = remember(containers, nearCap) {
        // Worst first; within a state, busiest first as in the hive.
        sortForHive(containers, HiveSort.Busiest).sortedBy { flagOf(it, nearCap).ordinal }
    }
    val running = containers.count { it.isLive }
    val missing = containers.count { it.isMissing }
    Panel(
        "CONTAINERS",
        modifier,
        trailing = "$running running" + if (missing > 0) " · $missing missing" else "",
    ) {
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("STATUS", ColStatus)
            HeaderCell("NAME", ColName)
            HeaderCell("KIND", ColKind)
            HeaderCell("CPU", ColCpu)
            HeaderCell("MEMORY", ColMemory)
            HeaderCell("UP", ColUp, TextAlign.End)
        }
        FitRows(
            rows,
            TableRow,
            Modifier.fillMaxSize(),
            more = { n -> Text("+ $n more", Modifier.align(Alignment.CenterVertically), color = HiveTokens.Color.textMuted) },
        ) { i, row -> ContainerRow(row, flagOf(row, nearCap), striped = i % 2 == 1) }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(text, Modifier.weight(weight), style = Label, color = HiveTokens.Color.textDim, textAlign = align, maxLines = 1)
}

@Composable
private fun RowScope.ContainerRow(c: ContainerStatus, flag: ContainerFlag, striped: Boolean) {
    val colors = HiveTokens.Color
    val dim = flag == ContainerFlag.Ended
    val back = when {
        flag == ContainerFlag.Missing -> HiveTokens.Color.Cell.baseMissing
        striped -> HiveTokens.Color.Cell.base
        else -> Color.Transparent
    }
    Row(
        Modifier.weight(1f).fillMaxHeight().background(back).padding(horizontal = GapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(ColStatus), horizontalArrangement = Arrangement.spacedBy(GapS), verticalAlignment = Alignment.CenterVertically) {
            Text(if (flag == ContainerFlag.Ok) "●" else if (flag == ContainerFlag.Ended) "○" else "▲", style = Cell, color = flagColor(flag))
            Text(flag.word, style = CellBold, color = flagColor(flag), maxLines = 1)
        }
        Text(
            c.shortName,
            Modifier.weight(ColName),
            style = HiveType.bodyBold,
            color = if (dim) colors.textDim else colors.textBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            c.kind.name.lowercase(),
            Modifier.weight(ColKind),
            style = Cell,
            color = if (dim) colors.textDim else kindColor(c.kind),
            maxLines = 1,
        )
        Row(Modifier.weight(ColCpu), horizontalArrangement = Arrangement.spacedBy(GapS), verticalAlignment = Alignment.CenterVertically) {
            Text(if (c.isLive) formatCores(c.cores) else "—", Modifier.width(60.dp), style = Cell, color = if (dim) colors.textDim else colors.text)
            if (c.isLive) {
                SegmentBar(
                    glowLevel(c.cores), CPU_BAR_SEGMENTS,
                    lit = if (flag == ContainerFlag.Busy) HiveTokens.Color.Status.inProgress else colors.textSoft,
                    unlit = colors.track,
                    modifier = Modifier.width(CpuBarWidth).height(CpuBarHeight),
                    gap = 3.dp,
                )
            }
        }
        Text(
            if (c.isLive) shortMemory(c) else "—",
            Modifier.weight(ColMemory),
            style = Cell,
            color = when {
                flag == ContainerFlag.NearCap -> HiveTokens.Color.Status.trouble
                dim -> colors.textDim
                else -> colors.text
            },
            maxLines = 1,
        )
        Text(
            when (val p = c.presence) {
                is Presence.Live -> formatAgeShort(p.uptime)
                is Presence.Missing -> formatAgeShort(p.goneFor)
                is Presence.Ended -> formatAgeShort(p.ago)
            },
            Modifier.weight(ColUp),
            style = Cell,
            color = colors.textMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

// ---- Host ----------------------------------------------------------------

@Composable
private fun MetricTiles(state: DashboardState, modifier: Modifier = Modifier) {
    val n = hostNumbers(state.hostTrends)
    val t = state.hostTrends
    val host = state.host
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
        Tile(
            if (n.cpuTrouble) "CPU · HIGH" else "CPU", "${n.cpu.toInt()}", "%",
            "${one(n.cpu / 100 * host.threads)} cores",
            t.cpuPercent, 100f, trouble = n.cpuTrouble, Modifier.weight(1f),
        )
        Tile(
            "MEMORY", one(n.memoryGb), "GB", "${(n.memoryGb / host.memoryTotalGb * 100).toInt()}% used",
            t.memoryGb, host.memoryTotalGb.toFloat(), trouble = false, Modifier.weight(1f),
        )
        Tile(
            "LOAD", one(n.load), "", "avg ${one(t.load5)} · ${one(t.load15)}",
            t.load, host.threads.toFloat(), trouble = false, Modifier.weight(1f),
        )
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    unit: String,
    sub: String,
    series: List<Float>,
    scaleMax: Float,
    trouble: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = HiveTokens.Color
    Panel(label, modifier.fillMaxHeight()) {
        Text(
            buildAnnotatedString {
                append(value)
                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 24.sp, color = c.textMuted, fontWeight = FontWeight.Normal)) { append(" $unit") }
            },
            style = Number,
            color = if (trouble) HiveTokens.Color.Status.trouble else c.textBright,
            maxLines = 1,
        )
        Text(sub, style = HiveType.body, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Sparkline(series, scaleMax, c.amber, Modifier.fillMaxWidth().height(SparkHeight), fill = c.chartFill)
    }
}

// ---- Events --------------------------------------------------------------

@Composable
private fun Events(state: DashboardState, nearCap: Int) {
    val stale = state.stale
    val events = remember(state.containers, stale, nearCap) {
        val lost = stale?.let {
            Warning(
                state.host.name,
                "has not answered for ${formatAge(it.unansweredFor)} · showing the reading from ${it.lastReadingAt.format(ReadingTime)}",
                Severity.Wrong,
            )
        }
        listOfNotNull(lost) + allWarningsFor(state.containers, nearCap).sortedBy { if (it.severity == Severity.Wrong) 0 else 1 }
    }
    Panel("EVENTS", Modifier.fillMaxSize(), trailing = if (events.isEmpty()) null else "${events.size}") {
        if (events.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(GapS)) {
                Text("✓", style = CellBold, color = HiveTokens.Color.Status.success)
                Text("All containers normal", color = HiveTokens.Color.textSecondary)
            }
            return@Panel
        }
        FitRows(
            events,
            EventRow,
            Modifier.fillMaxSize(),
            more = { n -> Text("+ $n more", Modifier.align(Alignment.CenterVertically), color = HiveTokens.Color.textMuted) },
        ) { _, w -> EventLine(w) }
    }
}

@Composable
private fun RowScope.EventLine(w: Warning) {
    val c = HiveTokens.Color
    val color = if (w.severity == Severity.Wrong) HiveTokens.Color.Status.trouble else HiveTokens.Color.Status.inProgress
    Text(
        if (w.severity == Severity.Wrong) "CRIT" else "BUSY",
        Modifier.width(TagWidth).align(Alignment.CenterVertically),
        style = CellBold,
        color = color,
    )
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textBright)) { append(w.who) }
            append(" ${w.what}")
        },
        Modifier.weight(1f).align(Alignment.CenterVertically),
        color = c.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---- Deploys -------------------------------------------------------------

@Composable
private fun Deploys(state: DashboardState, active: Boolean, runs: List<DeployRun>, repoNames: List<String>, modifier: Modifier) {
    val c = HiveTokens.Color
    val notice = deployNotice(state.deployStatus, runs.isNotEmpty(), repoNames)
    Panel("DEPLOYS", modifier.fillMaxWidth(), trailing = if (notice == null) (if (active) "past hour" else "latest runs") else null) {
        if (notice != null) {
            val (text, trouble) = notice
            Row(horizontalArrangement = Arrangement.spacedBy(GapS)) {
                Text(if (trouble) "▲" else "—", style = CellBold, color = if (trouble) HiveTokens.Color.Status.trouble else c.textDim)
                Text(text, color = if (trouble) HiveTokens.Color.Status.trouble else c.textSecondary)
            }
            return@Panel
        }
        FitRows(
            runs,
            DeployRow,
            Modifier.fillMaxSize(),
            more = { n -> Text("+ $n more", Modifier.align(Alignment.CenterVertically), color = c.textMuted) },
        ) { i, run -> RunRow(run, striped = i % 2 == 1) }
    }
}

@Composable
private fun RowScope.RunRow(run: DeployRun, striped: Boolean) {
    val c = HiveTokens.Color
    val outcome = run.outcome
    Row(
        Modifier.weight(1f).fillMaxHeight().background(if (striped) HiveTokens.Color.Cell.base else Color.Transparent).padding(horizontal = GapS),
        horizontalArrangement = Arrangement.spacedBy(GapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(runWord(outcome), Modifier.width(130.dp), style = CellBold, color = runColor(outcome), maxLines = 1)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.textBright)) { append(run.repo) }
                append(" · ${run.workflow}")
                if (outcome is DeployOutcome.Failed && outcome.failedStep != null) {
                    withStyle(SpanStyle(color = HiveTokens.Color.Status.trouble)) { append(" @ ${outcome.failedStep}") }
                } else {
                    withStyle(SpanStyle(color = c.textMuted)) { append(" · ${run.branch}") }
                }
            },
            Modifier.weight(1f),
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.width(180.dp), contentAlignment = Alignment.CenterEnd) {
            when (outcome) {
                is DeployOutcome.InProgress -> Row(horizontalArrangement = Arrangement.spacedBy(GapS), verticalAlignment = Alignment.CenterVertically) {
                    if (outcome.progress != null) {
                        SegmentBar(outcome.progress, RUN_BAR_SEGMENTS, HiveTokens.Color.Status.inProgress, c.track, Modifier.width(RunBarWidth).height(CpuBarHeight), gap = 3.dp)
                    }
                    Text(formatTimer(outcome.elapsed), style = CellBold, color = HiveTokens.Color.Status.inProgress)
                }
                is DeployOutcome.Failed -> Text("${formatAge(outcome.ago)} ago", style = Cell, color = c.textMuted)
                is DeployOutcome.Passed -> Text("${formatAge(outcome.ago)} ago", style = Cell, color = c.textMuted)
            }
        }
    }
}
