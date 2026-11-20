package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.github.GitHubAccount
import com.blayter.mantel.domain.FoundMachine
import com.blayter.mantel.domain.deploysHeading
import com.blayter.mantel.domain.readyLines
import com.blayter.mantel.domain.readySentence
import com.blayter.mantel.domain.watchingHeading
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Where the right-hand card starts so it lines up with the left one, under a two-line lead.
// The approved "Setup · ready" board says 202; the built title is 5 taller.
private val SideCardTop = 207.dp
private const val LEAD_LINES = 2

/**
 * Setup, "Ready": what the display is about to show, machines on the left and
 * deploys on the right, the same split as the dashboard. Nothing starts on a
 * timer; "Start" stores the choices on this display and nothing else.
 *
 * @param account the GitHub sign-in, or null when deploys were skipped.
 */
@Composable
fun ReadyScreen(
    machines: List<FoundMachine>,
    account: GitHubAccount?,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    BackHandler(onBack = onBack)

    SetupFrame(
        title = "Ready to start",
        lead = readySentence(machines.map { it.name }, liveDeploys = account != null && account.repos.isNotEmpty()),
        subtitle = SETUP_READY,
        leadMinLines = LEAD_LINES,
        buttons = {
            SetupButton("Start", Modifier.focusRequester(start), main = true, onClick = onStart)
            SetupButton("Back", onClick = onBack)
        },
        side = {
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = SideCardTop),
                verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20),
            ) {
                when {
                    account == null -> {
                        SetupCard("No deploys for now") {
                            Quiet("You skipped GitHub, so the deploys card shows sample runs, and the screen says so.")
                        }
                    }
                    account.repos.isEmpty() -> {
                        SetupCard(deploysHeading(0, account.login)) {
                            Quiet("Runs appear by themselves once you choose repositories for Mantel on GitHub.")
                        }
                    }
                    else -> {
                        SetupCard(deploysHeading(account.repos.size, account.login)) {
                            TickedList(account.repos)
                        }
                        Quiet("To add or remove repositories, change what Mantel can read in your GitHub settings.")
                    }
                }
            }
        },
    ) {
        SetupCard(watchingHeading(machines.size)) {
            val (shown, more) = readyLines(machines.map { it.url })
            for (machine in machines.take(shown.size)) TickedLine(machine.name, machine.shownAddress)
            if (more > 0) Quiet("and $more more")
        }
    }
}

@Composable
private fun TickedList(names: List<String>) {
    val (shown, more) = readyLines(names)
    for (name in shown) TickedLine(name)
    if (more > 0) Quiet("and $more more")
}

@Composable
private fun Quiet(text: String) {
    Text(
        text,
        style = HiveType.body.copy(lineHeight = SETUP_CARD_LINE_HEIGHT_EM.em),
        color = HiveTokens.Color.textMuted,
    )
}
