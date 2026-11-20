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
- `ui/setup` — on-TV setup: `OnTvSetup` holds the flow. Step "machines" is
  `MachinePickerScreen`, `NoMachinesScreen` and `AddressScreen`; step
  "deploys" is `GitHubSetup` (code, code ran out, no repositories) with
  `QrTile`; then `ReadyScreen`. `SettingsScreen` reuses the same frame and
  screens. `MainActivity` shows it over the live session (`Overlay`) and
  opens it in `dispatchKeyEvent`: Menu, or OK held. `data/github/GitHubSignIn` is the device-flow
  client and `GitHubSession`, which keeps the token fresh; the pure reading
  of GitHub's answers is in `GitHubAuth`.
  `data/glances/GlancesScanner` asks the TV's own /24 for Glances (TCP connect,
  then GET only); the pure rules, including the number pad's `AddressEntry`,
  are in `domain/SetupRules.kt`.
- `MainActivity.open()` is the only place that chooses sources. No config shows
  `OnTvSetup`, whose result is stored as a config plus, if the owner signed
  in, a `StoredSignIn`; a
  config with no reading yet shows `LoadingScreen`; unanswered
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
- The setup picker shows one cell per address that answers, and never merges
  cells by name. The CI box answers on its wired and wireless addresses
  (`.38` and `.99`) and so shows twice; the owner picks one. Owner's
  decision, 2026-11-20.
- Glances reports container uptime only as rounded words ("6 hours").
- Install help on the TV (the "no machines" screen and the picker's "Not
  seeing a machine?" card) links by QR code to the official Glances docs,
  `nicolargo.github.io/glances`, not to the README. Owner's decision,
  2026-11-20.
- GitHub sign-in on the TV is the device flow against the owner's GitHub App
  `mantel-ci-cd-display` (app ID 5855032). Its client ID is in
  `GitHubAuth.kt`; it is public and there is no client secret anywhere. The
  three sign-in POSTs to github.com (`httpPostForm`) are the only requests
  that are not GETs, accepted by the owner on 2026-11-20. Never use POST for
  anything else.
- Settings opens with the Menu button or by holding OK, and holds the
  proposed board plus "Choose repositories" (the app page's QR code) and
  "Sign in to GitHub". Owner's decision, 2026-11-20. The destructive buttons
  ask once by changing their words; that confirm style is mine, not designed.
- After sign-in the repositories come from GitHub (where the app is installed),
  read again every 10 minutes, at most the 12 most recently pushed (the cap is
  my assumption, to stay inside GitHub's rate limit).

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
- Designed and built: loading, host-unreachable and
  the deploys token and no-recent-runs cards (DESIGN.md, "Setup, loading and
  failure states"; mockups in `docs/design/mockups`). "No recent runs", the
  dimmed last-seen rows and the not-stored card have not been seen on a device.
- The active and maintenance states of the deploys card and its carousel have
  no mockup; they were built from the owner's description.
- Not designed yet: run detail. Design it in the Hive language before
  building.
- Going to market: the owner wants both stores (Google Play and Amazon), will
  register a GitHub App for device sign-in, and wants several machines per
  TV. Of the on-TV setup that replaces the adb file, step "machines" (network
  search, picker, address pad), step "deploys" (GitHub sign-in by code and
  QR), Ready and settings are approved and built (DESIGN.md, "On-TV setup").
  The password prompt is proposed only (`docs/design/proposals`).
- Settings, not seen anywhere yet: removing a machine, the signed-in deploys
  card, "Choose repositories", signing in from settings, "Sign out" and
  "Start over". Seen on the emulator: opening by Menu and by holding OK, the
  not-signed-in screen, "Add a machine" and Back. Not seen on the Fire TV.
- GitHub sign-in, not seen with a real approval yet: everything after the
  code screen ("No repositories", Ready with repositories, live deploys from
  a sign-in, the token refresh after 8 hours). The code screen, Skip, Ready
  without deploys and Start were seen on the emulator.
- GitHub sign-in, not decided: what the deploys card says when the sign-in
  has ended (today: the token-expired card, written for a pushed token; the
  way back is settings, "Sign out" then "Sign in to GitHub"). What it says when signed in with no
  repository chosen (today: "No recent runs").
- On-TV setup, not decided: what the deploys card should show when GitHub was skipped
  (today: the sample, with "sample deploys" in the header). Machines are stored in address order, and only
  the first gets the expected runners, so picking a machine with a lower
  address than the CI box moves the "missing" cells to the wrong machine.
- On-TV setup, not seen on a device: "No machines answered" and the address
  pad on the Fire TV (both seen on the emulator).
- Several machines: the owner chose one on screen at a time, taking turns,
  and rejected the first look (`Header-Machines` mockup, a row of names in the
  header). Do not build it. The redesign brief for Claude Design is
  `docs/design/handoff/PROMPT.md`.
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
