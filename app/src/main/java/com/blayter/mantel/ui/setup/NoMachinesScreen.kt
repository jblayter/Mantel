package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.config.ConfigFile
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.ui.hive.WarningLine
import com.blayter.mantel.ui.panel.HiveCard
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved "Setup · no machines" board that are not in tokens.json.

/** The one short command that starts Glances with its web page on. */
internal const val START_GLANCES = "glances -w"

/** The official Glances documentation, which has the install steps. As a person would type it. */
internal const val GLANCES_DOCS = "nicolargo.github.io/glances"

/**
 * Setup, "No machines answered". Says what Glances is and how to start it,
 * and keeps looking by itself.
 *
 * The QR code and the address in step 1 are the official Glances
 * documentation, which has the install steps (the owner's choice).
 *
 * @param scanned how many addresses were asked; zero when the TV has no network address.
 */
@Composable
fun NoMachinesScreen(
    scanned: Int,
    fileProblem: String?,
    onLookAgain: () -> Unit,
    onEnterAddress: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val lookAgain = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { lookAgain.requestFocus() } }
    val glances = "Mantel reads from Glances, a small free program that runs on the machine you want to watch."

    SetupFrame(
        title = "No machines answered",
        lead = if (scanned > 0) {
            "$glances None of the $scanned addresses on this network answered as Glances."
        } else {
            "$glances This TV has no network address yet, so there was nowhere to look."
        },
        note = "Already running it? It may be on another network or port. Enter its address instead.",
        buttons = {
            SetupButton("Look again now", Modifier.focusRequester(lookAgain), main = true, onClick = onLookAgain)
            SetupButton("Enter an address", onClick = onEnterAddress)
            if (onBack != null) SetupButton("Back", onClick = onBack)
        },
        side = { QrTile("https://$GLANCES_DOCS/", GLANCES_DOCS) },
    ) {
        SetupCard("To start Glances") {
            SetupStep(1, installStep("Install Glances on the machine you want to watch."))
            SetupStep(2, "Start it there with its web page on:  $START_GLANCES")
            SetupStep(3, "Leave this screen on. The machine appears here by itself.")
        }
        SpinnerLine("Looking again every 10 seconds.")
        if (fileProblem != null) {
            WarningLine(Warning(ConfigFile.NAME, "was not used: $fileProblem", Severity.Wrong))
        }
    }
}

/** Step 1 of "how to start Glances": what to do, and where the steps are. */
internal fun installStep(what: String): AnnotatedString = buildAnnotatedString {
    append("$what Scan the code, or open ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(GLANCES_DOCS) }
}
