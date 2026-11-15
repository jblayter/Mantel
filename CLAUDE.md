# Mantel

Open-source Android TV app: an always-on wall display for a home CI box. It
answers two questions at a glance from across the room: "is the machine
struggling?" and "did my last deploy go out?"

The main "Hive" dashboard is built and runs on mock data. There are no network
calls yet: the Glances and GitHub clients are the next piece of work.

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
- `MainActivity.mockRepository()` is the only place that chooses sources. Going
  live means replacing the two mock sources there and dropping the seed.

## Decisions made

- Where the mockup and tokens.json disagreed on a size, the mockup won and the
  value was added to tokens.json.
- The kind line in a cell uses short units (`RUNNER · 4m`); the long form does
  not fit.

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
  `http://ci-runner01.local:61208/api/4/containers`, `/quicklook`, `/mem`,
  `/load`. Poll every 3-5 seconds. Glances keeps no history, so hold recent
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
- Not designed yet: run detail, first-run setup (TV QR screen plus phone form),
  settings, and the loading, host-unreachable, token-expired and
  no-recent-runs states. Design them in the Hive language before building.
- Whether the table-style container list becomes a drill-in screen.
- How secrets (GitHub token, Glances password) are stored is undecided.
- The short-name rule for non-runner containers (`shortNameOf`) is an
  assumption that matches the mockup; check it against the real container list.
- How the deploy progress bar gets a value: GitHub reports no progress for a
  run, and the mock's percentage is invented.
- What time window the CPU, memory and load charts cover (now 60 samples).
- Whether coral warnings should be listed before amber ones when more than
  three apply.
- The launcher banner is a placeholder, not designed art.

## Working agreements

- Work on a branch, not `main`. Commit only when asked.
