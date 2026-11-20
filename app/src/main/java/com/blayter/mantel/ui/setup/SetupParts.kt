package com.blayter.mantel.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.ui.HiveFrame
import com.blayter.mantel.ui.panel.HiveCard
import com.blayter.mantel.ui.panel.Spinner
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved setup boards that are not in tokens.json.
private val TitleTop = 28.dp
private val LeadTop = 16.dp
private val BodyTop = 28.dp
private val NoteBottom = 22.dp
private val ButtonRowPadding = 6.dp
private val ButtonPaddingX = 26.dp
private val ButtonPaddingY = 12.dp
private val StepNumberWidth = 26.dp
private val StepGap = 14.dp
private val TickSize = 28.dp
private const val TICK_STROKE = 2.8f
private const val TITLE_LINE_HEIGHT_EM = 1.1f
internal const val SETUP_TEXT_LINE_HEIGHT_EM = 1.4f
internal const val SETUP_CARD_LINE_HEIGHT_EM = 1.35f

internal const val SETUP_STEP_MACHINES = "Setup · step 1 of 2 · machines"
internal const val SETUP_STEP_DEPLOYS = "Setup · step 2 of 2 · deploys"
internal const val SETUP_READY = "Setup · ready"
internal const val SETTINGS_SUBTITLE = "Settings"
const val SETTINGS_MACHINES_SUBTITLE = "Settings · machines"

/** What the header says on the machines screens: setup's step 1, or settings when opened from there. */
val LocalMachinesSubtitle = compositionLocalOf { SETUP_STEP_MACHINES }

/**
 * The frame every setup screen shares, so nothing jumps between them: the
 * title at one height, the buttons on the bottom edge of the left column,
 * and one supporting thing in the right column.
 *
 * @param note a quiet sentence just above the buttons.
 * @param leadMinLines room kept for the lead, where something beside it must line up with what is under it.
 */
@Composable
fun SetupFrame(
    title: String,
    lead: String,
    buttons: @Composable RowScope.() -> Unit,
    side: @Composable BoxScope.() -> Unit,
    note: String? = null,
    subtitle: String = LocalMachinesSubtitle.current,
    leadMinLines: Int = 1,
    body: @Composable ColumnScope.() -> Unit,
) {
    HiveFrame(subtitle = subtitle, showDay = false) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            Column(Modifier.width(HiveTokens.Screen.hiveColumnWidth).fillMaxHeight().padding(top = TitleTop)) {
                Text(
                    title,
                    style = HiveType.displayL.copy(lineHeight = TITLE_LINE_HEIGHT_EM.em),
                    color = HiveTokens.Color.textBright,
                )
                Spacer(Modifier.height(LeadTop))
                Text(
                    lead,
                    style = HiveType.lead.copy(lineHeight = SETUP_TEXT_LINE_HEIGHT_EM.em),
                    color = HiveTokens.Color.textSecondary,
                    minLines = leadMinLines,
                )
                Spacer(Modifier.height(BodyTop))
                Column(verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20), content = body)
                Spacer(Modifier.weight(1f))
                if (note != null) {
                    Text(
                        note,
                        style = HiveType.body.copy(lineHeight = SETUP_CARD_LINE_HEIGHT_EM.em),
                        color = HiveTokens.Color.textMuted,
                    )
                    Spacer(Modifier.height(NoteBottom))
                }
                Row(
                    Modifier.padding(ButtonRowPadding),
                    horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = side)
        }
    }
}

/** The focus ring: 3 px of bright cream, 3 px outside a card-shaped control. */
internal fun Modifier.focusRing(focused: Boolean): Modifier = drawBehind {
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

/**
 * A setup button: a card with a bold label. The main action is amber with
 * dark text. Setup buttons only change what this display stores or shows.
 */
@Composable
fun SetupButton(label: String, modifier: Modifier = Modifier, main: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    val fill = if (main) HiveTokens.Color.amber else HiveTokens.Color.surface
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(shape)
            .background(fill)
            .border(HiveTokens.Card.borderWidth, if (main) HiveTokens.Color.amber else HiveTokens.Color.surfaceBorder, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = ButtonPaddingX, vertical = ButtonPaddingY),
    ) {
        Text(
            label,
            style = HiveType.bodyBold,
            color = if (main) HiveTokens.Color.textOnAmber else HiveTokens.Color.textSecondary,
            maxLines = 1,
        )
    }
}

/** Where a button would be when there is nothing for it to do yet: dim, and not a focus stop. */
@Composable
fun SetupButtonPlaceholder(label: String) {
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    Box(
        Modifier
            .clip(shape)
            .border(HiveTokens.Card.borderWidth, HiveTokens.Color.surfaceBorder, shape)
            .padding(horizontal = ButtonPaddingX, vertical = ButtonPaddingY),
    ) {
        Text(label, style = HiveType.bodyBold, color = HiveTokens.Color.textDim, maxLines = 1)
    }
}

/** The amber ring and one sentence: something is going on by itself. */
@Composable
fun SpinnerLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spinner()
        Text(text, color = HiveTokens.Color.textSecondary)
    }
}

/** A card with a quiet bold heading and lines under it: numbered steps, or a ticked list. */
@Composable
fun SetupCard(heading: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HiveCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = HiveTokens.Card.paddingX, vertical = HiveTokens.Card.paddingY),
            verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s10),
        ) {
            Text(
                heading,
                style = HiveType.bodyBold,
                color = HiveTokens.Color.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}

/** One numbered step in a [SetupCard]. */
@Composable
fun SetupStep(number: Int, text: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(StepGap)) {
        Text(
            number.toString(),
            Modifier.width(StepNumberWidth).alignByBaseline(),
            style = HiveType.displayM.copy(fontSize = HiveTokens.TypeScale.title),
            color = HiveTokens.Color.amber,
        )
        Text(
            text,
            Modifier.alignByBaseline(),
            style = HiveType.body.copy(lineHeight = SETUP_CARD_LINE_HEIGHT_EM.em),
            color = HiveTokens.Color.textBright,
        )
    }
}

@Composable
fun SetupStep(number: Int, text: String) = SetupStep(number, AnnotatedString(text))

/** The green tick of a ticked list. */
@Composable
fun SetupTick() {
    Canvas(Modifier.size(TickSize)) {
        val u = size.width / 24f
        val tick = Path().apply {
            moveTo(5f * u, 12.5f * u)
            lineTo(9.5f * u, 17f * u)
            lineTo(19f * u, 7.5f * u)
        }
        drawPath(
            tick,
            HiveTokens.Color.Status.success,
            style = Stroke(TICK_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * A button that asks once before acting: the first OK changes its words to
 * [confirm], the second does it. Moving away takes the question back.
 */
@Composable
fun ConfirmButton(label: String, confirm: String, modifier: Modifier = Modifier, onConfirmed: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    SetupButton(
        if (armed) confirm else label,
        modifier.onFocusChanged { if (!it.isFocused) armed = false },
    ) {
        if (armed) onConfirmed() else armed = true
    }
}

/** One line of a ticked list: a green tick, a bold name, and a quiet detail after it. */
@Composable
fun TickedLine(name: String, detail: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(StepGap), verticalAlignment = Alignment.CenterVertically) {
        SetupTick()
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                if (detail != null) {
                    withStyle(SpanStyle(color = HiveTokens.Color.textMuted)) { append(" · $detail") }
                }
            },
            color = HiveTokens.Color.textBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
