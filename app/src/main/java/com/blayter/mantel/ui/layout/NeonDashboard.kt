package com.blayter.mantel.ui.layout

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.blayter.mantel.domain.MAX_WARNINGS
import com.blayter.mantel.domain.Presence
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.domain.deployBoard
import com.blayter.mantel.domain.deployRowsAtOnce
import com.blayter.mantel.domain.flagOf
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.formatAgeShort
import com.blayter.mantel.domain.formatCores
import com.blayter.mantel.domain.formatMemory
import com.blayter.mantel.domain.formatTimer
import com.blayter.mantel.domain.formatTook
import com.blayter.mantel.domain.glowLevel
import com.blayter.mantel.domain.sortForHive
import com.blayter.mantel.domain.warningsFor
import com.blayter.mantel.ui.ScreenFrame
import com.blayter.mantel.ui.panel.ModeChangeMillis
import com.blayter.mantel.ui.theme.HiveTokens
import java.time.format.DateTimeFormatter

// Sizes of the Neon layout. Not designed in a mockup; see LayoutParts.
private val GridWidth = 980.dp
private val TileHeight = 184.dp
private val TileGap = 16.dp
private val TilePad = 14.dp
private val Chamfer = 18.dp
private val Outline = 3.dp
private val OutlineGlow = 10.dp
private val TileBar = 8.dp
private val GaugeSize = 320.dp
private val GaugeStroke = 22.dp
private val BarHeight = 18.dp
private val LogRow = 46.dp
private val LogTag = 96.dp
private val GapS = 8.dp
private val GapM = 16.dp
private const val TILES_PER_ROW = 4
private const val TILE_SEGMENTS = 10
private const val HOST_SEGMENTS = 24
private const val RUN_SEGMENTS = 8
private const val GAUGE_SWEEP = 270f
private const val GLOW_ALPHA = 0.22f

private val Clock = DateTimeFormatter.ofPattern("HH:mm:ss")
private val ReadingTime = DateTimeFormatter.ofPattern("HH:mm")

private val Body = mono(24.sp)
private val BodyBold = mono(24.sp, bold = true)
private val Small = mono(20.sp, bold = true, spacing = 1.sp)
private val Heading = mono(24.sp, bold = true, spacing = 3.sp)

/**
 * The Neon layout: cyberpunk, on near black. Containers are chamfered tiles
 * with a lit outline in their kind's color and a segment bar for their cores;
 * the host is a CPU ring and segment bars; deploys are a log, newest action
 * first. Bright color is only ever lines and text, never an area.
 */
@Composable
fun NeonDashboard(
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
    ScreenFrame(gap = HiveTokens.Space.s20) {
        Header(state, dataNote)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            Crossfade(machineKey, Modifier.width(GridWidth).fillMaxHeight(), tween(ModeChangeMillis), label = "neon grid") { key ->
                val m = seen[key] ?: state
                Column(verticalArrangement = Arrangement.spacedBy(GapM)) {
                    val live = m.containers.count { it.isLive }
                    val missing = m.containers.count { it.isMissing }
                    Text(
                        "// CONTAINERS  $live LIVE" + if (missing > 0) " · $missing MISSING" else "",
                        style = Heading,
                        color = HiveTokens.Color.amber,
                    )
                    TileGrid(m.containers, nearCap, Modifier.weight(1f).alpha(if (m.stale != null) STALE_ALPHA else 1f))
                    Alerts(m, nearCap, max = if (shelf != null) 2 else MAX_WARNINGS)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                Crossfade(machineKey, animationSpec = tween(ModeChangeMillis), label = "neon host") { key ->
                    val m = seen[key] ?: state
                    Host(m, Modifier.alpha(if (m.stale != null) STALE_ALPHA else 1f))
                }
                DeployLog(state, board.active, deployRowsAtOnce(board), repoNames, Modifier.weight(1f))
            }
        }
        shelf?.invoke()
    }
}

@Composable
private fun Header(state: DashboardState, dataNote: String?) {
    val c = HiveTokens.Color
    val now = rememberNow()
    Row(
        Modifier.fillMaxWidth().height(HiveTokens.Screen.headerHeight),
        horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "MANTEL//",
            style = mono(44.sp, bold = true, spacing = 6.sp),
            color = c.amber,
        )
        Text(
            buildAnnotatedString {
                append("> ")
                withStyle(SpanStyle(color = HiveTokens.Color.Kind.runner, fontWeight = FontWeight.Bold)) { append(state.host.name) }
                append(" · ${state.host.threads}T · ${state.host.memoryTotalGb}G")
                if (dataNote != null) append(" · ${dataNote.uppercase()}")
            },
            Modifier.weight(1f),
            style = Body,
            color = c.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val stale = state.stale
        Text(
            if (stale == null) "■ SYS ONLINE" else "✕ SIGNAL LOST ${formatAge(stale.unansweredFor).uppercase()}",
            style = BodyBold,
            color = if (stale == null) HiveTokens.Color.Status.success else HiveTokens.Color.Status.trouble,
        )
        Text(
            now.format(Clock),
            style = mono(48.sp, bold = true),
            color = c.textBright,
        )
    }
}

// ---- Containers ----------------------------------------------------------

private fun kindColor(kind: ContainerKind): Color = when (kind) {
    ContainerKind.Runner -> HiveTokens.Color.Kind.runner
    ContainerKind.Database -> HiveTokens.Color.Kind.database
    ContainerKind.Job -> HiveTokens.Color.Kind.job
}

/** A rectangle with the top-right and bottom-left corners cut off. */
private fun chamfered(w: Float, h: Float, cut: Float, inset: Float) = Path().apply {
    moveTo(inset, inset)
    lineTo(w - cut, inset)
    lineTo(w - inset, cut)
    lineTo(w - inset, h - inset)
    lineTo(cut, h - inset)
    lineTo(inset, h - cut)
    close()
}

@Composable
private fun TileGrid(containers: List<ContainerStatus>, nearCap: Int, modifier: Modifier) {
    val sorted = remember(containers) { sortForHive(containers, HiveSort.Busiest) }
    BoxWithConstraints(modifier) {
        val rows = ((maxHeight + TileGap) / (TileHeight + TileGap)).toInt().coerceAtLeast(1)
        val room = rows * TILES_PER_ROW
        val shown = if (sorted.size <= room) sorted else sorted.take(room - 1)
        val hidden = sorted.size - shown.size
        val tileWidth = (GridWidth - TileGap * (TILES_PER_ROW - 1)) / TILES_PER_ROW
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            (shown.map<ContainerStatus, ContainerStatus?> { it } + if (hidden > 0) listOf(null) else emptyList())
                .chunked(TILES_PER_ROW)
                .forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        line.forEach { c ->
                            val m = Modifier.size(tileWidth, TileHeight)
                            if (c == null) MoreTile(hidden, m) else Tile(c, flagOf(c, nearCap), m)
                        }
                    }
                }
        }
    }
}

@Composable
private fun TileFrame(stroke: Color, fill: Color, dashed: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cut = Chamfer.toPx()
            val shape = chamfered(size.width, size.height, cut, Outline.toPx() / 2)
            drawPath(shape, fill)
            val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx())) else null
            if (!dashed) drawPath(shape, stroke.copy(alpha = GLOW_ALPHA), style = Stroke(OutlineGlow.toPx()))
            drawPath(shape, stroke, style = Stroke(Outline.toPx(), pathEffect = dash))
        }
        Box(Modifier.fillMaxSize().padding(TilePad)) { content() }
    }
}

@Composable
private fun Tile(c: ContainerStatus, flag: ContainerFlag, modifier: Modifier) {
    val colors = HiveTokens.Color
    val presence = c.presence
    val (stroke, fill, dashed) = when (flag) {
        ContainerFlag.Missing -> Triple(HiveTokens.Color.Status.trouble, HiveTokens.Color.Cell.baseMissing, true)
        ContainerFlag.Ended -> Triple(HiveTokens.Color.Cell.strokeEnded, HiveTokens.Color.Cell.baseEnded, true)
        ContainerFlag.NearCap -> Triple(HiveTokens.Color.Status.trouble, HiveTokens.Color.Cell.base, false)
        else -> Triple(kindColor(c.kind), HiveTokens.Color.Cell.base, false)
    }
    val dim = flag == ContainerFlag.Ended
    TileFrame(stroke, fill, dashed, modifier) {
        Column(Modifier.fillMaxSize()) {
            val top = when (presence) {
                is Presence.Missing -> "MISSING · ${formatAgeShort(presence.goneFor)}"
                is Presence.Ended -> "ENDED · ${formatAgeShort(presence.ago)}"
                is Presence.Live -> {
                    val kind = c.kind.name.uppercase()
                    if (flag == ContainerFlag.Busy) "BUSY · $kind" else "$kind · ${formatAgeShort(presence.uptime)}"
                }
            }
            Text(
                top,
                style = Small,
                color = when (flag) {
                    ContainerFlag.Missing -> HiveTokens.Color.Status.trouble
                    ContainerFlag.Ended -> colors.textDim
                    ContainerFlag.Busy -> HiveTokens.Color.Status.inProgress
                    else -> kindColor(c.kind)
                },
                maxLines = 1,
            )
            Text(
                if (c.isLive) formatCores(c.cores) else "--",
                style = mono(44.sp, bold = true),
                color = if (dim) colors.textDim else colors.textBright,
                maxLines = 1,
            )
            Text(
                c.shortName,
                style = BodyBold,
                color = if (dim) colors.textDim else colors.textBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (c.isLive) (if (flag == ContainerFlag.NearCap) "▲ " else "") + formatMemory(c) else " ",
                style = mono(20.sp),
                color = if (flag == ContainerFlag.NearCap) HiveTokens.Color.Status.trouble else colors.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.weight(1f))
            SegmentBar(
                if (c.isLive) glowLevel(c.cores) else 0f, TILE_SEGMENTS,
                lit = stroke, unlit = colors.track,
                modifier = Modifier.fillMaxWidth().height(TileBar), gap = 3.dp,
            )
        }
    }
}

@Composable
private fun MoreTile(hidden: Int, modifier: Modifier) {
    val c = HiveTokens.Color
    TileFrame(c.surfaceBorder, c.surface, dashed = true, modifier = modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+$hidden", style = mono(44.sp, bold = true), color = c.textMuted)
            Text("MORE", style = Small, color = c.textMuted)
        }
    }
}

@Composable
private fun Alerts(state: DashboardState, nearCap: Int, max: Int) {
    val c = HiveTokens.Color
    val stale = state.stale
    val lines = remember(state.containers, stale, nearCap, max) {
        val lost = stale?.let {
            Warning(
                state.host.name,
                "has not answered for ${formatAge(it.unansweredFor)} · reading from ${it.lastReadingAt.format(ReadingTime)}",
                Severity.Wrong,
            )
        }
        (listOfNotNull(lost) + warningsFor(state.containers, nearCap, max)).take(max)
    }
    Column(verticalArrangement = Arrangement.spacedBy(GapS)) {
        if (lines.isEmpty()) {
            Text("// ALL SYSTEMS NOMINAL", style = Heading, color = HiveTokens.Color.Status.success)
        }
        lines.forEach { w ->
            val color = if (w.severity == Severity.Wrong) HiveTokens.Color.Status.trouble else HiveTokens.Color.Status.inProgress
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                        append(if (w.severity == Severity.Wrong) "!! " else "~~ ")
                        append(w.who)
                    }
                    append(" ${w.what}")
                },
                style = Body,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Host ----------------------------------------------------------------

@Composable
private fun Host(state: DashboardState, modifier: Modifier = Modifier) {
    val c = HiveTokens.Color
    val n = hostNumbers(state.hostTrends)
    val t = state.hostTrends
    val host = state.host
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s32), verticalAlignment = Alignment.CenterVertically) {
        CpuRing(n.cpu, n.cpuTrouble, "${one(n.cpu / 100 * host.threads)}/${host.threads} CORES")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapM)) {
            Meter("MEM", "${one(n.memoryGb)}/${host.memoryTotalGb} GB", n.memoryGb / host.memoryTotalGb, c.amberSoft)
            Meter("LOAD", "${one(n.load)} · ${one(t.load5)} · ${one(t.load15)}", n.load / host.threads, c.amberSoft)
            Text("CPU TREND", style = Small, color = c.textMuted)
            Sparkline(t.cpuPercent, 100f, c.amber, Modifier.fillMaxWidth().height(70.dp), fill = c.chartFill)
        }
    }
}

/** CPU as a 270° ring with the percentage inside. */
@Composable
private fun CpuRing(percent: Float, trouble: Boolean, sub: String) {
    val c = HiveTokens.Color
    val color = if (trouble) HiveTokens.Color.Status.trouble else c.amber
    Box(Modifier.size(GaugeSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = GaugeStroke.toPx()
            val inset = w / 2 + 6.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
            val start = 90f + (360f - GAUGE_SWEEP) / 2
            drawArc(c.track, start, GAUGE_SWEEP, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Butt))
            val sweep = GAUGE_SWEEP * (percent / 100f).coerceIn(0f, 1f)
            drawArc(color.copy(alpha = GLOW_ALPHA), start, sweep, false, topLeft, arc, style = Stroke(w + 14.dp.toPx(), cap = StrokeCap.Butt))
            drawArc(color, start, sweep, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Butt))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CPU" + if (trouble) " · HIGH" else "", style = Small, color = if (trouble) HiveTokens.Color.Status.trouble else c.textMuted)
            Text(
                buildAnnotatedString {
                    append("${percent.toInt()}")
                    withStyle(SpanStyle(fontSize = 32.sp)) { append("%") }
                },
                style = mono(88.sp, bold = true),
                color = c.textBright,
            )
            Text(sub, style = mono(20.sp), color = c.textMuted)
        }
    }
}

@Composable
private fun Meter(label: String, value: String, level: Float, lit: Color) {
    val c = HiveTokens.Color
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, Modifier.weight(1f), style = Small, color = c.textMuted)
            Text(value, style = BodyBold, color = c.textBright)
        }
        SegmentBar(level, HOST_SEGMENTS, lit, c.track, Modifier.fillMaxWidth().height(BarHeight), gap = 4.dp)
    }
}

// ---- Deploys -------------------------------------------------------------

@Composable
private fun DeployLog(state: DashboardState, active: Boolean, runs: List<DeployRun>, repoNames: List<String>, modifier: Modifier) {
    val c = HiveTokens.Color
    val notice = deployNotice(state.deployStatus, runs.isNotEmpty(), repoNames)
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(2.dp, c.surfaceBorder)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(GapS),
    ) {
        Row {
            Text("// DEPLOY.LOG", Modifier.weight(1f), style = Heading, color = c.amber)
            if (notice == null) Text(if (active) "PAST HOUR" else "LATEST", style = Small, color = c.textMuted)
        }
        if (notice != null) {
            val (text, trouble) = notice
            Text("> $text", style = Body, color = if (trouble) HiveTokens.Color.Status.trouble else c.textSecondary)
            return@Column
        }
        FitRows(
            runs,
            LogRow,
            Modifier.fillMaxSize(),
            more = { n -> Text("> +$n more", Modifier.align(Alignment.CenterVertically), style = Body, color = c.textMuted) },
        ) { _, run -> LogLine(run) }
    }
}

@Composable
private fun RowScope.LogLine(run: DeployRun) {
    val c = HiveTokens.Color
    val o = run.outcome
    val tag = when (o) {
        is DeployOutcome.InProgress -> "[RUN ]"
        is DeployOutcome.Failed -> "[FAIL]"
        is DeployOutcome.Passed -> "[ OK ]"
    }
    Text(tag, Modifier.width(LogTag).align(Alignment.CenterVertically), style = BodyBold, color = runColor(o))
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.textBright, fontWeight = FontWeight.Bold)) { append("${run.repo}/${run.workflow}") }
            when (o) {
                is DeployOutcome.InProgress -> append(" ${run.branch}")
                is DeployOutcome.Failed -> withStyle(SpanStyle(color = HiveTokens.Color.Status.trouble)) { append(" @ ${o.failedStep ?: "?"}") }
                is DeployOutcome.Passed -> append(" ${formatTook(o.took)}")
            }
        },
        Modifier.weight(1f).align(Alignment.CenterVertically),
        style = Body,
        color = c.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(GapS), verticalAlignment = Alignment.CenterVertically) {
        when (o) {
            is DeployOutcome.InProgress -> {
                if (o.progress != null) SegmentBar(o.progress, RUN_SEGMENTS, HiveTokens.Color.Status.inProgress, c.track, Modifier.width(96.dp).height(14.dp), gap = 3.dp)
                Text(formatTimer(o.elapsed), style = BodyBold, color = HiveTokens.Color.Status.inProgress)
            }
            is DeployOutcome.Failed -> Text("${formatAge(o.ago)} ago", style = Body, color = c.textMuted)
            is DeployOutcome.Passed -> Text("${formatAge(o.ago)} ago", style = Body, color = c.textMuted)
        }
    }
}
