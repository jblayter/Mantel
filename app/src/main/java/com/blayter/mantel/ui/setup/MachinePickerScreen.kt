package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.config.ConfigFile
import com.blayter.mantel.domain.FoundMachine
import com.blayter.mantel.domain.MAX_PICKER_CELLS
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.domain.pickerSentence
import com.blayter.mantel.domain.watchLabel
import com.blayter.mantel.ui.hive.EmptyHexCell
import com.blayter.mantel.ui.hive.WarningLine
import com.blayter.mantel.ui.hive.hexPath
import com.blayter.mantel.ui.panel.HiveCard
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved "Setup · machines" board that are not in tokens.json.
private const val WATCHING_GLOW_LEVEL = 0.38f
private val CellTextInset = 14.dp
private val CellNameMaxWidth = 176.dp
private val CheckSize = 20.dp
private val CheckGap = 6.dp
private const val STATE_LETTER_SPACING_EM = 0.06f
// "NOT WATCHING" is the longest state word and sits where the cell is narrow.
private const val STATE_LETTER_SPACING_TIGHT_EM = 0.02f
private const val NUMBER_LINE_HEIGHT_EM = 1.05f
// The largest code that fits beside the three steps. Not on a board.
private val HelpQrSize = 220.dp
private val DetailPaddingX = 30.dp
private val DetailPaddingY = 24.dp
private val DetailGap = 12.dp
private val DetailLastLineTop = 6.dp
private const val DETAIL_NAME_LINE_HEIGHT_EM = 1.1f

/**
 * Setup, "Which machines should Mantel watch?". The comb is the picker: each
 * machine that answered is a cell, OK on a cell watches it or stops, and the
 * card on the right says the rest about the cell in focus. Nothing here can
 * change a machine.
 *
 * @param machines in display order; at most [MAX_PICKER_CELLS] are shown.
 * @param watched the `url` of every machine picked.
 * @param focusWatch goes up when the focus should move to the main button.
 */
@Composable
fun MachinePickerScreen(
    machines: List<FoundMachine>,
    watched: Set<String>,
    searching: Boolean,
    checked: Int,
    total: Int,
    fileProblem: String?,
    focusWatch: Int,
    onToggle: (FoundMachine) -> Unit,
    onWatch: () -> Unit,
    onEnterAddress: () -> Unit,
    onLookAgain: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val shown = machines.take(MAX_PICKER_CELLS)
    var inFocus by remember { mutableStateOf<String?>(null) }
    var cellHasFocus by remember { mutableStateOf(false) }
    val firstCell = remember { FocusRequester() }
    val watchButton = remember { FocusRequester() }
    val addressButton = remember { FocusRequester() }
    val watchWords = watchLabel(watched.size)
    // Stated outright, so the remote can always get between the comb and the buttons.
    val belowCells = if (watchWords != null) watchButton else addressButton
    val toCells = if (shown.isNotEmpty()) Modifier.focusProperties { up = firstCell } else Modifier

    // The first cell takes the focus as soon as there is one; until then a button has it.
    LaunchedEffect(shown.isNotEmpty()) {
        runCatching { (if (shown.isNotEmpty()) firstCell else addressButton).requestFocus() }
    }
    LaunchedEffect(focusWatch) {
        if (focusWatch > 0 && watchWords != null) runCatching { watchButton.requestFocus() }
    }

    SetupFrame(
        title = "Which machines should Mantel watch?",
        lead = pickerSentence(machines.size, searching),
        buttons = {
            if (watchWords != null) {
                SetupButton(watchWords, toCells.focusRequester(watchButton), main = true, onClick = onWatch)
            } else {
                SetupButtonPlaceholder("No machine picked yet")
            }
            SetupButton("Enter an address", toCells.focusRequester(addressButton), onClick = onEnterAddress)
            SetupButton("Look again", toCells, onClick = onLookAgain)
            if (onBack != null) SetupButton("Back", toCells, onClick = onBack)
        },
        side = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                shown.firstOrNull { it.url == inFocus }?.let { MachineDetail(it, watching = it.url in watched, inFocus = cellHasFocus) }
                WhatAMachineNeeds()
            }
        },
    ) {
        PickerComb(
            shown, watched, firstCell, belowCells,
            onFocus = { machine, focused ->
                if (focused) inFocus = machine.url
                if (focused || inFocus == machine.url) cellHasFocus = focused
            },
            onToggle = onToggle,
        )
        if (searching) {
            SpinnerLine("Still looking. $checked of $total addresses checked.")
        } else {
            Text("All $total addresses checked.", color = HiveTokens.Color.textMuted)
        }
        if (fileProblem != null) {
            WarningLine(Warning(ConfigFile.NAME, "was not used: $fileProblem", Severity.Wrong))
        }
    }
}

/**
 * What has to be on a machine before Mantel can watch it. Always on this
 * screen, under the card for the cell in focus, so someone whose machine is
 * not in the comb can see why without leaving setup.
 */
@Composable
private fun WhatAMachineNeeds() {
    SetupCard("Not seeing a machine?") {
        Row(horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s24)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s10)) {
                SetupStep(1, installStep("Install Glances on it."))
                SetupStep(2, "Start it there with its web page on:  $START_GLANCES")
                SetupStep(3, "Choose “Look again”.")
            }
            QrTile("https://$GLANCES_DOCS/", caption = null, size = HelpQrSize)
        }
    }
}

/** A row of four places and a row of three: machines first, then dashed empty cells. */
@Composable
private fun PickerComb(
    machines: List<FoundMachine>,
    watched: Set<String>,
    firstCell: FocusRequester,
    belowCells: FocusRequester,
    onFocus: (FoundMachine, Boolean) -> Unit,
    onToggle: (FoundMachine) -> Unit,
) {
    val hex = HiveTokens.Hex
    val stepX = hex.width + hex.gap
    val stepY = hex.height + hex.rowOverlap
    Box(Modifier.size(HiveTokens.Screen.hiveColumnWidth, hex.height + stepY)) {
        for (place in 0 until MAX_PICKER_CELLS) {
            val secondRow = place >= hex.perRow
            val col = if (secondRow) place - hex.perRow else place
            val at = Modifier.offset(
                x = stepX * col + if (secondRow) hex.oddRowOffset else 0.dp,
                y = if (secondRow) stepY else 0.dp,
            )
            val machine = machines.getOrNull(place)
            if (machine == null) {
                EmptyHexCell(at)
            } else {
                MachineCell(
                    machine = machine,
                    watching = machine.url in watched,
                    modifier = (if (place == 0) at.focusRequester(firstCell) else at).focusProperties { down = belowCells },
                    onFocus = { onFocus(machine, it) },
                    onClick = { onToggle(machine) },
                )
            }
        }
    }
}

/**
 * One machine as a cell. Watching: amber outline, glow, a tick and the word.
 * Not watching: plain outline and dim text. The state is always a word too.
 */
@Composable
private fun MachineCell(
    machine: FoundMachine,
    watching: Boolean,
    modifier: Modifier,
    onFocus: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val c = HiveTokens.Color
    val hex = HiveTokens.Hex
    Box(
        modifier
            .size(hex.width, hex.height)
            .onFocusChanged {
                if (focused != it.isFocused) onFocus(it.isFocused)
                focused = it.isFocused
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            clipPath(hexPath(size, 0f)) {
                drawRect(if (watching) HiveTokens.Color.Cell.base else HiveTokens.Color.Cell.baseEnded)
                if (watching) {
                    drawRect(
                        HiveTokens.Color.Cell.glow,
                        Offset(0f, size.height * (1 - WATCHING_GLOW_LEVEL)),
                        Size(size.width, size.height * WATCHING_GLOW_LEVEL),
                    )
                }
            }
            if (focused) {
                // The ring sits in the gap between cells, 3 px outside this one, so the cell itself does not shrink.
                drawPath(
                    hexPath(size, -(HiveTokens.Focus.offset + HiveTokens.Focus.borderWidth / 2).toPx()),
                    HiveTokens.Focus.color,
                    style = Stroke(HiveTokens.Focus.borderWidth.toPx(), join = StrokeJoin.Round),
                )
            }
            drawPath(
                hexPath(size, hex.strokeWidth.toPx()),
                if (watching) c.amber else HiveTokens.Color.Cell.strokeEnded,
                style = Stroke((if (watching) hex.strokeWidthAlert else hex.strokeWidth).toPx(), join = StrokeJoin.Round),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = CellTextInset),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val stateStyle = HiveType.cellLabelSmallBold.copy(letterSpacing = STATE_LETTER_SPACING_EM.em)
            if (watching) {
                Row(horizontalArrangement = Arrangement.spacedBy(CheckGap), verticalAlignment = Alignment.CenterVertically) {
                    Tick(c.amberSoft)
                    Text("WATCHING", style = stateStyle, color = c.amberSoft, maxLines = 1)
                }
            } else {
                Text("NOT WATCHING", style = stateStyle.copy(letterSpacing = STATE_LETTER_SPACING_TIGHT_EM.em), color = c.textDim, maxLines = 1)
            }
            Text(
                machine.containers.toString(),
                style = HiveType.displayL.copy(lineHeight = NUMBER_LINE_HEIGHT_EM.em),
                color = if (watching) c.textBright else c.textDim,
                maxLines = 1,
            )
            Text(
                machine.name,
                Modifier.widthIn(max = CellNameMaxWidth),
                style = HiveType.cellLabel,
                color = if (watching) c.text else c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (machine.containers == 1) "container" else "containers",
                style = HiveType.cellLabelSmall,
                color = if (watching) c.textMuted else c.textDim,
                maxLines = 1,
            )
        }
    }
}

/** The board's tick, on a 24-unit grid. */
@Composable
private fun Tick(color: Color, size: Dp = CheckSize) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val path = Path().apply {
            moveTo(5 * u, 12.5f * u)
            lineTo(9.5f * u, 17 * u)
            lineTo(19 * u, 7.5f * u)
        }
        drawPath(path, color, style = Stroke(2.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The right column: everything else about the cell in focus, once, for one machine at a time. */
@Composable
private fun MachineDetail(machine: FoundMachine, watching: Boolean, inFocus: Boolean) {
    val c = HiveTokens.Color
    HiveCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = DetailPaddingX, vertical = DetailPaddingY),
            verticalArrangement = Arrangement.spacedBy(DetailGap),
        ) {
            Text(if (inFocus) "The cell in focus" else "The last cell in focus", style = HiveType.bodyBold, color = c.textMuted)
            Text(
                machine.name,
                style = HiveType.displayM.copy(lineHeight = DETAIL_NAME_LINE_HEIGHT_EM.em),
                color = c.textBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Fact("Address", machine.shownAddress)
            Fact("Threads", machine.threads.toString())
            Fact("Memory", "${machine.memoryGb} GB")
            Fact("Containers", machine.containers.toString())
            Text(
                when {
                    !inFocus -> if (watching) "Watching." else "Not watching."
                    watching -> "Watching. Press OK to stop."
                    else -> "Not watching. Press OK to watch."
                },
                Modifier.padding(top = DetailLastLineTop),
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = HiveTokens.Color.textMuted)
        Text(value, style = HiveType.bodyBold)
    }
}
