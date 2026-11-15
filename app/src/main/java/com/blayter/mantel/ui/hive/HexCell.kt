package com.blayter.mantel.ui.hive

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.domain.ContainerKind
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.Presence
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.formatAgeShort
import com.blayter.mantel.domain.formatCores
import com.blayter.mantel.domain.formatMemory
import com.blayter.mantel.domain.glowLevel
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Text limits inside a cell, from the approved mockup.
private val TextInset = 14.dp
// Wider than the mockup's 150 so "DATABASE · 13m" fits; still inside the outline at that height.
private val KindMaxWidth = 176.dp
private val NameMaxWidth = 184.dp
private val SubMaxWidth = 170.dp
private const val KIND_LETTER_SPACING_EM = 0.06f
private const val NUMBER_LINE_HEIGHT_EM = 1.05f
private const val PULSE_MIN_ALPHA = 0.5f

fun ContainerKind.color(): Color = when (this) {
    ContainerKind.Runner -> HiveTokens.Color.Kind.runner
    ContainerKind.Database -> HiveTokens.Color.Kind.database
    ContainerKind.Job -> HiveTokens.Color.Kind.job
}

private fun ContainerKind.word() = when (this) {
    ContainerKind.Runner -> "RUNNER"
    ContainerKind.Database -> "DATABASE"
    ContainerKind.Job -> "JOB"
}

/** Pointy-top hexagon inside [size], pulled in by [inset] on every side. */
private fun hexPath(size: Size, inset: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w / 2, inset)
    lineTo(w - inset, h * 0.25f + inset / 2)
    lineTo(w - inset, h * 0.75f - inset / 2)
    lineTo(w / 2, h - inset)
    lineTo(inset, h * 0.75f - inset / 2)
    lineTo(inset, h * 0.25f + inset / 2)
    close()
}

/** One container as a hexagonal cell. The five states are in DESIGN.md, "Cell states". */
@Composable
fun HexCell(container: ContainerStatus, modifier: Modifier = Modifier) {
    val c = HiveTokens.Color
    val trouble = HiveTokens.Color.Status.trouble
    val missing = container.isMissing
    val ended = container.isEnded
    val live = container.isLive
    val hot = container.isSustained
    val memHot = container.isMemoryNearCap(HiveTokens.ThresholdsProposed.memoryNearCapPercent)

    val base = when {
        missing -> HiveTokens.Color.Cell.baseMissing
        ended -> HiveTokens.Color.Cell.baseEnded
        else -> HiveTokens.Color.Cell.base
    }
    val glow = if (hot) HiveTokens.Color.Cell.glowHot else HiveTokens.Color.Cell.glow
    val level = if (live) glowLevel(container.cores) else 0f
    val stroke = when {
        missing || memHot -> trouble
        ended -> HiveTokens.Color.Cell.strokeEnded
        hot -> c.amber
        else -> container.kind.color()
    }
    val strokeWidth: Dp = if (missing || memHot || hot) HiveTokens.Hex.strokeWidthAlert else HiveTokens.Hex.strokeWidth
    val dashed = missing || ended
    val strokeAlpha = if (missing) pulse() else remember { mutableFloatStateOf(1f) }

    Box(modifier.size(HiveTokens.Hex.width, HiveTokens.Hex.height)) {
        Canvas(Modifier.fillMaxSize()) {
            clipPath(hexPath(size, 0f)) {
                drawRect(base)
                if (level > 0f) {
                    drawRect(glow, Offset(0f, size.height * (1 - level)), Size(size.width, size.height * level))
                }
            }
            drawPath(
                path = hexPath(size, HiveTokens.Hex.strokeWidth.toPx()),
                color = stroke,
                alpha = strokeAlpha.value,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    join = StrokeJoin.Round,
                    pathEffect = if (dashed) {
                        PathEffect.dashPathEffect(HiveTokens.Hex.dash.map { it.toPx() }.toFloatArray())
                    } else {
                        null
                    },
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = TextInset),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val presence = container.presence
            Line(
                text = when (presence) {
                    is Presence.Missing -> "MISSING"
                    is Presence.Ended -> "ENDED"
                    is Presence.Live -> "${container.kind.word()} · ${formatAgeShort(presence.uptime)}"
                },
                style = HiveType.cellLabelSmallBold.copy(letterSpacing = KIND_LETTER_SPACING_EM.em),
                color = if (missing) trouble else if (ended) c.textDim else container.kind.color(),
                maxWidth = KindMaxWidth,
            )
            Text(
                text = if (live) formatCores(container.cores) else "—",
                style = HiveType.displayL.copy(lineHeight = NUMBER_LINE_HEIGHT_EM.em),
                color = if (live) c.textBright else c.textDim,
                maxLines = 1,
            )
            Line(container.shortName, HiveType.cellLabel, if (ended) c.textDim else c.text, NameMaxWidth)
            Line(
                text = when {
                    presence is Presence.Missing -> "gone ${formatAge(presence.goneFor)}"
                    presence is Presence.Ended -> "${formatAge(presence.ago)} ago"
                    hot -> "busy ${formatAge(container.sustainedFor!!)}"
                    else -> formatMemory(container)
                },
                style = if (missing || memHot || hot) HiveType.cellLabelSmallBold else HiveType.cellLabelSmall,
                color = when {
                    missing || memHot -> trouble
                    ended -> c.textDim
                    hot -> c.amberSoft
                    else -> c.textSoft
                },
                maxWidth = SubMaxWidth,
            )
        }
    }
}

@Composable
private fun Line(text: String, style: TextStyle, color: Color, maxWidth: Dp) {
    Text(text, Modifier.widthIn(max = maxWidth), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** The slow pulse on a missing cell's outline: one of the few animations DESIGN.md allows. */
@Composable
private fun pulse(): State<Float> {
    val halfMillis = (HiveTokens.Motion.pulseSeconds * 1000 / 2).toInt()
    return rememberInfiniteTransition(label = "missing").animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(halfMillis, easing = EaseInOut), RepeatMode.Reverse),
        label = "pulse",
    )
}
