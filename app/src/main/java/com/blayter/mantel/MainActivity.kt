package com.blayter.mantel

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.blayter.mantel.data.config.MantelConfig
import com.blayter.mantel.data.config.StoredConfig
import com.blayter.mantel.data.config.StoredSignIn
import com.blayter.mantel.data.github.GitHubAccount
import com.blayter.mantel.data.github.GitHubSession
import com.blayter.mantel.data.github.GitHubSignInClient
import com.blayter.mantel.data.config.hostOf
import com.blayter.mantel.data.github.GitHubDeploySource
import com.blayter.mantel.data.github.GitHubRuns
import com.blayter.mantel.data.glances.GlancesHostSource
import com.blayter.mantel.data.mock.MockDashboard
import com.blayter.mantel.data.mock.MockDeploySource
import com.blayter.mantel.data.mock.MockHostSource
import com.blayter.mantel.data.mock.MockMachines
import com.blayter.mantel.domain.ExpectedRunners
import com.blayter.mantel.domain.DashboardState
import com.blayter.mantel.domain.FoundMachine
import com.blayter.mantel.domain.MachineCondition
import com.blayter.mantel.domain.machineSummary
import com.blayter.mantel.domain.machinesAfterPicking
import com.blayter.mantel.domain.Thresholds
import com.blayter.mantel.ui.DashboardScreen
import com.blayter.mantel.ui.LoadingScreen
import com.blayter.mantel.ui.MachinesScreen
import com.blayter.mantel.ui.HiveFrame
import com.blayter.mantel.ui.setup.GitHubSignInFromSettings
import com.blayter.mantel.ui.setup.LocalMachinesSubtitle
import com.blayter.mantel.ui.setup.OnTvSetup
import com.blayter.mantel.ui.setup.RepositoriesScreen
import com.blayter.mantel.ui.setup.SETTINGS_MACHINES_SUBTITLE
import com.blayter.mantel.ui.setup.SettingsDeploys
import com.blayter.mantel.ui.setup.SettingsMachine
import com.blayter.mantel.ui.setup.SettingsScreen
import com.blayter.mantel.ui.theme.ActiveTheme
import com.blayter.mantel.ui.theme.HiveThemes
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.MantelTheme
import com.blayter.mantel.ui.theme.ThemeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A running data pipeline and what the screen should say about it. */
private class Session(
    /** One per machine, the first one first. Each polls its machine the whole time. */
    val repositories: List<DashboardRepository>,
    /** Where each machine's data comes from, for the loading screen and until it says its name. */
    val addresses: List<String>,
    /** Label beside the weekday while some of the data is not real. */
    val dataNote: String?,
    repoNames: List<String>,
    val job: Job,
) {
    /** Asks GitHub now which repositories may be read. Null when not signed in on the TV. */
    var checkRepositories: (() -> Unit)? = null

    /** The sign-in's token as it is now, after any refresh. */
    var gitHub: GitHubSession? = null

    /** Short names of the repositories the deploys card watches. They can change on GitHub. */
    var repoNames by mutableStateOf(repoNames)
}

/** Mantel's only activity: an unattended, read-only wall display. */
class MainActivity : ComponentActivity() {
    private var session by mutableStateOf<Session?>(null)
    private var fileProblem by mutableStateOf<String?>(null)

    /** Nothing is stored yet, so the TV asks which machines to watch. */
    private var needsSetup by mutableStateOf(false)
    private var store: ConfigStore? = null
    private lateinit var themes: ThemeStore

    /** What the display is running on, for settings. Null in the mock build and before setup. */
    private var stored by mutableStateOf<StoredConfig?>(null)

    /** What is in front of the dashboard, if anything. */
    private var overlay by mutableStateOf(Overlay.None)

    /** OK was held to open settings; its release must not press what is now in focus. */
    private var swallowOk = false

    private val started = TimeSource.Monotonic.markNow()
    private val elapsed = { started.elapsedNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A wall display must never blank or sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themes = ThemeStore(this)
        themes.apply()
        themeFromIntent(intent)

        if (BuildConfig.MOCK_DATA) {
            val machines = listOf(MockHostSource(elapsed) to "mock") + MockMachines.extra(BuildConfig.MOCK_MACHINES - 1, elapsed)
            session = open(machines, MockDeploySource(elapsed), "mock data", MockDashboard.seed)
        } else {
            lifecycleScope.launch { followConfig() }
        }

        setContent {
            MantelTheme {
                val current = session
                if (current == null) {
                    if (needsSetup) {
                        OnTvSetup(fileProblem, onDone = ::finishSetup)
                    } else {
                        // The moment before the stored config has been read.
                        HiveFrame(showDay = false) {}
                    }
                } else {
                    val states = current.repositories.map { it.state.collectAsStateWithLifecycle().value }
                    val running = stored
                    if (running != null && overlay != Overlay.None) {
                        Settings(current, running, states)
                    } else if (states.size == 1) {
                        // One machine: the approved screen, exactly.
                        states[0]?.let { DashboardScreen(it, current.dataNote, current.repoNames) }
                            ?: LoadingScreen(current.addresses[0])
                    } else {
                        MachinesScreen(states, current.addresses, current.dataNote, current.repoNames)
                    }
                }
            }
        }
    }

    @Composable
    private fun Settings(current: Session, running: StoredConfig, states: List<DashboardState?>) {
        val config = running.config
        val signIn = running.signIn
        when (overlay) {
            Overlay.AddMachine -> CompositionLocalProvider(LocalMachinesSubtitle provides SETTINGS_MACHINES_SUBTITLE) {
                OnTvSetup(
                    fileProblem = null,
                    watchedAlready = config.glancesUrls.toSet(),
                    onMachines = { watched, pickerOrder ->
                        val urls = machinesAfterPicking(config.glancesUrls, watched, pickerOrder)
                        if (urls.isNotEmpty() && urls != config.glancesUrls) {
                            change(config.copy(glancesUrl = urls.first(), moreGlancesUrls = urls.drop(1)), currentSignIn(current, signIn))
                        }
                        overlay = Overlay.Settings
                    },
                    onCancel = { overlay = Overlay.Settings },
                )
            }
            Overlay.SignIn -> GitHubSignInFromSettings(
                client = remember { GitHubSignInClient() },
                onDone = { account ->
                    change(config.copy(githubToken = null, repos = account.repos), StoredSignIn(account.token, account.login))
                    overlay = Overlay.Settings
                },
                onCancel = { overlay = Overlay.Settings },
            )
            Overlay.Repositories -> if (signIn == null) {
                LaunchedEffect(Unit) { overlay = Overlay.Settings }
            } else {
                RepositoriesScreen(
                    login = signIn.login,
                    repos = config.repos,
                    onCheck = { current.checkRepositories?.invoke() },
                    onBack = { overlay = Overlay.Settings },
                )
            }
            else -> SettingsScreen(
                machines = config.glancesUrls.mapIndexed { i, url ->
                    val address = current.addresses.getOrElse(i) { hostOf(url) }
                    val summary = machineSummary(states.getOrNull(i), address, HiveTokens.ThresholdsProposed.memoryNearCapPercent)
                    SettingsMachine(
                        url = url,
                        name = summary.name,
                        address = address,
                        notAnswering = (summary.condition as? MachineCondition.NotAnswering)?.sentence,
                    )
                },
                deploys = when {
                    signIn != null -> SettingsDeploys.SignedIn(signIn.login, config.repos)
                    running.tokenNotStored -> SettingsDeploys.NotStored
                    config.githubToken != null -> SettingsDeploys.FromFile(config.repos)
                    else -> SettingsDeploys.None
                },
                onRemove = { machine ->
                    val urls = config.glancesUrls - machine.url
                    if (urls.isNotEmpty()) {
                        change(config.copy(glancesUrl = urls.first(), moreGlancesUrls = urls.drop(1)), currentSignIn(current, signIn))
                    }
                },
                onAddMachine = { overlay = Overlay.AddMachine },
                onChooseRepositories = { overlay = Overlay.Repositories },
                onSignIn = { overlay = Overlay.SignIn },
                // Only this display forgets the sign-in; nothing changes on GitHub.
                onSignOut = { change(config.copy(githubToken = null, repos = emptyList()), null) },
                onStartOver = ::startOver,
                onBack = { overlay = Overlay.None },
                themes = HiveThemes.all,
                theme = ActiveTheme.theme,
                onTheme = themes::choose,
            )
        }
    }

    /** The sign-in to store again with a changed config: the same person, with the token as it is now. */
    private fun currentSignIn(current: Session, signIn: StoredSignIn?): StoredSignIn? =
        signIn?.let { StoredSignIn(current.gitHub?.current ?: it.token, it.login) }

    /** Stores a change made in settings and restarts the display on it. Machine history starts again. */
    private fun change(config: MantelConfig, signIn: StoredSignIn?) {
        val store = store ?: return
        lifecycleScope.launch { goLive(withContext(Dispatchers.IO) { store.save(config, signIn) }) }
    }

    /** Forgets everything this display stores and shows setup again. */
    private fun startOver() {
        val store = store ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.clear() }
            themes.forget()
            session?.job?.cancel()
            session = null
            stored = null
            overlay = Overlay.None
            needsSetup = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        themeFromIntent(intent)
    }

    /**
     * `adb shell am start -n com.blayter.mantel/.MainActivity --es theme <id>`
     * switches theme and remembers it, so a theme can be tried on a TV or on
     * the mock build, which has no settings. An unknown id is ignored.
     */
    private fun themeFromIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_THEME) ?: return
        HiveThemes.all.firstOrNull { it.id == id }?.let(themes::choose)
    }

    /**
     * Settings opens from the dashboard with the Menu button, or by holding
     * OK on a remote that has none.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (swallowOk && ok) {
            if (event.action == KeyEvent.ACTION_UP) swallowOk = false
            return true
        }
        if (session != null && stored != null && overlay == Overlay.None) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) overlay = Overlay.Settings
                return true
            }
            val held = event.isLongPress || (event.repeatCount > 0 && event.eventTime - event.downTime >= HOLD_OK_MILLIS)
            if (ok && event.action == KeyEvent.ACTION_DOWN && held) {
                overlay = Overlay.Settings
                swallowOk = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Starts from the stored config if there is one, then keeps looking for a
     * pushed config file: the first one sets the display up, a later one
     * points it somewhere new.
     */
    private suspend fun followConfig() {
        val store = withContext(Dispatchers.IO) { ConfigStore(applicationContext) }
        this.store = store
        val stored = withContext(Dispatchers.IO) { store.load() }
        if (stored != null) goLive(stored) else needsSetup = true
        while (true) {
            when (val result = withContext(Dispatchers.IO) { ConfigFile.tryRead(applicationContext) }) {
                is ConfigFileResult.Loaded -> {
                    val saved = withContext(Dispatchers.IO) { store.save(result.config) }
                    fileProblem = null
                    overlay = Overlay.None
                    goLive(saved)
                }
                is ConfigFileResult.Unusable -> fileProblem = result.reason
                ConfigFileResult.NotFound -> fileProblem = null
            }
            delay(CONFIG_CHECK_EVERY)
        }
    }

    /**
     * What was chosen in on-TV setup becomes the stored config: the machines,
     * and the GitHub sign-in if there was one.
     */
    private fun finishSetup(machines: List<FoundMachine>, account: GitHubAccount?) {
        val store = store ?: return
        val first = machines.firstOrNull() ?: return
        val config = MantelConfig(first.url, null, account?.repos.orEmpty(), machines.drop(1).map { it.url })
        val signIn = account?.let { StoredSignIn(it.token, it.login) }
        lifecycleScope.launch { goLive(withContext(Dispatchers.IO) { store.save(config, signIn) }) }
    }

    private fun goLive(stored: StoredConfig) {
        this.stored = stored
        needsSetup = false
        val config = stored.config
        val token = config.githubToken
        val signIn = stored.signIn
        session?.job?.cancel()
        val machines = config.glancesUrls.map { GlancesHostSource(it) to hostOf(it) }
        session = when {
            signIn != null -> openSignedIn(machines, signIn, config.repos)
            // The token could not be encrypted on this TV, so there is none. The deploys card says so.
            stored.tokenNotStored -> open(machines, NoToken, null, repos = config.repos)
            token != null -> open(machines, GitHubDeploySource(token, config.repos), null, repos = config.repos)
            // No GitHub details at all: deploys stay on the mock, and the header says so.
            else -> open(machines, MockDeploySource(elapsed), "sample deploys")
        }
    }

    /**
     * Signed in on the TV: the token is kept fresh, and the list of
     * repositories is read again from GitHub now and then, because the owner
     * changes it there and not here.
     */
    private fun openSignedIn(machines: List<Pair<HostSource, String>>, signIn: StoredSignIn, storedRepos: List<String>): Session {
        val client = GitHubSignInClient()
        val gitHub = GitHubSession(signIn.token, client) { fresh ->
            withContext(Dispatchers.IO) { store?.saveToken(fresh) }
        }
        val repos = AtomicReference(storedRepos)
        val opened = open(machines, GitHubDeploySource(gitHub::token, repos::get), null, repos = storedRepos)
        val askNow = Channel<Unit>(Channel.CONFLATED)
        opened.gitHub = gitHub
        opened.checkRepositories = { askNow.trySend(Unit) }
        (lifecycleScope + opened.job).launch {
            while (true) {
                try {
                    val now = client.repositories(gitHub.token())
                    if (now != repos.get()) {
                        repos.set(now)
                        opened.repoNames = now.map(GitHubRuns::shortRepo)
                        stored = stored?.let { it.copy(config = it.config.copy(repos = now)) }
                        withContext(Dispatchers.IO) { store?.saveRepos(now) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No answer, or the sign-in has ended (the deploys card says that). Keep the list.
                }
                withTimeoutOrNull(REPOSITORIES_CHECK_EVERY) { askNow.receive() }
            }
        }
        return opened
    }

    /**
     * The one place that chooses data sources. Each machine gets its own
     * repository, which holds that machine's history; they share one deploy
     * source, which answers from its last fetch.
     *
     * @param machines each machine's source and address, the first one first.
     */
    private fun open(
        machines: List<Pair<HostSource, String>>,
        deploySource: DeploySource,
        dataNote: String?,
        seed: RepositorySeed = RepositorySeed(),
        repos: List<String> = emptyList(),
    ): Session {
        val proposed = HiveTokens.ThresholdsProposed
        val job = Job(lifecycleScope.coroutineContext[Job])
        val repositories = machines.mapIndexed { i, (hostSource, _) ->
            DashboardRepository(
                hostSource = hostSource,
                deploySource = deploySource,
                thresholds = Thresholds(
                    memoryNearCapPercent = proposed.memoryNearCapPercent,
                    sustainedCores = proposed.sustainedCpuCores.toDouble(),
                    sustainedFor = proposed.sustainedCpuMinutes.minutes,
                    endedLinger = proposed.endedLingerSeconds.seconds,
                ),
                now = elapsed,
                // Only the first machine is the CI box: it alone has the sample history
                // and the runners that are expected to be present.
                seed = if (i == 0) seed else RepositorySeed(),
                expectedRunners = if (i == 0) ExpectedRunners else emptyList(),
                // Asked every second so running timers tick; the GitHub source only goes to the network every 45 s.
                deployPollEvery = 1.seconds,
            ).also { it.start(lifecycleScope + job) }
        }
        return Session(repositories, machines.map { it.second }, dataNote, repos.map(GitHubRuns::shortRepo), job)
    }

    private enum class Overlay { None, Settings, AddMachine, SignIn, Repositories }

    private companion object {
        /** How long OK is held before settings opens. */
        const val HOLD_OK_MILLIS = 700L

        /** The intent extra that switches theme; see [themeFromIntent]. */
        const val EXTRA_THEME = "theme"

        val CONFIG_CHECK_EVERY: Duration = 5.seconds

        /** How often a signed-in display asks GitHub which repositories it may read. */
        val REPOSITORIES_CHECK_EVERY: Duration = 10.minutes

        val NoToken = object : DeploySource {
            override suspend fun recentRuns() = throw DeploySourceException(DeployFailure.TokenNotStored)
        }
    }
}
