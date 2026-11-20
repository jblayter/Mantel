package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.domain.AddressEntry
import com.blayter.mantel.domain.AddressField
import com.blayter.mantel.domain.GLANCES_PORT
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.ui.hive.WarningLine
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved "Setup · enter an address" boards that are not in tokens.json.
private val FieldPaddingY = 18.dp
private val CaretWidth = 3.dp
private val CaretHeight = 50.dp
private val PortCaretHeight = 26.dp
private val CaretGap = 4.dp
private val KeyWidth = 140.dp
private val KeyHeight = 100.dp
private val KeyGap = 14.dp
private val AdviceIndent = 42.dp
private const val FIELD_LINE_HEIGHT_EM = 1.1f
private const val FIRST_FOCUS_KEY = '5'
private val PadRows = listOf("123", "456", "789")

/** What happened to the address last asked. */
sealed interface AddressCheck {
    data object Idle : AddressCheck
    data object Checking : AddressCheck
    data class NoAnswer(val address: String, val port: Int) : AddressCheck
    data class NeedsPassword(val address: String) : AddressCheck
}

/**
 * Setup, "Enter the machine's address": a number pad for a machine the
 * search cannot reach. Nothing long is typed; to begin with only the last
 * number is asked for.
 */
@Composable
fun AddressScreen(
    entry: AddressEntry,
    check: AddressCheck,
    onKey: (Char) -> Unit,
    onDelete: () -> Unit,
    onCheck: () -> Unit,
    onChangeFirstPart: () -> Unit,
    onTogglePort: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    val failed = check is AddressCheck.NoAnswer || check is AddressCheck.NeedsPassword
    // Stated outright: the pad is up and to the right of the buttons, where the remote would not look.
    val toPad = Modifier.focusProperties { up = firstKey }

    SetupFrame(
        title = "Enter the machine’s address",
        lead = if (entry.prefix.isNotEmpty()) {
            "Only the last number is needed. The first part is this TV’s own network."
        } else {
            "Type the whole address, with a dot between the numbers."
        },
        buttons = {
            SetupButton(if (failed) "Check again" else "Check this address", toPad, main = true, onClick = onCheck)
            if (entry.prefix.isNotEmpty()) SetupButton("Change first part", toPad, onClick = onChangeFirstPart)
            SetupButton(if (entry.field == AddressField.Port) "Back to address" else "Change port", toPad, onClick = onTogglePort)
            SetupButton("Back", toPad, onClick = onBack)
        },
        side = { NumberPad(firstKey, onKey, onDelete) },
    ) {
        AddressField(entry, trouble = failed)
        when (check) {
            AddressCheck.Idle -> Text(
                if (entry.field == AddressField.Port) {
                    "Type the port, then go back to the address."
                } else {
                    "Glances answers on port $GLANCES_PORT unless you changed it."
                },
                color = HiveTokens.Color.textMuted,
            )
            AddressCheck.Checking -> SpinnerLine("Asking ${entry.address} on port ${entry.port}.")
            is AddressCheck.NoAnswer -> Trouble(
                Warning(check.address, "did not answer on port ${check.port}.", Severity.Wrong),
                "Check that Glances is running there with its web page on, and that the machine is on the " +
                    "same network as this TV.",
            )
            is AddressCheck.NeedsPassword -> Trouble(
                Warning(check.address, "asks for a password.", Severity.Wrong),
                "Mantel cannot give a password yet. To watch this machine, start Glances there without one.",
            )
        }
    }
}

@Composable
private fun Trouble(warning: Warning, advice: String) {
    WarningLine(warning)
    Text(
        advice,
        Modifier.padding(start = AdviceIndent),
        style = HiveType.body.copy(lineHeight = SETUP_CARD_LINE_HEIGHT_EM.em),
        color = HiveTokens.Color.textSecondary,
    )
}

/** The address so far, with the caret where the pad is typing. Coral border when nothing answered. */
@Composable
private fun AddressField(entry: AddressEntry, trouble: Boolean) {
    val c = HiveTokens.Color
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    val onPort = entry.field == AddressField.Port
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(HiveTokens.Card.borderWidth, if (trouble) HiveTokens.Color.Status.trouble else c.surfaceBorder, shape)
            .padding(horizontal = HiveTokens.Card.paddingX, vertical = FieldPaddingY),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val style = HiveType.displayL.copy(lineHeight = FIELD_LINE_HEIGHT_EM.em)
            // A space keeps the field its full height before anything is typed.
            Text(entry.prefix.ifEmpty { if (entry.typed.isEmpty()) " " else "" }, style = style, color = c.textDim)
            Text(entry.typed, style = style, color = c.textBright)
            if (!onPort && !trouble) Caret(CaretHeight)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("port ", color = c.textMuted)
            Text(entry.port, style = if (onPort) HiveType.bodyBold else HiveType.body, color = if (onPort) c.textBright else c.textMuted)
            if (onPort) Caret(PortCaretHeight)
        }
    }
}

@Composable
private fun Caret(height: Dp) {
    Box(Modifier.padding(start = CaretGap).size(CaretWidth, height).background(HiveTokens.Color.amber))
}

/** Digits, a dot and Delete. */
@Composable
private fun NumberPad(firstKey: FocusRequester, onKey: (Char) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        for (row in PadRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (key in row) {
                    PadKey(key.toString(), if (key == FIRST_FOCUS_KEY) Modifier.focusRequester(firstKey) else Modifier) { onKey(key) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            PadKey(".") { onKey('.') }
            PadKey("0") { onKey('0') }
            PadKey("Delete", word = true, onClick = onDelete)
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier = Modifier, word: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    Box(
        modifier
            .size(KeyWidth, KeyHeight)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(HiveTokens.Card.borderWidth, HiveTokens.Color.surfaceBorder, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (word) {
            Text(label, style = HiveType.bodyBold, color = HiveTokens.Color.textSecondary)
        } else {
            Text(label, style = HiveType.displayM.copy(lineHeight = 1.em), color = HiveTokens.Color.textBright)
        }
    }
}
