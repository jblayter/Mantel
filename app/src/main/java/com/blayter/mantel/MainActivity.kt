package com.blayter.mantel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.blayter.mantel.data.DashboardRepository
import com.blayter.mantel.data.mock.MockDashboard
import com.blayter.mantel.data.mock.MockDeploySource
import com.blayter.mantel.data.mock.MockHostSource
import com.blayter.mantel.domain.Thresholds
import com.blayter.mantel.ui.DashboardScreen
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.MantelTheme
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Mantel's only activity: an unattended, read-only wall display. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A wall display must never blank or sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val repository = mockRepository()
        repository.start(lifecycleScope)

        setContent {
            MantelTheme {
                val state by repository.state.collectAsStateWithLifecycle()
                // Loading and host-unreachable states are not designed yet; show the bare ground until data arrives.
                state?.let { DashboardScreen(it) }
            }
        }
    }

    /**
     * The one place that chooses data sources. To go live, replace the two
     * mock sources with the Glances and GitHub clients and drop the seed;
     * nothing in the repository, rules or UI changes.
     */
    private fun mockRepository(): DashboardRepository {
        val started = TimeSource.Monotonic.markNow()
        val elapsed = { started.elapsedNow() }
        val proposed = HiveTokens.ThresholdsProposed
        return DashboardRepository(
            hostSource = MockHostSource(elapsed),
            deploySource = MockDeploySource(elapsed),
            thresholds = Thresholds(
                memoryNearCapPercent = proposed.memoryNearCapPercent,
                sustainedCores = proposed.sustainedCpuCores.toDouble(),
                sustainedFor = proposed.sustainedCpuMinutes.minutes,
                endedLinger = proposed.endedLingerSeconds.seconds,
            ),
            now = elapsed,
            seed = MockDashboard.seed,
            // The mock timer should tick every second; the real GitHub poll is 30-60 s.
            deployPollEvery = 1.seconds,
        )
    }
}
