package com.blayter.mantel.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.blayter.mantel.data.github.GitHubAccount
import com.blayter.mantel.data.github.GitHubSignInClient
import com.blayter.mantel.data.glances.GlancesScanner
import com.blayter.mantel.data.glances.ProbeResult
import com.blayter.mantel.data.glances.ownNetworkAddress
import com.blayter.mantel.domain.AddressEntry
import com.blayter.mantel.domain.FoundMachine
import com.blayter.mantel.domain.networkPrefix
import com.blayter.mantel.domain.scanAddresses
import com.blayter.mantel.domain.sortedForPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** With no machine found, the search repeats by itself this often. */
private val LOOK_AGAIN_EVERY = 10.seconds

private enum class Stage { Machines, Deploys, Ready }

/**
 * First-run setup on the TV. Step "machines": look for Glances on the TV's
 * own network, let the person pick which machines to watch, and take an
 * address by number pad for one the search cannot reach. Step "deploys":
 * an optional GitHub sign-in with a code typed on a phone. Then "Ready".
 *
 * From settings it is opened with [onMachines]: only the machines step, with
 * the machines already watched ticked ([watchedAlready]) and a way back
 * ([onCancel]).
 *
 * It only reads from the network and asks GitHub for permission to read, and
 * [onDone] only changes what this display stores. A pushed config file still takes over at any moment; that
 * is handled by the activity, which shows [fileProblem] when one is rejected.
 */
@Composable
fun OnTvSetup(
    fileProblem: String?,
    scanner: GlancesScanner = remember { GlancesScanner() },
    gitHub: GitHubSignInClient = remember { GitHubSignInClient() },
    watchedAlready: Set<String> = emptySet(),
    onMachines: ((watched: Set<String>, pickerOrder: List<String>) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDone: (List<FoundMachine>, GitHubAccount?) -> Unit = { _, _ -> },
) {
    var step by remember { mutableStateOf(Stage.Machines) }
    // Kept when going back to the machines, so nobody signs in twice.
    var account by remember { mutableStateOf<GitHubAccount?>(null) }
    var continueWithoutRepos by remember { mutableStateOf(false) }
    val found = remember { mutableStateMapOf<String, FoundMachine>() }
    var watched by remember { mutableStateOf(watchedAlready) }
    // Once the person has picked anything, nothing is picked for them.
    var touched by remember { mutableStateOf(watchedAlready.isNotEmpty()) }
    var ownAddress by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(true) }
    var searchedOnce by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var focusWatch by remember { mutableIntStateOf(0) }
    var entry by remember { mutableStateOf<AddressEntry?>(null) }
    var check by remember { mutableStateOf<AddressCheck>(AddressCheck.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(round) {
        searching = true
        checked = 0
        ownAddress = ownNetworkAddress()
        val addresses = ownAddress?.let(::scanAddresses).orEmpty()
        total = addresses.size
        scanner.scan(addresses, onChecked = { checked = it }, onFound = { found[it.url] = it })
        searching = false
        searchedOnce = true
        if (found.size == 1 && !touched) {
            // Exactly one answered: it is already watched, and the main button has the focus.
            watched = found.keys.toSet()
            focusWatch++
        }
        if (found.isEmpty()) {
            delay(LOOK_AGAIN_EVERY)
            round++
        }
    }

    val picked = sortedForPicker(found.values).filter { it.url in watched }
    val signedIn = account
    val typing = entry
    when {
        step == Stage.Deploys && signedIn == null -> GitHubSetup(
            client = gitHub,
            onSignedIn = {
                account = it
                continueWithoutRepos = false
                if (it.repos.isNotEmpty()) step = Stage.Ready
            },
            onSkip = { step = Stage.Ready },
            onBack = { step = Stage.Machines },
        )
        step == Stage.Deploys && signedIn != null -> NoRepositoriesScreen(
            account = signedIn,
            client = gitHub,
            onRepositories = {
                account = signedIn.copy(repos = it)
                step = Stage.Ready
            },
            onContinue = {
                continueWithoutRepos = true
                step = Stage.Ready
            },
            // Only this display forgets the sign-in; nothing changes on GitHub.
            onSignOut = { account = null },
        )
        step == Stage.Ready -> ReadyScreen(
            machines = picked,
            account = signedIn,
            onStart = { onDone(picked, signedIn) },
            onBack = { step = Stage.Machines },
        )
        typing != null -> AddressScreen(
            entry = typing,
            check = check,
            onKey = {
                entry = typing.press(it)
                check = AddressCheck.Idle
            },
            onDelete = {
                entry = typing.delete()
                check = AddressCheck.Idle
            },
            onCheck = {
                val port = typing.portNumber
                if (typing.isComplete && port != null && check != AddressCheck.Checking) {
                    check = AddressCheck.Checking
                    scope.launch {
                        when (val result = scanner.probe(typing.address, port)) {
                            is ProbeResult.Found -> {
                                found[result.machine.url] = result.machine
                                watched = watched + result.machine.url
                                touched = true
                                check = AddressCheck.Idle
                                entry = null
                            }
                            ProbeResult.NoAnswer -> check = AddressCheck.NoAnswer(typing.address, port)
                            ProbeResult.NeedsPassword -> check = AddressCheck.NeedsPassword(typing.address)
                        }
                    }
                }
            },
            onChangeFirstPart = {
                entry = typing.changeFirstPart()
                check = AddressCheck.Idle
            },
            onTogglePort = {
                entry = typing.togglePort()
                check = AddressCheck.Idle
            },
            onBack = {
                entry = null
                check = AddressCheck.Idle
            },
        )
        found.isEmpty() && searchedOnce -> NoMachinesScreen(
            scanned = total,
            fileProblem = fileProblem,
            onLookAgain = { round++ },
            onEnterAddress = { entry = AddressEntry(prefix = ownAddress?.let(::networkPrefix).orEmpty()) },
            onBack = onCancel,
        )
        else -> MachinePickerScreen(
            machines = sortedForPicker(found.values),
            watched = watched,
            searching = searching,
            checked = checked,
            total = total,
            fileProblem = fileProblem,
            focusWatch = focusWatch,
            onToggle = {
                touched = true
                watched = if (it.url in watched) watched - it.url else watched + it.url
            },
            onBack = onCancel,
            onWatch = {
                if (onMachines != null) {
                    onMachines(watched, sortedForPicker(found.values).map { it.url })
                } else if (picked.isNotEmpty()) {
                    step = when {
                        signedIn == null -> Stage.Deploys
                        signedIn.repos.isEmpty() && !continueWithoutRepos -> Stage.Deploys
                        else -> Stage.Ready
                    }
                }
            },
            onEnterAddress = { entry = AddressEntry(prefix = ownAddress?.let(::networkPrefix).orEmpty()) },
            onLookAgain = { round++ },
        )
    }
}
