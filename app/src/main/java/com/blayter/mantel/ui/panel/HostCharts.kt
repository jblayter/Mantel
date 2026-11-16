package com.blayter.mantel.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.DashboardRepository.Companion.TREND_SAMPLES
import com.blayter.mantel.domain.HostInfo
import com.blayter.mantel.domain.HostTrends
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import java.util.Locale
import kotlin.math.roundToInt

// Sizes from the approved mockup that are not in tokens.json.
private val TextGap = 2.dp
private val LineWidth = 4.dp

/** The chart uses the top 90% of its height for the full scale. */
private const val CHART_HEADROOM = 0.9f

/** Host CPU at or above this turns the number coral, as in the mockup. */
private const val CPU_TROUBLE_PERCENT = 90f

private fun one(v: Float) = String.format(Locale.US, "%.1f", v)

/** CPU, memory and load: one big number and an area chart each. */
@Composable
fun HostCharts(host: HostInfo, trends: HostTrends, modifier: Modifier = Modifier, stale: Boolean = false) {
    val cpu = trends.cpuPercent.lastOrNull() ?: 0f
    val memory = trends.memoryGb.lastOrNull() ?: 0f
    val load = trends.load.lastOrNull() ?: 0f
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
        HostChart(
            title = "CPU",
            value = cpu.roundToInt().toString(),
            unit = "%",
            sub = "${one(cpu / 100 * host.threads)} of ${host.threads} cores",
            series = trends.cpuPercent,
            scaleMax = 100f,
            valueColor = if (cpu >= CPU_TROUBLE_PERCENT) HiveTokens.Color.Status.trouble else HiveTokens.Color.textBright,
            stale = stale,
            modifier = Modifier.weight(1f),
        )
        HostChart(
            title = "Memory",
            value = one(memory),
            unit = "GB",
            sub = "${(memory / host.memoryTotalGb * 100).roundToInt()}% of ${host.memoryTotalGb} GB",
            series = trends.memoryGb,
            scaleMax = host.memoryTotalGb.toFloat(),
            stale = stale,
            modifier = Modifier.weight(1f),
        )
        HostChart(
            title = "Load",
            value = one(load),
            unit = "",
            sub = "5 m ${one(trends.load5)} · 15 m ${one(trends.load15)}",
            series = trends.load,
            scaleMax = host.threads.toFloat(),
            stale = stale,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The three host cards before the first reading: titles, and dashes where the numbers will be. */
@Composable
fun HostChartsPlaceholder(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
        listOf("CPU", "Memory", "Load").forEach { title ->
            HiveCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.padding(horizontal = HiveTokens.Space.s20, vertical = HiveTokens.Space.s16),
                    verticalArrangement = Arrangement.spacedBy(TextGap),
                ) {
                    Text(title, style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
                    Text("—", style = HiveType.hostNumber.copy(lineHeight = 1.em), color = HiveTokens.Color.textDim)
                }
            }
        }
    }
}

@Composable
private fun HostChart(
    title: String,
    value: String,
    unit: String,
    sub: String,
    series: List<Float>,
    scaleMax: Float,
    stale: Boolean,
    modifier: Modifier = Modifier,
    valueColor: Color = HiveTokens.Color.textBright,
) {
    HiveCard(modifier.fillMaxHeight()) {
        Column(Modifier.padding(top = HiveTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(TextGap)) {
            Column(
                Modifier.padding(horizontal = HiveTokens.Space.s20),
                verticalArrangement = Arrangement.spacedBy(TextGap),
            ) {
                Text(title, style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
                Text(
                    buildAnnotatedString {
                        append(value)
                        if (unit.isNotEmpty()) {
                            withStyle(HiveType.body.toSpanStyle().copy(color = HiveTokens.Color.textMuted)) { append(" $unit") }
                        }
                    },
                    style = HiveType.hostNumber.copy(lineHeight = 1.em),
                    color = if (stale) HiveTokens.Color.textDim else valueColor,
                    maxLines = 1,
                )
                Text(
                    sub,
                    style = HiveType.cellLabelSmall,
                    color = HiveTokens.Color.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AreaChart(series, scaleMax, stale, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Filled area with an amber line on top, edge to edge across the card. Grey when the reading is stale. */
@Composable
private fun AreaChart(series: List<Float>, scaleMax: Float, stale: Boolean, modifier: Modifier = Modifier) {
    val fill = if (stale) HiveTokens.Color.track else HiveTokens.Color.chartFill
    val stroke = if (stale) HiveTokens.Color.textDim else HiveTokens.Color.amber
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        // Always the full window wide, newest at the right edge.
        val stepX = size.width / (TREND_SAMPLES - 1)
        val startX = size.width - (series.size - 1) * stepX
        fun y(v: Float) = size.height * (1 - v.coerceIn(0f, scaleMax) / scaleMax * CHART_HEADROOM)
        val line = Path().apply {
            series.forEachIndexed { i, v -> if (i == 0) moveTo(startX, y(v)) else lineTo(startX + i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(startX, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(line, stroke, style = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
