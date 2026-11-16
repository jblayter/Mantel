package com.blayter.mantel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.blayter.mantel.data.DashboardRepository
import com.blayter.mantel.data.DeployFailure
import com.blayter.mantel.data.DeploySource
import com.blayter.mantel.data.DeploySourceException
import com.blayter.mantel.data.HostSource
import com.blayter.mantel.data.RepositorySeed
import com.blayter.mantel.data.config.ConfigFile
import com.blayter.mantel.data.config.ConfigFileResult
import com.blayter.mantel.data.config.ConfigStore
import com.blayter.mantel.data.config.StoredConfig
import com.blayter.mantel.data.github.GitHubDeploySource
import com.blayter.mantel.data.github.GitHubRuns
import com.blayter.mantel.data.glances.GlancesHostSource
import com.blayter.mantel.data.mock.MockDashboard
import com.blayter.mantel.data.mock.MockDeploySource
import com.blayter.mantel.data.mock.MockHostSource
import com.blayter.mantel.domain.Thresholds
import com.blayter.mantel.ui.DashboardScreen
import com.blayter.mantel.ui.LoadingScreen
import com.blayter.mantel.ui.SetupScreen
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.MantelTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A running data pipeline and what the screen should say about it. */
private class Session(
    val repository: DashboardRepository,
    /** Where the host data comes from, for the loading screen. */
    val address: String,
    /** Label beside the weekday while some of the data is not real. */
    val dataNote: String?,
    /** Short names of the repositories the deploys card watches. */
    val repoNames: List<String>,
    val job: Job,
)

/** Mantel's only activity: an unattended, read-only wall display. */
class MainActivity : ComponentActivity() {
    private var session by mutableStateOf<Session?>(null)
    private var fileProblem by mutableStateOf<String?>(null)

    private val started = TimeSource.Monotonic.markNow()
    private val elapsed = { started.elapsedNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A wall display must never blank or sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (BuildConfig.MOCK_DATA) {
            session = open(MockHostSource(elapsed), MockDeploySource(elapsed), "mock", "mock data", MockDashboard.seed)
        } else {
            lifecycleScope.launch { followConfig() }
        }

        setContent {
            MantelTheme {
                val current = session
                if (current == null) {
                    SetupScreen(fileProblem)
                } else {
                    val state by current.repository.state.collectAsStateWithLifecycle()
                    state?.let { DashboardScreen(it, current.dataNote, current.repoNames) } ?: LoadingScreen(current.address)
                }
            }
        }
    }

    /**
     * Starts from the stored config if there is one, then keeps looking for a
     * pushed config file: the first one sets the display up, a later one
     * points it somewhere new.
     */
    private suspend fun followConfig() {
        val store = withContext(Dispatchers.IO) { ConfigStore(applicationContext) }
        withContext(Dispatchers.IO) { store.load() }?.let(::goLive)
        while (true) {
            when (val result = withContext(Dispatchers.IO) { ConfigFile.tryRead(applicationContext) }) {
                is ConfigFileResult.Loaded -> {
                    val stored = withContext(Dispatchers.IO) { store.save(result.config) }
                    fileProblem = null
                    goLive(stored)
                }
                is ConfigFileResult.Unusable -> fileProblem = result.reason
                ConfigFileResult.NotFound -> fileProblem = null
            }
            delay(CONFIG_CHECK_EVERY)
        }
    }

    private fun goLive(stored: StoredConfig) {
        val config = stored.config
        val token = config.githubToken
        session?.job?.cancel()
        session = when {
            // The token could not be encrypted on this TV, so there is none. The deploys card says so.
            stored.tokenNotStored ->
                open(GlancesHostSource(config.glancesUrl), NoToken, config.glancesHost, null, repos = config.repos)
            token != null -> open(
                GlancesHostSource(config.glancesUrl), GitHubDeploySource(token, config.repos),
                config.glancesHost, null, repos = config.repos,
            )
            // No GitHub details in the config: deploys stay on the mock, and the header says so.
            else -> open(GlancesHostSource(config.glancesUrl), MockDeploySource(elapsed), config.glancesHost, "sample deploys")
        }
    }

    /** The one place that chooses data sources. */
    private fun open(
        hostSource: HostSource,
        deploySource: DeploySource,
        address: String,
        dataNote: String?,
        seed: RepositorySeed = RepositorySeed(),
        repos: List<String> = emptyList(),
    ): Session {
        val proposed = HiveTokens.ThresholdsProposed
        val repository = DashboardRepository(
            hostSource = hostSource,
            deploySource = deploySource,
            thresholds = Thresholds(
                memoryNearCapPercent = proposed.memoryNearCapPercent,
                sustainedCores = proposed.sustainedCpuCores.toDouble(),
                sustainedFor = proposed.sustainedCpuMinutes.minutes,
                endedLinger = proposed.endedLingerSeconds.seconds,
            ),
            now = elapsed,
            seed = seed,
            // Asked every second so running timers tick; the GitHub source only goes to the network every 45 s.
            deployPollEvery = 1.seconds,
        )
        val job = Job(lifecycleScope.coroutineContext[Job])
        repository.start(lifecycleScope + job)
        return Session(repository, address, dataNote, repos.map(GitHubRuns::shortRepo), job)
    }

    private companion object {
        val CONFIG_CHECK_EVERY: Duration = 5.seconds

        val NoToken = object : DeploySource {
            override suspend fun recentRuns() = throw DeploySourceException(DeployFailure.TokenNotStored)
        }
    }
}
