# Mantel

Open-source Android TV app: an always-on wall display for a home CI box. It
answers two questions at a glance from across the room: "is the machine
struggling?" and "did my last deploy go out?"

The main "Hive" dashboard is built. Host stats and containers are live from
Glances and deploys are live from GitHub Actions when the config has a token;
without one the deploys card is the mock and the header says "sample deploys".

## Read first

- `docs/design/DESIGN.md` — the approved design language ("Hive"). Follow it
  for every screen: warm walnut palette, honeycomb container cells,
  craftsman-style cards, Fraunces + DM Sans.
- `docs/design/tokens.json` — colors, type scale, spacing, hex geometry. Build
  the Compose theme from this file; do not invent new colors or sizes.
- `docs/design/mockups/Hive.dc.html` — source of the approved main screen. It
  only renders inside the design canvas; use it as a reference for structure,
  sample data and the state logic in its script block.

## Build and run

- Always build with `./gradlew` (Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21, JDK 17
  pinned in `gradle.properties`). The Homebrew `gradle` runs on a newer JDK
  that this AGP does not support. Same toolchain as the KrakenTV board.
- `local.properties` (gitignored) must point at the SDK:
  `sdk.dir=/opt/homebrew/share/android-commandlinetools`.
- `./gradlew :app:testDebugUnitTest` runs the unit tests;
  `./gradlew :app:installDebug` installs on the running emulator or TV.
- Emulator: the `Mantel_TV_1080p` AVD (Android TV 14, 1920x1080). Check changes
  there with a screenshot (`adb exec-out screencap -p`) against the mockup.

## Code layout

One module, `:app`, application ID `com.blayter.mantel`, minSdk 26.

- `ui/theme` — `HiveTokens` is generated from `docs/design/tokens.json` on every
  build by the `generateHiveTokens` task in `app/build.gradle.kts`; it is not
  checked in. To change a color or size, edit tokens.json. `MantelTheme`
  overrides density so the screen is always 1920 dp wide: one design px is one
  dp, and "24dp body text" is 24 physical px on a 1080p panel.
- `ui/hive`, `ui/panel` — the hex grid and cells; the count, host chart and
  deploy cards. Sizes that exist only in the mockup are named constants at the
  top of each file.
- `domain` — models and the pure rules (kinds, short names, sorting, warnings,
  formatting).
- `data` — `HostSource` and `DeploySource` interfaces, and `DashboardRepository`,
  which holds all history and derives missing, ended, sustained CPU and the
  trends. `data/mock` has the ticking mock sources and the seed that opens on
  the mockup's sample screen.
- `data/config` — the pushed `mantel-config.json` (`ConfigFile`, read once and
  deleted) and `ConfigStore` (EncryptedSharedPreferences). `data/glances` — the
  Glances client; `GlancesParser` is pure and tested against captured replies in
  `app/src/test/resources/glances`.
- `data/github` — `GitHubDeploySource` (fetches every 45 s, answers every second
  so timers tick) and the pure rules in `GitHubRuns`.
- `MainActivity.open()` is the only place that chooses sources. No config shows
  `SetupScreen`; a config with no reading yet shows `LoadingScreen`; unanswered
  polls set `DashboardState.stale` and the dashboard dims to the last reading.
- Several machines: one `DashboardRepository` per machine, all polling all the
  time and sharing one deploy source. `ui/MachinesScreen` runs the turns,
  `ui/panel/MachineShelf` draws the plaques, and the rules (summaries, turn
  order and length, which plaques show) are pure, in `domain/MachineRules`.
  One machine never goes through any of this.
- `-Pmantel.mock=true -Pmantel.mockMachines=3` adds sample machines (one with
  a warning, one that stops answering after 20 s); 6 shows the "N more" plaque.
- `-Pmantel.mock=true` builds the all-mock app that opens on the mockup's sample
  screen.

## Decisions made

- Where the mockup and tokens.json disagreed on a size, the mockup won and the
  value was added to tokens.json.
- The kind line in a cell uses short units (`RUNNER · 4m`); the long form does
  not fit.
- Config arrives as an adb-pushed file, never in the APK or repo. The Fire TV
  (Android 11) refuses a push into `/sdcard/Android/data`, so the route is
  `/data/local/tmp` then `run-as cp` into internal storage.
- The CI box is `192.168.6.38`; the Fire TV (`192.168.6.226`) cannot resolve
  `.local` names, so configs carry the IP.
- The box's name, thread count and total memory on screen come from Glances
  (`/system` hostname, `/load` cpucore, `/mem` total). Never hard-code them;
  the name and size in `data/mock` are only the mockup's sample values.
- `mantel-config.json` at the repo root is gitignored and may hold the owner's
  GitHub token. Never read, print or push it; the owner pushes it themselves.
- If the TV cannot encrypt, the token is not stored and the deploys card says so.
- Deploys: every workflow of the repositories in the config, last 7 days,
  running first. Skipped and cancelled runs are hidden (cancelled is my
  assumption). The progress bar is elapsed time over the median of that
  workflow's recent successful runs, capped at 95%.
- The deploys card is a carousel, one page per repository, changing every 10 s
  or with left and right on the remote. A run is active while it is going and
  for an hour after it ends. While any run is active the card shows only the
  active repositories and runs, and the count and host cards shrink to make
  room; otherwise (the maintenance window) it shows every repository's latest
  four runs. The pure rules are in `domain/DeployRules.kt`.
- The owner asked for "active" to mean started in the last hour. It is built as
  going, or ended in the last hour, so a long run does not drop off the card
  while it is still running. Unconfirmed.
- The deploys card reaches 24dp into the bottom overscan margin, at the owner's
  suggestion, so four runs fit.
- The mock deploy source loops every 5 minutes: 3 active, then 2 pretending
  hours have passed, so both states can be seen.
- Memory is in binary units, as Glances and `docker stats` show it (a 64 GB
  box reads 59 GB, a `4g` cap 4.0 GB).
- When more warnings apply than there are lines (three, or two with the
  shelf), coral ones are listed first, each color in cell order. When all fit
  they stay in cell order. Owner's decision, 2026-11-19.
- Glances reports container uptime only as rounded words ("6 hours").

## Product constraints

- Android TV, Jetpack Compose for TV. 1920x1080 landscape, 5% overscan-safe
  margin, D-pad only, every focusable element needs an obvious focus state.
- 10-foot UI: body text 24dp minimum (hex cell labels are the one exception).
- The main screen must be fully readable with zero interaction.
- Read-only. Never add controls that start, stop or change containers or
  workflows.
- Status never relies on color alone: pair it with an icon, a word or a line
  style.
- On screen for hours: no large static bright areas; keep the slow layout drift.

## Data sources

- Glances REST API on the CI box, no login, home network only:
  `<glancesUrl>/api/4/containers`, `/quicklook`, `/mem`, `/load` every poll,
  and `/system` once for the hostname. Poll every 3-5 seconds. Glances keeps no history, so hold recent
  samples in memory for trends.
- GitHub Actions workflow runs via the GitHub REST API with a personal access
  token, polled every 30-60 seconds.
- Container kinds, expected runners and the "no cap" memory rule are in
  DESIGN.md under "Data rules".
- The app must work against mock data (the sample values in the mockup) so it
  can be developed without access to the machine.

## Open questions — ask the owner, do not guess

- Alert thresholds in `tokens.json` (`thresholds_proposed`) are proposals, not
  confirmed. The app uses them provisionally. Host CPU turning coral at 90%
  (from the mockup's script) is also unconfirmed.
- Designed and built: the interim setup screen, loading, host-unreachable and
  the deploys token and no-recent-runs cards (DESIGN.md, "Setup, loading and
  failure states"; mockups in `docs/design/mockups`). "No recent runs", the
  dimmed last-seen rows and the not-stored card have not been seen on a device.
- The active and maintenance states of the deploys card and its carousel have
  no mockup; they were built from the owner's description.
- Not designed yet: run detail, settings. Design them in the Hive language
  before building.
- Going to market: the owner wants both stores (Google Play and Amazon), will
  register a GitHub App for device sign-in, and wants several machines per
  TV. The on-TV setup that replaces the adb file is designed but not approved
  or built (DESIGN.md, "On-TV setup"; `Setup-*` mockups). It replaced the
  earlier QR-and-phone-form idea.
- Several machines: the owner chose one on screen at a time, taking turns,
  and rejected the first look (`Header-Machines` mockup, a row of names in the
  header). Do not build it. The redesign brief for Claude Design is
  `docs/design/handoff/PROMPT.md`; the `Setup-*` drafts go with it.
- The data rules (kinds, expected runners, short names) describe the owner's
  box and need a general form before a store release.
- The screen turns stale after 3 unanswered polls
  (`STALE_AFTER_FAILED_POLLS`); provisional.
- What the deploys card shows when GitHub fails for a reason other than the
  token (wrong repository name, no network): today it keeps what it had.
- reach and krakentv repositories are not in the config yet.
- Several machines: only the first machine in the config has expected runners;
  whether other machines need their own list is undecided.
- The machine change and the deploys carousel are not kept apart in time yet.
- Whether the table-style container list becomes a drill-in screen.
- The short-name rule for non-runner containers (`shortNameOf`) is an
  assumption that matches the mockup; check it against the real container list.
- What time window the CPU, memory and load charts cover (now 60 samples).
- The launcher banner is a placeholder, not designed art.

## Working agreements

- Work on a branch, not `main`. Commit only when asked.
