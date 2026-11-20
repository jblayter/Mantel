package com.blayter.mantel.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.github.DeviceCode
import com.blayter.mantel.data.github.GitHubAccount
import com.blayter.mantel.data.github.GitHubApp
import com.blayter.mantel.data.github.GitHubSignInClient
import com.blayter.mantel.data.github.GitHubToken
import com.blayter.mantel.data.github.PollAnswer
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.domain.codeTimeLeft
import com.blayter.mantel.domain.repositoriesNow
import com.blayter.mantel.ui.hive.WarningLine
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// Sizes from the approved "Setup · deploys" boards that are not in tokens.json.
private val CodeTop = 6.dp
private const val CODE_LETTER_SPACING_EM = 0.02f

/** After a failure that may pass (no network), and while no repository is chosen, ask again this often. */
private val ASK_AGAIN_EVERY = 5.seconds

private const val DEPLOYS_TITLE = "Show your deploys too?"
private const val DEPLOYS_LEAD = "Optional. Mantel can show GitHub Actions runs beside your machines. It can only read them."

private sealed interface SignInPhase {
    /** Asking GitHub for a code. */
    data object Asking : SignInPhase

    /** GitHub did not give one. */
    data object NoCode : SignInPhase

    data class Waiting(val code: DeviceCode) : SignInPhase

    /** The code ran out, or was refused on GitHub. */
    data class Dead(val userCode: String, val refused: Boolean) : SignInPhase

    /** Approved; reading who it is and what they let Mantel read. */
    data object Reading : SignInPhase
}

/**
 * Setup, step "deploys": sign in to GitHub with a code typed on a phone.
 * Optional, and skipping is where the focus starts; opened from settings
 * there is nothing to skip ([onSkip] is null), only "Back". It only asks GitHub for
 * permission to read; nothing here can change a repository or a workflow.
 */
@Composable
fun GitHubSetup(
    client: GitHubSignInClient,
    onSignedIn: (GitHubAccount) -> Unit,
    onSkip: (() -> Unit)?,
    onBack: () -> Unit,
    title: String = DEPLOYS_TITLE,
    lead: String = DEPLOYS_LEAD,
    subtitle: String = SETUP_STEP_DEPLOYS,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf<SignInPhase>(SignInPhase.Asking) }
    var secondsLeft by remember { mutableLongStateOf(0L) }
    val first = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(phase::class) { runCatching { first.requestFocus() } }

    LaunchedEffect(attempt) {
        phase = SignInPhase.Asking
        val code = try {
            client.requestCode()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase = SignInPhase.NoCode
            return@LaunchedEffect
        }
        secondsLeft = code.expiresInSeconds
        phase = SignInPhase.Waiting(code)
        val started = TimeSource.Monotonic.markNow()
        var interval = code.intervalSeconds
        var nextAsk = interval
        while (true) {
            delay(1.seconds)
            val gone = started.elapsedNow().inWholeSeconds
            secondsLeft = code.expiresInSeconds - gone
            if (secondsLeft <= 0) {
                phase = SignInPhase.Dead(code.userCode, refused = false)
                return@LaunchedEffect
            }
            if (gone < nextAsk) continue
            val answer = try {
                client.poll(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // GitHub could not be reached this time. The code is still good.
                PollAnswer.Pending
            }
            when (answer) {
                is PollAnswer.Approved -> {
                    phase = SignInPhase.Reading
                    onSignedIn(readAccount(client, answer.token))
                    return@LaunchedEffect
                }
                PollAnswer.Pending -> {}
                is PollAnswer.SlowDown -> interval = answer.intervalSeconds ?: (interval + 5)
                PollAnswer.Expired -> {
                    phase = SignInPhase.Dead(code.userCode, refused = false)
                    return@LaunchedEffect
                }
                PollAnswer.Denied -> {
                    phase = SignInPhase.Dead(code.userCode, refused = true)
                    return@LaunchedEffect
                }
                is PollAnswer.Failed -> {
                    phase = SignInPhase.NoCode
                    return@LaunchedEffect
                }
            }
            nextAsk = started.elapsedNow().inWholeSeconds + interval
        }
    }

    val current = phase
    val dead = current is SignInPhase.Dead || current == SignInPhase.NoCode
    SetupFrame(
        title = title,
        lead = lead,
        subtitle = subtitle,
        buttons = {
            if (dead) {
                SetupButton(
                    if (current == SignInPhase.NoCode) "Try again" else "Get a new code",
                    Modifier.focusRequester(first),
                    main = true,
                ) { attempt++ }
                if (onSkip != null) SetupButton("Skip, no deploys for now", onClick = onSkip)
                SetupButton("Back", onClick = onBack)
            } else if (onSkip != null) {
                SetupButton("Skip, no deploys for now", Modifier.focusRequester(first), onClick = onSkip)
                SetupButton("Back", onClick = onBack)
            } else {
                SetupButton("Back", Modifier.focusRequester(first), onClick = onBack)
            }
        },
        side = {
            when (current) {
                is SignInPhase.Waiting ->
                    QrTile(current.code.verificationUri, current.code.verificationUri.removePrefix("https://"))
                is SignInPhase.Dead -> EmptyQrTile("No code to scan until you get a new one")
                SignInPhase.Reading -> EmptyQrTile("Approved")
                else -> EmptyQrTile("No code to scan yet")
            }
        },
    ) {
        when (current) {
            SignInPhase.Asking -> SpinnerLine("Asking GitHub for a code.")
            SignInPhase.NoCode -> WarningLine(
                Warning("GitHub did not give a code.", "Check that this TV is online, then try again.", Severity.Wrong),
            )
            is SignInPhase.Waiting -> {
                SetupCard("On your phone or computer") {
                    SetupStep(
                        1,
                        buildAnnotatedString {
                            append("Scan the code, or open ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(current.code.verificationUri.removePrefix("https://"))
                            }
                        },
                    )
                    SetupStep(2, "Enter this code and approve.")
                    Spacer(Modifier.height(CodeTop))
                    CodeText(current.code.userCode, alive = true)
                }
                SpinnerLine("Waiting for you to approve on GitHub. ${codeTimeLeft(secondsLeft)}")
            }
            is SignInPhase.Dead -> {
                SetupCard("This code can no longer be used") {
                    Spacer(Modifier.height(CodeTop))
                    CodeText(current.userCode, alive = false)
                }
                WarningLine(
                    if (current.refused) {
                        Warning("The request was cancelled", "on GitHub. A new code starts again.", Severity.Wrong)
                    } else {
                        Warning("The code ran out", "before it was approved. Codes last 15 minutes.", Severity.Wrong)
                    },
                )
            }
            SignInPhase.Reading -> SpinnerLine("Approved. Reading what Mantel may show.")
        }
    }
}

/** The code, large enough to read across the room. A dead one stays, dim and struck through. */
@Composable
private fun CodeText(code: String, alive: Boolean) {
    Text(
        code,
        style = HiveType.displayXl.copy(
            letterSpacing = CODE_LETTER_SPACING_EM.em,
            lineHeight = 1.em,
            textDecoration = if (alive) null else TextDecoration.LineThrough,
        ),
        color = if (alive) HiveTokens.Color.textBright else HiveTokens.Color.textDim,
        maxLines = 1,
    )
}

/** Who signed in and what they let Mantel read. Keeps asking until GitHub answers. */
private suspend fun readAccount(client: GitHubSignInClient, token: GitHubToken): GitHubAccount {
    while (true) {
        try {
            return GitHubAccount(token, client.login(token.accessToken), client.repositories(token.accessToken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            delay(ASK_AGAIN_EVERY)
        }
    }
}

/**
 * Setup, "No repositories": signed in, but the person has not yet chosen any
 * repository for Mantel on GitHub. Keeps checking by itself.
 */
@Composable
fun NoRepositoriesScreen(
    account: GitHubAccount,
    client: GitHubSignInClient,
    onRepositories: (List<String>) -> Unit,
    onContinue: () -> Unit,
    onSignOut: () -> Unit,
    subtitle: String = SETUP_STEP_DEPLOYS,
) {
    var round by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onSignOut)

    LaunchedEffect(round) {
        while (true) {
            try {
                val repos = client.repositories(account.token.accessToken)
                if (repos.isNotEmpty()) {
                    onRepositories(repos)
                    return@LaunchedEffect
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No answer this time; the next round asks again.
            }
            delay(ASK_AGAIN_EVERY)
        }
    }

    SetupFrame(
        title = "Mantel can’t read any repositories yet",
        lead = "You are signed in as ${account.login}. GitHub lets you choose which repositories Mantel may read, " +
            "and none are chosen.",
        subtitle = subtitle,
        buttons = {
            SetupButton("Check again now", Modifier.focusRequester(first), main = true) { round++ }
            SetupButton("Continue without deploys", onClick = onContinue)
            SetupButton("Sign out", onClick = onSignOut)
        },
        side = { QrTile("https://${GitHubApp.PAGE}", GitHubApp.PAGE) },
    ) {
        SetupCard("On your phone or computer") {
            SetupStep(
                1,
                buildAnnotatedString {
                    append("Scan the code, or open ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(GitHubApp.PAGE) }
                },
            )
            SetupStep(2, "Choose “Install” or “Configure”, then pick the repositories to show.")
            SetupStep(3, "Leave this screen on. They appear here by themselves.")
        }
        SpinnerLine("Checking again every few seconds.")
    }
}

/**
 * Signing in to GitHub from settings: the same screens as setup's step
 * "deploys", without "Skip". [onDone] gets the sign-in, with whatever
 * repositories are chosen so far.
 */
@Composable
fun GitHubSignInFromSettings(client: GitHubSignInClient, onDone: (GitHubAccount) -> Unit, onCancel: () -> Unit) {
    var account by remember { mutableStateOf<GitHubAccount?>(null) }
    val signedIn = account
    if (signedIn == null) {
        GitHubSetup(
            client = client,
            onSignedIn = { if (it.repos.isNotEmpty()) onDone(it) else account = it },
            onSkip = null,
            onBack = onCancel,
            title = "Sign in to GitHub",
            lead = "Mantel can show GitHub Actions runs beside your machines. It can only read them.",
            subtitle = SETTINGS_SUBTITLE,
        )
    } else {
        NoRepositoriesScreen(
            account = signedIn,
            client = client,
            onRepositories = { onDone(signedIn.copy(repos = it)) },
            onContinue = { onDone(signedIn) },
            onSignOut = { account = null },
            subtitle = SETTINGS_SUBTITLE,
        )
    }
}

/**
 * Settings, "Choose repositories": the QR code of the app's page on GitHub,
 * where the owner picks what Mantel may read. Nothing is chosen on the TV.
 * While it is open the display asks GitHub for the list every few seconds.
 *
 * @param repos what Mantel reads now, as `owner/name`.
 * @param onCheck asks GitHub for the list now.
 */
@Composable
fun RepositoriesScreen(login: String, repos: List<String>, onCheck: () -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        while (true) {
            onCheck()
            delay(ASK_AGAIN_EVERY)
        }
    }

    SetupFrame(
        title = "Choose repositories on GitHub",
        lead = "You are signed in as $login. Which repositories Mantel may read is chosen on GitHub, not here.",
        subtitle = SETTINGS_SUBTITLE,
        buttons = { SetupButton("Back to settings", Modifier.focusRequester(first), main = true, onClick = onBack) },
        side = { QrTile("https://${GitHubApp.PAGE}", GitHubApp.PAGE) },
    ) {
        SetupCard("On your phone or computer") {
            SetupStep(
                1,
                buildAnnotatedString {
                    append("Scan the code, or open ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(GitHubApp.PAGE) }
                },
            )
            SetupStep(2, "Choose “Install” or “Configure”, then pick the repositories to show.")
            SetupStep(3, "Leave this screen on. The change shows here by itself.")
        }
        SpinnerLine("${repositoriesNow(repos.size)} Checking again every few seconds.")
    }
}
