package com.blayter.mantel.ui.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blayter.mantel.data.github.GitHubRuns
import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployStatus
import com.blayter.mantel.domain.HOST_CPU_TROUBLE_PERCENT
import com.blayter.mantel.domain.HostTrends
import com.blayter.mantel.data.DashboardRepository.Companion.TREND_SAMPLES
import com.blayter.mantel.domain.litSegments
import com.blayter.mantel.ui.theme.HiveTokens
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale

/*
 * Parts shared by the NOC and Neon layouts. Neither is designed in Claude
 * Design yet; both were built from the owner's description ("typical of a
 * NOC", "cyberpunk, futuristic, neon") and keep every product rule: 24dp
 * body text, status in words as well as color, read-only, no focus needed.
 */

/** Monospace, the system's. Both layouts use it for numbers; Neon for everything. */
internal val Mono = FontFamily.Monospace

internal fun mono(size: TextUnit, bold: Boolean = false, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = Mono,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size,
    letterSpacing = spacing,
)

/** Dims what belongs to a machine that has stopped answering. */
internal const val STALE_ALPHA = 0.55f

/** The time now, ticking every second. */
@Composable
internal fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000)
        }
    }
    return now
}

internal fun one(v: Float) = String.format(Locale.US, "%.1f", v)

/** The three host numbers, as both layouts print them. */
internal data class HostNumbers(val cpu: Float, val memoryGb: Float, val load: Float) {
    val cpuTrouble get() = cpu >= HOST_CPU_TROUBLE_PERCENT
}

internal fun hostNumbers(t: HostTrends) =
    HostNumbers(t.cpuPercent.lastOrNull() ?: 0f, t.memoryGb.lastOrNull() ?: 0f, t.load.lastOrNull() ?: 0f)

/** A line chart over the whole trend window, newest at the right; filled under the line when [fill] is set. */
@Composable
internal fun Sparkline(
    series: List<Float>,
    scaleMax: Float,
    line: Color,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    width: Dp = 3.dp,
) {
    Canvas(modifier) {
        if (series.size < 2 || scaleMax <= 0f) return@Canvas
        val stepX = size.width / (TREND_SAMPLES - 1)
        val startX = size.width - (series.size - 1) * stepX
        fun y(v: Float) = size.height * (1 - v.coerceIn(0f, scaleMax) / scaleMax)
        val path = Path().apply {
            series.forEachIndexed { i, v -> if (i == 0) moveTo(startX, y(v)) else lineTo(startX + i * stepX, y(v)) }
        }
        if (fill != null) {
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(startX, size.height)
                close()
            }
            drawPath(area, fill)
        }
        drawPath(path, line, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A bar of [total] separate segments with [level] of them lit: a level you can count, not only see. */
@Composable
internal fun SegmentBar(level: Float, total: Int, lit: Color, unlit: Color, modifier: Modifier = Modifier, gap: Dp = 4.dp) {
    val on = litSegments(level, total)
    Canvas(modifier) {
        val g = gap.toPx()
        val w = (size.width - g * (total - 1)) / total
        for (i in 0 until total) {
            drawRect(if (i < on) lit else unlit, Offset(i * (w + g), 0f), Size(w, size.height))
        }
    }
}

/**
 * A column of rows of one fixed height that shows as many as fit whole. When
 * some are left out, the last line is [more] with how many.
 */
@Composable
internal fun <T> FitRows(
    items: List<T>,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    more: @Composable RowScope.(Int) -> Unit,
    row: @Composable RowScope.(index: Int, item: T) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val room = ((maxHeight + gap) / (rowHeight + gap)).toInt().coerceAtLeast(0)
        val shown = if (items.size <= room) items.size else (room - 1).coerceAtLeast(0)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.take(shown).forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().height(rowHeight)) { row(i, item) }
            }
            if (shown < items.size) Row(Modifier.fillMaxWidth().height(rowHeight)) { more(items.size - shown) }
        }
    }
}

/** What a deploys panel says instead of runs, or null when it has runs to show. */
internal fun deployNotice(status: DeployStatus, hasRuns: Boolean, repoNames: List<String>): Pair<String, Boolean>? = when (status) {
    DeployStatus.Waiting -> "Waiting for the first reading" to false
    DeployStatus.TokenNotStored -> "GitHub token was not stored on this TV. Deploy status is off." to true
    is DeployStatus.TokenExpired ->
        (if (status.lastSeenAt != null) "GitHub token has expired. Push a config file with a new token." else "GitHub token was not accepted.") to true
    DeployStatus.Live -> if (hasRuns) {
        null
    } else {
        val where = if (repoNames.isEmpty()) "" else " in ${repoNames.joinToString(", ")}"
        "No recent runs$where in the last ${GitHubRuns.RECENT_WINDOW.inWholeDays} days." to false
    }
}

/** The status word of a run, as the table columns and the log print it. */
internal fun runWord(outcome: DeployOutcome): String = when (outcome) {
    is DeployOutcome.InProgress -> "RUNNING"
    is DeployOutcome.Failed -> "FAILED"
    is DeployOutcome.Passed -> "PASSED"
}

internal fun runColor(outcome: DeployOutcome): Color = when (outcome) {
    is DeployOutcome.InProgress -> HiveTokens.Color.Status.inProgress
    is DeployOutcome.Failed -> HiveTokens.Color.Status.trouble
    is DeployOutcome.Passed -> HiveTokens.Color.Status.success
}
