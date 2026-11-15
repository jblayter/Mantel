# Mantel

Open-source Android TV app: an always-on wall display for a home CI box. It
answers two questions at a glance from across the room: "is the machine
struggling?" and "did my last deploy go out?"

The repo currently holds only the design. No app code exists yet.

## Read first

- `docs/design/DESIGN.md` — the approved design language ("Hive"). Follow it
  for every screen: warm walnut palette, honeycomb container cells,
  craftsman-style cards, Fraunces + DM Sans.
- `docs/design/tokens.json` — colors, type scale, spacing, hex geometry. Build
  the Compose theme from this file; do not invent new colors or sizes.
- `docs/design/mockups/Hive.dc.html` — source of the approved main screen. It
  only renders inside the design canvas; use it as a reference for structure,
  sample data and the state logic in its script block.

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
  confirmed.
- Not designed yet: run detail, first-run setup (TV QR screen plus phone form),
  settings, and the loading, host-unreachable, token-expired and
  no-recent-runs states. Design them in the Hive language before building.
- Whether the table-style container list becomes a drill-in screen.
- Module layout, minimum SDK and how secrets (GitHub token, Glances password)
  are stored are undecided.

## Working agreements

- Work on a branch, not `main`. Commit only when asked.
