package com.blayter.mantel.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.domain.MachineCondition
import com.blayter.mantel.domain.MachineSummary
import com.blayter.mantel.domain.Plaque
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.plaqueSentence
import com.blayter.mantel.domain.shelfPlaques
import com.blayter.mantel.ui.hive.hexPath
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved shelf mockups that are not in tokens.json.
private val PlaquePaddingX = 20.dp
private val GlyphGap = 16.dp
private val GlyphStroke = 4.dp
private val TurnBarHeight = 6.dp
private const val GLYPH_HEIGHT_RATIO = 1.154f
private const val NAME_LINE_HEIGHT_EM = 1.15f
private val GlyphDash = listOf(6.dp, 5.dp)
private const val ON_SCREEN = "On screen"

/**
 * One plaque per machine along the bottom of the dashboard: which machine is
 * on screen, how many there are, and how each one is doing, always in a
 * sentence. Read-only; the remote only changes which machine is shown.
 *
 * The shelf is one focus stop. With focus, the plaque of the machine on
 * screen takes the ring, and left and right call [onStep].
 *
 * @param turnProgress how far through its turn the machine on screen is, 0..1.
 * @param paused the turns are paused after a key press; the bar goes dim.
 */
@Composable
fun MachineShelf(
    machines: List<MachineSummary>,
    onScreen: Int,
    turnProgress: () -> Float,
    paused: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val plaques = remember(machines, onScreen) { shelfPlaques(machines, onScreen) }
    Row(
        modifier
            .fillMaxWidth()
            .height(HiveTokens.Screen.shelfHeight)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionRight -> 1
                    Key.DirectionLeft -> -1
                    else -> 0
                }
                if (step != 0 && event.type == KeyEventType.KeyDown) onStep(step)
                step != 0
            }
            .focusable()
            .semantics { contentDescription = "Machines. Left and right change the machine on screen." },
        horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
    ) {
        plaques.forEach { plaque ->
            when (plaque) {
                is Plaque.Machine -> {
                    val shown = plaque.index == onScreen
                    MachinePlaque(
                        machines[plaque.index], shown, paused && shown, focused && shown,
                        if (shown) turnProgress else null, Modifier.weight(1f),
                    )
                }
                is Plaque.More -> MorePlaque(plaque, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MachinePlaque(
    machine: MachineSummary,
    onScreen: Boolean,
    paused: Boolean,
    focused: Boolean,
    turnProgress: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    val c = HiveTokens.Color
    val trouble = HiveTokens.Color.Status.trouble
    val condition = machine.condition
    val silent = condition is MachineCondition.NotAnswering
    // Coral is for something wrong; a machine that is only busy keeps the plain plaque.
    val wrong = condition is MachineCondition.Warning && condition.severity == Severity.Wrong
    val border = when {
        onScreen -> c.amber
        wrong -> trouble
        else -> c.surfaceBorder
    }
    val nameColor = when {
        onScreen -> c.textBright
        wrong -> trouble
        silent -> c.textDim
        else -> c.text
    }
    val sentence = plaqueSentence(machine, onScreen, paused)
    PlaqueFrame(
        border, focused, modifier,
        overlay = {
            if (turnProgress != null) {
                // The turn as a line along the bottom edge: amber while running, dim while paused.
                val bar = if (paused) c.textDim else c.amber
                Canvas(Modifier.matchParentSize()) {
                    val h = TurnBarHeight.toPx()
                    drawRect(c.track, Offset(0f, size.height - h), Size(size.width, h))
                    drawRect(bar, Offset(0f, size.height - h), Size(size.width * turnProgress().coerceIn(0f, 1f), h))
                }
            }
        },
    ) {
        MachineGlyph(
            stroke = when {
                onScreen -> c.amber
                wrong -> trouble
                silent -> HiveTokens.Color.Cell.strokeEnded
                else -> c.textMuted
            },
            fill = if (onScreen) HiveTokens.Color.Cell.glow else null,
            dashed = silent && !onScreen,
            mark = wrong && !onScreen,
        )
        Column(Modifier.weight(1f)) {
            Text(
                machine.name,
                style = HiveType.title.copy(lineHeight = NAME_LINE_HEIGHT_EM.em),
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildAnnotatedString {
                    if (onScreen && sentence.startsWith(ON_SCREEN)) {
                        withStyle(SpanStyle(color = c.amber, fontWeight = FontWeight.Bold)) { append(ON_SCREEN) }
                        append(sentence.removePrefix(ON_SCREEN))
                    } else {
                        append(sentence)
                    }
                },
                color = if (silent && !onScreen) c.textDim else c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MorePlaque(more: Plaque.More, modifier: Modifier = Modifier) {
    val c = HiveTokens.Color
    val trouble = HiveTokens.Color.Status.trouble
    val attention = more.needAttention > 0
    PlaqueFrame(if (attention) trouble else c.surfaceBorder, focused = false, modifier = modifier) {
        MachineGlyph(stroke = if (attention) trouble else c.textMuted, fill = null, dashed = false, mark = attention)
        Column(Modifier.weight(1f)) {
            Text(
                "${more.count} more",
                style = HiveType.title.copy(lineHeight = NAME_LINE_HEIGHT_EM.em),
                color = if (attention) trouble else c.text,
                maxLines = 1,
            )
            Text(
                when {
                    !attention -> "All well"
                    more.needAttention == 1 -> "1 needs attention"
                    else -> "${more.needAttention} need attention"
                },
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A craftsman card with the plaque's border color, and the focus ring a little
 * outside it. [content] is the glyph and the text, in a row; [overlay] is
 * drawn over the whole plaque (the turn bar).
 */
@Composable
private fun PlaqueFrame(
    border: Color,
    focused: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    Box(
        modifier
            .fillMaxHeight()
            .drawBehind {
                if (focused) {
                    val out = (HiveTokens.Focus.offset + HiveTokens.Focus.borderWidth / 2).toPx()
                    drawRoundRect(
                        HiveTokens.Focus.color,
                        topLeft = Offset(-out, -out),
                        size = Size(size.width + 2 * out, size.height + 2 * out),
                        cornerRadius = CornerRadius((HiveTokens.Card.radius + HiveTokens.Focus.offset).toPx()),
                        style = Stroke(HiveTokens.Focus.borderWidth.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(HiveTokens.Card.borderWidth, border, shape),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = PlaquePaddingX),
            horizontalArrangement = Arrangement.spacedBy(GlyphGap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        overlay()
    }
}

/** The small hexagon that stands for a machine. A "!" inside means it needs attention. */
@Composable
private fun MachineGlyph(stroke: Color, fill: Color?, dashed: Boolean, mark: Boolean, width: Dp = HiveTokens.Hex.plaqueGlyph) {
    Canvas(Modifier.size(width, width * GLYPH_HEIGHT_RATIO)) {
        val w = GlyphStroke.toPx()
        val path = hexPath(size, w / 2 + 0.5f)
        if (fill != null) clipPath(path) { drawRect(fill) }
        drawPath(
            path, stroke,
            style = Stroke(
                w, join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(GlyphDash.map { it.toPx() }.toFloatArray()) else null,
            ),
        )
        if (mark) {
            val x = size.width / 2
            drawLine(stroke, Offset(x, size.height * 0.30f), Offset(x, size.height * 0.56f), w, StrokeCap.Round)
            drawLine(stroke, Offset(x, size.height * 0.70f), Offset(x, size.height * 0.72f), w, StrokeCap.Round)
        }
    }
}
