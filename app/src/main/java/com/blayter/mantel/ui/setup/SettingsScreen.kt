package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.domain.readyLines
import com.blayter.mantel.ui.theme.HiveTheme
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import com.blayter.mantel.ui.theme.defaultThemeFor
import com.blayter.mantel.ui.theme.layoutsOf
import com.blayter.mantel.ui.theme.themesFor

// Sizes from the "Settings" board that are not in tokens.json.
private val RowPaddingX = 18.dp
private val RowPaddingY = 12.dp
private val RowGap = 14.dp
private val CardButtonsTop = 6.dp

// Not designed: the label column of the look card's two rows of buttons.
private val ChoiceLabelWidth = 120.dp

// Where the right-hand card starts so it lines up with the left one, under a two-line lead. See ReadyScreen.
private val SideCardTop = 207.dp
private const val LEAD_LINES = 2

/** One watched machine, as settings lists it. */
data class SettingsMachine(
    /** Its Glances address as stored; what "Remove" removes. */
    val url: String,
    /** What it calls itself, or its address until it has said. */
    val name: String,
    val address: String,
    /** "has not answered for 4 min", or null while it is answering. */
    val notAnswering: String?,
)

/** Where the deploys card gets its runs from. */
sealed interface SettingsDeploys {
    /** Signed in on the TV. [repos] as `owner/name`. */
    data class SignedIn(val login: String, val repos: List<String>) : SettingsDeploys

    /** A token from a pushed config file. */
    data class FromFile(val repos: List<String>) : SettingsDeploys

    /** A token was given but this TV could not encrypt it. */
    data object NotStored : SettingsDeploys

    /** Nothing: the card shows the sample. */
    data object None : SettingsDeploys
}

/**
 * Settings, opened from the dashboard with the Menu button or by holding OK.
 * Machines on the left, deploys on the right, the same split as the
 * dashboard. Nothing here changes a machine or GitHub: it only changes what
 * this display stores. "Remove", "Sign out" and "Start over" ask once.
 * Choosing a layout or theme changes the screen at once and needs no
 * confirm: it is one press to go back.
 */
@Composable
fun SettingsScreen(
    machines: List<SettingsMachine>,
    deploys: SettingsDeploys,
    onRemove: (SettingsMachine) -> Unit,
    onAddMachine: () -> Unit,
    onChooseRepositories: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onStartOver: () -> Unit,
    onBack: () -> Unit,
    themes: List<HiveTheme> = emptyList(),
    theme: HiveTheme? = null,
    onTheme: (HiveTheme) -> Unit = {},
) {
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
    BackHandler(onBack = onBack)

    SetupFrame(
        title = "Settings",
        lead = "Nothing here changes a machine or GitHub. It only changes what this display watches.",
        subtitle = SETTINGS_SUBTITLE,
        leadMinLines = LEAD_LINES,
        buttons = {
            SetupButton("Back to the display", Modifier.focusRequester(back), main = true, onClick = onBack)
            ConfirmButton("Start over", "OK again to forget everything", onConfirmed = onStartOver)
        },
        side = {
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = SideCardTop),
                verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20),
            ) {
                DeploysSettings(deploys, onChooseRepositories, onSignIn, onSignOut)
            }
        },
    ) {
        SetupCard("Machines") {
            // The last machine cannot be removed: a display with none is "Start over".
            for (machine in machines) MachineRow(machine, removable = machines.size > 1) { onRemove(machine) }
            Row(Modifier.padding(top = CardButtonsTop)) {
                SetupButton("Add a machine", onClick = onAddMachine)
            }
        }
        if (theme != null && themes.size > 1) ThemeSettings(themes, theme, onTheme)
    }
}

/**
 * The look card: which layout the main screen uses, and which of that
 * layout's color themes. Choosing a layout opens it on its own theme. What is
 * in use is the filled button and is named in the heading too, so the choice
 * is never shown by color alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettings(themes: List<HiveTheme>, theme: HiveTheme, onTheme: (HiveTheme) -> Unit) {
    val layouts = layoutsOf(themes)
    val mine = themesFor(theme.layout, themes)
    SetupCard("Look · ${theme.layout.label} layout · ${theme.name} theme") {
        Quiet(theme.description)
        if (layouts.size > 1) {
            ChoiceRow("Layout") {
                for (layout in layouts) {
                    SetupButton(layout.label, main = layout == theme.layout) {
                        if (layout != theme.layout) defaultThemeFor(layout, themes)?.let(onTheme)
                    }
                }
            }
        }
        if (mine.size > 1) {
            ChoiceRow("Theme") {
                for (each in mine) SetupButton(each.name, main = each.id == theme.id) { onTheme(each) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, buttons: @Composable () -> Unit) {
    Row(Modifier.padding(top = CardButtonsTop), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(ChoiceLabelWidth), style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
            verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
        ) { buttons() }
    }
}

/** A machine's line. With more than one machine it is a focus stop, and OK twice removes it. */
@Composable
private fun MachineRow(machine: SettingsMachine, removable: Boolean, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    val stop = if (removable) {
        Modifier
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) armed = false
            }
            .focusRing(focused)
    } else {
        Modifier
    }
    Row(
        stop
            .fillMaxWidth()
            .clip(shape)
            .background(HiveTokens.Color.ground)
            .border(HiveTokens.Card.borderWidth, HiveTokens.Color.surfaceBorder, shape)
            .then(
                if (removable) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (armed) onRemove() else armed = true
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = RowPaddingX, vertical = RowPaddingY),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetupTick()
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(machine.name) }
                withStyle(SpanStyle(color = HiveTokens.Color.textMuted)) {
                    if (machine.address != machine.name) append(" · ${machine.address}")
                    append(" · ")
                }
                if (machine.notAnswering != null) {
                    withStyle(SpanStyle(color = HiveTokens.Color.Status.trouble, fontWeight = FontWeight.Bold)) {
                        append(machine.notAnswering)
                    }
                } else {
                    withStyle(SpanStyle(color = HiveTokens.Color.textMuted)) { append("answering") }
                }
            },
            Modifier.weight(1f),
            color = HiveTokens.Color.textBright,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (removable) {
            Text(
                if (armed) "OK again to remove" else "Remove",
                style = HiveType.bodyBold,
                color = if (armed) HiveTokens.Color.Status.trouble else HiveTokens.Color.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeploysSettings(
    deploys: SettingsDeploys,
    onChooseRepositories: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (deploys) {
        is SettingsDeploys.SignedIn -> SetupCard("Deploys · signed in as ${deploys.login}") {
            RepoLines(deploys.repos)
            Quiet(
                if (deploys.repos.isEmpty()) {
                    "No repositories are chosen yet. They are chosen on GitHub, not here."
                } else {
                    "Repositories are chosen on GitHub, not here."
                },
            )
            Row(Modifier.padding(top = CardButtonsTop), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
                SetupButton("Choose repositories", onClick = onChooseRepositories)
                ConfirmButton("Sign out of GitHub", "OK again to sign out", onConfirmed = onSignOut)
            }
        }
        is SettingsDeploys.FromFile -> SetupCard("Deploys · from the config file") {
            RepoLines(deploys.repos)
            Quiet("The token and this list came in the pushed config file. Signing in here replaces them.")
            Row(Modifier.padding(top = CardButtonsTop)) { SetupButton("Sign in to GitHub", onClick = onSignIn) }
        }
        SettingsDeploys.NotStored -> SetupCard("Deploys · token not stored") {
            Quiet("This TV could not encrypt the token from the config file, so Mantel did not keep it.")
            Row(Modifier.padding(top = CardButtonsTop)) { SetupButton("Sign in to GitHub", onClick = onSignIn) }
        }
        SettingsDeploys.None -> SetupCard("Deploys · not set up") {
            Quiet("The deploys card shows sample runs. Sign in to show your GitHub Actions runs; Mantel can only read them.")
            Row(Modifier.padding(top = CardButtonsTop)) { SetupButton("Sign in to GitHub", onClick = onSignIn) }
        }
    }
}

@Composable
private fun RepoLines(repos: List<String>) {
    val (shown, more) = readyLines(repos)
    for (repo in shown) TickedLine(repo)
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
