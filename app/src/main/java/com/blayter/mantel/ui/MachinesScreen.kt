package com.blayter.mantel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.machineSummary
import com.blayter.mantel.domain.nextTurn
import com.blayter.mantel.domain.stepMachine
import com.blayter.mantel.domain.turnLength
import com.blayter.mantel.ui.panel.MachineShelf
import com.blayter.mantel.ui.theme.HiveTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * The dashboard for several machines: one on screen at a time, taking turns,
 * with every machine on the shelf underneath. All machines are polled the
 * whole time, so each one's trends are complete when its turn comes.
 *
 * Nobody needs to touch it. With the remote, left and right on the shelf
 * change machine at once and pause the turns for a while.
 *
 * @param states each machine's latest state, or null until it first answers.
 * @param addresses each machine's address, shown until it has said its name.
 */
@Composable
fun MachinesScreen(
    states: List<DashboardState?>,
    addresses: List<String>,
    dataNote: String? = null,
    repoNames: List<String> = emptyList(),
) {
    val nearCap = HiveTokens.ThresholdsProposed.memoryNearCapPercent
    val summaries = states.mapIndexed { i, state -> machineSummary(state, addresses[i], nearCap) }
    val latestSummaries by rememberUpdatedState(summaries)
    val latestStates by rememberUpdatedState(states)

    var wanted by remember { mutableIntStateOf(0) }
    // Until the wanted machine has a reading, show the first one that does.
    val onScreen = if (states.getOrNull(wanted) != null) wanted else states.indexOfFirst { it != null }
    val shown = states.getOrNull(onScreen)
    if (shown == null) {
        LoadingScreen(addresses.first())
        return
    }

    val progress = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    // Counts key presses, so each one restarts the pause.
    var presses by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(presses) {
        if (presses > 0) {
            paused = true
            delay(HiveTokens.Motion.machinePauseSeconds.seconds)
            paused = false
        }
    }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            val length = turnLength(
                latestSummaries[onScreenOf(wanted, latestStates)],
                HiveTokens.Motion.machineTurnSeconds.seconds,
                HiveTokens.Motion.machineTurnTroubleSeconds.seconds,
            )
            // After a pause the turn carries on from where its bar stopped.
            val left = (length.inWholeMilliseconds * (1f - progress.value)).toInt().coerceAtLeast(0)
            progress.animateTo(1f, tween(left, easing = LinearEasing))
            wanted = nextTurn(onScreenOf(wanted, latestStates), latestSummaries)
            progress.snapTo(0f)
        }
    }

    DashboardScreen(shown, dataNote, repoNames, machineKey = onScreen) {
        MachineShelf(
            machines = summaries,
            onScreen = onScreen,
            turnProgress = { progress.value },
            paused = paused,
            onStep = { step ->
                wanted = stepMachine(onScreen, states.map { it != null }, step)
                presses++
                scope.launch { progress.snapTo(0f) }
            },
        )
    }
}

private fun onScreenOf(wanted: Int, states: List<DashboardState?>): Int =
    if (states.getOrNull(wanted) != null) wanted else states.indexOfFirst { it != null }.coerceAtLeast(0)
