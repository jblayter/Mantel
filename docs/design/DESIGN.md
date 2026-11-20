# Mantel design language — "Hive"

Mantel is an always-on Android TV wall display for a home CI box. This document
is the approved visual direction and the rules behind it. Machine-readable
values are in [`tokens.json`](tokens.json); the approved mockup source is in
[`mockups/Hive.dc.html`](mockups/Hive.dc.html).

Live mockup (private to the owner): https://claude.ai/artifact/4Fd3qbhw8M1wowTr43kE39
— the artboard titled "Hive".

## Where the look comes from

The TV hangs in a craftsman-style home: walnut and cherry wood, cream walls,
stacked stone, a lamp with a honeycomb shade that glows amber. The screen should
look like it belongs on that wall, not like a terminal.

- **Warm, never black.** The ground is dark walnut brown. No pure black, no
  cool grey, no blue-black.
- **Honeycomb is the signature.** Each container is a hexagonal cell that fills
  with amber as it uses more CPU, like a lit cell in the lamp shade.
- **Craftsman, not glossy.** Panels are rectilinear with small 8px corners and a
  solid wood-toned border, like joinery. No gradients as decoration, no glow
  effects, no drop shadows.
- **Charts over tables.** Prefer a shape that can be read from across the room
  (a filled cell, an area chart, one big number) over rows of figures.
- **Plain sentences for trouble.** "rxflow 2 has held about 12 cores for 14 min",
  not a code or a badge alone.

## Color

All colors are in `tokens.json`. Roles:

| Role | Value | Use |
| --- | --- | --- |
| Ground | `#2B1E15` | Screen background |
| Surface | `#3A2A1E` | Cards, unselected chips |
| Surface border | `#5C4431` | 3px card border |
| Track | `#503B2B` | Empty part of bars |
| Text | `#F2E9DA` | Body text |
| Text bright | `#FFF8EA` | Key numbers |
| Text secondary | `#EADFCB` | Supporting lines |
| Text muted | `#D2C0A6` | Labels, captions |
| Text soft | `#E0D3BD` | Unselected chip label, memory line in a cell |
| Text dim | `#AD9D87` | Ended / inactive |
| Amber | `#F0B45A` | Accent: wordmark, chart lines, in-progress, selected chip |
| Runner | `#93BBD0` | Kind: long-lived CI runners (dusty slate blue) |
| Database | `#AFC487` | Kind: job databases (sage green); also "passed" |
| Job | `#EDE3D0` | Kind: any other job container (cream) |
| Trouble | `#FF8A68` | Missing runner, memory near cap, failed deploy (coral) |

Rules:

- Status never relies on color alone. Every state also has an icon, a word, or
  a line style (dashed outline for missing and ended).
- Amber means "busy / in progress / attention". Coral means "something is
  wrong". Do not use coral for anything that is merely busy.
- Text on an amber fill uses `#1A140C`.
- Brown gives less contrast than black. Do not use text darker than
  `Text dim` on the ground, and keep body text at `Text` or brighter.

## Type

- **Display: Fraunces** (weights 500, 700), tabular numerals. Used for the
  wordmark, the clock and every key number.
- **Text: DM Sans** (weights 500, 700). Everything else.
- No monospace anywhere.

| Token | Size | Use |
| --- | --- | --- |
| display-xl | 124px | Container count |
| display-l | 52px | Clock, cores inside a cell, host numbers |
| display-m | 44–48px | Wordmark, deploy timer |
| title | 30px | Deploy row title |
| lead | 26px | Section caption, header sub-line |
| body | 24px | Default; nothing in a card goes below this |
| cell-label | 20–23px | Labels inside a hex cell only |

10-foot rule: body text is 24px minimum. The only exception is inside a hex
cell, where 20–23px labels sit under a 52px number.

## Layout (1920×1080, landscape)

- Overscan-safe padding: 96px left/right, 54px top/bottom.
- Header row 64px: wordmark, host summary, clock.
- Body is a two-column grid: **980px hive column** and a flexible right column,
  32px gap.
- Right column is a vertical stack of cards, 20px gap: container count with
  30-minute stacked trend (216px), three host charts in a row (196px), deploys
  (fills the rest, and reaches 24px into the bottom margin so four runs fit).
- While deploys are active (see "Deploy rules") the right column gives its
  room to the deploys card: the count card keeps only its 30-minute trend
  (74px), the host cards lose the line under each number (168px), and the
  deploys card takes what is freed.
- Spacing steps: 8, 10, 16, 20, 24, 32.
- Card: surface fill, 3px surface-border, 8px radius, 18–26px padding.
- Host cards: the area chart is the card's background, edge to edge and top
  to bottom, and the full scale is the full height, so 60% CPU fills the card
  to 60%. The title, number and sub-line sit on top of it. (The approved
  mockup had the chart in a strip under the text.)
- Burn-in: the whole layout drifts by about 6px × 4px over 15 minutes and
  back. Keep large bright static areas out of the design.

### The hive

- Pointy-top hexagons, **208 × 240px**, 10px gap between cells in a row.
- Rows overlap: each row after the first has a top margin of **−51px**.
- Odd rows (2nd, 4th…) are offset right by **109px** (half a cell plus half a gap).
- 4 cells per row; 3 rows shows 12 containers. For more, shrink the cell
  proportionally rather than scrolling.
- Order: busiest first by default (use a short moving average of CPU so cells
  do not reshuffle on every sample). Missing runners are pinned first, ended
  containers last. Sort chips: Busiest, Memory, Name.

Each cell, top to bottom: kind and uptime in short units (`RUNNER · 4m`,
`DATABASE · 6d`; the long form does not fit the cell), cores in use
(`cpu_percent / 100`, one decimal, display-l), short name, memory.

Cell states:

| State | Outline | Fill | Extra |
| --- | --- | --- | --- |
| Normal | 4px, kind color | Amber glow rising from the bottom | — |
| Sustained high CPU | 6px amber | Brighter glow (`glowHot`) | Last line reads "busy 14 min" |
| Memory near cap | 6px coral | Normal glow | Memory line in coral, "3.6 of 4.0 GB" |
| Missing expected runner | 6px coral, dashed, slow pulse | None, coral-tinted base | "MISSING", "gone 3 min" |
| Ended | 4px dim, dashed | None, darker base | "ENDED", "20 s ago"; stays briefly so cells do not jump |

Glow height = `sqrt(cores / 12)`, clamped to 0–100%. The square root keeps idle
containers visibly lit and stops one busy runner flattening everything else.

Below the hive: up to three plain-sentence warnings, each with a triangle icon
in amber (busy) or coral (wrong).

## Motion

Subtle and slow. Allowed: the layout drift, the pulse on a missing cell
(2.6s), the spinner and moving stripes on an in-progress deploy, and the two
changes on the deploys card. When deploys become active or go quiet, the text
that is leaving fades out and the cards resize over 0.7s. When the deploys
card changes repository, the rows slide a short way and fade (0.45s). Nothing
else animates.

## Focus (D-pad)

The main screen needs no interaction. Where something is focusable (sort chips,
the deploys card when it has more than one repository, later a cell or deploy
row), focus is a 3px `#FFF8EA` border. Selected chip is amber fill with dark
text. With the deploys card focused, right and left change repository; left on
the first one moves focus back to the sort chips.

## Data rules shown in the mockup

Source is the Glances REST API on the CI box (`/api/4/containers`,
`/quicklook`, `/mem`, `/load`), polled every 3–5 seconds. Glances keeps no
history, so the app holds its own recent samples for the trends.

- Kinds: names matching `*-runner-runner-N` are runners; `postgres` images are
  job databases; everything else is a job container.
- Runners restart after every job, so a short uptime is normal, not a fault.
- `memory_limit` equal to the host total means "no cap"; only show "X of Y" and
  the near-cap warning when a real cap exists (krakentv runner, 4 GB).
- Expected runners: rxflow 1–3, reach 1–2, krakentv 1. One absent from the list
  is "missing".

Thresholds used in the mockup — **proposed, not yet confirmed by the owner**:
memory warning at 85% of cap; "sustained" CPU at roughly 8+ cores for 10+
minutes; ended cells linger for about a minute.

## Mapping to Jetpack Compose for TV

- Hive: a `Column` of `Row`s with the offsets above, or a custom `Layout`; each
  cell is a `Box` clipped to a hexagon `Shape`, with the glow as a
  bottom-anchored fill and the outline as a stroked path (dashed via
  `PathEffect`).
- Cards: `Surface` with `RoundedCornerShape(8.dp)` and a 3dp border.
- Charts: `Canvas` area charts; stacked step chart for the count trend.
- Treat px in this document as dp at 1080p.

## Setup, loading and failure states

Mockups are in `mockups/`: `State-Loading`, `State-Unreachable`,
`Deploys-TokenExpired` and `Deploys-NoRuns`. All of them reuse the main
screen's frame, header, cards and type. Nothing in them can be focused.

- **Not set up yet** (no config): the on-TV setup below. The earlier interim
  screen, which showed the adb commands, is gone; the commands are in the
  README.
- **Waiting for the first reading**: the main layout with twelve empty cells
  (ended-cell base and dashed outline), a dim "—" where each number will be,
  and the spinner with "Waiting for the first reading from [address]".
- **CI box not answering**: the last reading stays on screen, visibly stale.
  Every cell takes the ended look (no glow, dim dashed outline, dim text) but
  keeps its values. The count and host numbers go dim, the chart line goes dim
  on a track-colored fill, and the count title reads "containers at the last
  reading". The warnings are replaced by one coral line, "[host] has not
  answered for [time]", and a second line giving the clock time of the last
  reading. The deploys card stays live: GitHub is a separate source.
- **Deploys, token problem**: coral triangle, a coral title ("GitHub token has
  expired", or "was not accepted" for a token that never worked, or "was not
  stored" when the TV could not encrypt it), one sentence on what to do, then
  the last runs seen, dimmed, with clock times instead of ages.
- **Deploys, no recent runs**: a dim circle-and-dash icon, "No recent runs",
  and one sentence naming the repositories and the 7-day window.

## On-TV setup

Replaces the adb-pushed file for people who install from a store. The pushed
file stays as a developer route. Nothing long is ever typed with the remote,
and no token is typed at all. Nothing in setup can change a machine or GitHub.

### Step 1, machines — approved by the owner and built

Mockups: `Setup-1-Machines`, `Setup-2-NoMachines`, `Setup-3-Address`,
`Setup-3b-NoAnswer`.

All screens share one frame so nothing jumps between them: the header says
"Setup · machines" where the host summary goes; the title sits at the same
height on every screen; the buttons sit on the bottom edge of the 980px left
column; the right column holds one supporting thing.

- Buttons are cards with a 24px bold label. The main action is amber fill
  with dark text. Focus is the 3px `#FFF8EA` ring, 3px outside the control.
- Trouble is a coral triangle line with a sentence, as on the dashboard.
- **The search.** Mantel asks all 254 addresses that share the TV's first
  three numbers whether Glances answers on port 61208. It opens a connection
  and sends GET requests, nothing else. A spinner line counts: "Still
  looking. 148 of 254 addresses checked."
- **Machines.** The comb is the picker: a row of four places and a row of
  three. Each machine that answers becomes a cell: the state word ("WATCHING"
  with a tick, or "NOT WATCHING"), its container count as the number, its
  name, "containers". Watching: amber 6px outline and glow. Not watching:
  plain outline, dim text. Places not yet filled are dashed empty cells. OK on
  a cell watches it or stops. Machines are in address order; more than seven
  are not shown, and the sentence under the title says so.
- The right column is a card for the cell in focus: name, address, threads,
  memory, containers, and what OK will do. The facts are shown once, for one
  machine at a time. When the focus is on a button the card keeps the last
  cell and says "The last cell in focus".
- A cell in focus takes the ring in the gap around it, 3px outside its
  outline. (The board draws the ring inside and shrinks the cell; built, that
  pushed "NOT WATCHING" into the outline.)
- First focus: the first cell. If exactly one machine answered it is already
  watched and the focus starts on "Watch 1 machine". With nothing picked the
  main button is a dim "No machine picked yet" and is not a focus stop.
- **No machines.** One sentence on what Glances is, a three-step card, and a
  spinner line: the search repeats every 10 seconds by itself. First focus:
  "Look again now". The right column is the QR code of the official Glances
  documentation, `nicolargo.github.io/glances`, with the address under it
  and in step 1 (the owner's choice, 2026-11-20; the board's
  `mantel.example/start` was a placeholder). Step 2 shows `glances -w`.
- **Enter an address.** The first three numbers are filled in from the TV's
  own address and shown dim; the person types only the last one on a number
  pad (digits, dot, Delete). "Change first part" opens the whole address for
  another network. "Change port" moves the pad to the port, shown as a quiet
  24px label inside the field. A key that would make an impossible address is
  ignored. First focus: the "5" key. (The board's "Change the first part" and
  "Change the port" did not fit on one row with the other two buttons.)
- **Nothing answered.** The same screen: the field takes a coral border, and
  under it a coral line "192.168.1.57 did not answer on port 61208." with one
  sentence on what to check. No command is shown. The main button reads
  "Check again".
- A machine that answers at an address is added to the comb, watched.
- One cell per address that answers. A machine on both a wired and a
  wireless address shows as two cells with the same name; they are not
  merged, and the card for the cell in focus tells them apart by address.
- A Glances that asks for a password is not listed by the search. Entered by
  address, it gets a coral line saying Mantel cannot give a password yet.
- Under the card for the cell in focus, a second card, "Not seeing a
  machine?", says what a machine needs: install Glances, start it with
  `glances -w`, then "Look again", with a 220px QR code of the Glances
  documentation beside the steps. It is always there. Both cards fill the
  right column (the board's detail card is 560px wide). Added at the owner's
  request, 2026-11-20; it is not on the board.
- "Watch N machines" goes on to step 2. Nothing is stored until "Start".

### Step 2, deploys, and Ready — approved by the owner and built

Mockups: `Setup-4-GitHub`, `Setup-4b-CodeExpired`, `Setup-4c-NoRepos`,
`Setup-5-Ready`. The header says "Setup · step 2 of 2 · deploys", then
"Setup · ready".

Sign-in is GitHub's device flow against the GitHub App
`github.com/apps/mantel-ci-cd-display`, which asks for read access to Actions
and Metadata and nothing else. The app holds the app's client ID, which is
public, and no client secret. Asking for a code, exchanging it and refreshing
the token are POSTs to github.com: the only requests Mantel makes that are
not GETs. They change nothing in a repository or workflow.

- **Deploys.** "Show your deploys too?" Two numbered steps, the code in
  display-xl, a spinner line with the minutes left, and the QR code of
  `github.com/login/device` with the address under it. First focus: "Skip, no
  deploys for now". "Back" returns to the machines with the picks kept.
- **Code ran out.** The old code stays, dim and struck through; the QR tile
  becomes a dashed empty tile. A coral line says why: it ran out, or the
  request was cancelled on GitHub. First focus: "Get a new code".
- **No repositories.** Signed in, but the app is installed nowhere or has no
  repository chosen. Three steps and the QR code of the app's page. It asks
  GitHub again every 5 seconds and moves on by itself. "Continue without
  deploys" keeps the sign-in; "Sign out" makes this display forget it.
- **Ready.** Machines on the left, deploys on the right, a green tick per
  line, at most ten lines a card and then "and N more". First focus: "Start".
  Nothing starts on a timer. "Start" stores the machines and the sign-in.
- The QR tile is 374px with a 22px quiet margin: `Text secondary` ground with
  ground-colored squares. It is the one large light area allowed, on these
  screens only.

After sign-in:

- The repositories are the ones the owner chose for the app on GitHub, read
  again every 10 minutes. With more than 12, the 12 most recently pushed are
  watched.
- GitHub's token lasts 8 hours. It is swapped for a new one 10 minutes before
  it runs out, and both tokens are stored encrypted. If the TV cannot
  encrypt, the sign-in lasts until the app next starts.
- Signed in with no repository chosen, the deploys card says "No recent runs".
  Skipped, it shows the sample and the header says "sample deploys".

Differences from the boards, and states with no board:

- No step pips in the header, as in step 1.
- "Asking GitHub for a code", "GitHub did not give a code" (first focus "Try
  again") and "Approved. Reading what Mantel may show." were built without a
  board, in the same frame.
- The request being cancelled on GitHub reuses the "Code ran out" screen with
  its own sentence.
- Ready has cards for "No deploys for now" and "no repositories yet", which
  the board does not show.
- The "No repositories" step 2 says "Install" or "Configure"; the board says
  only "Configure", which is not there before the first install.

### Settings — approved by the owner and built

Mockup: `Settings`. Opened from the live dashboard with the Menu button, or
by holding OK for 0.7 s on a remote with no Menu button. Nothing is drawn on
the dashboard for it. The display keeps polling behind it. The mock build has
no settings.

- The setup frame, header "Settings". Machines card on the left, deploys card
  on the right, the same split as the dashboard. First focus: "Back to the
  display"; the remote's Back does the same.
- **Machines.** One row per machine: tick, name, address, and "answering" or
  the coral "has not answered for 4 min". With two or more machines a row is
  a focus stop and OK removes it; the last machine cannot be removed.
- **Add a machine** runs the network search again: setup's machines screens
  with the header "Settings · machines", the machines already watched ticked,
  and a "Back" button. Machines already watched keep their order, new ones
  go after them, and one the search did not find stays watched.
- **Deploys, signed in.** "Deploys · signed in as NAME", the repositories with
  ticks (ten lines, then "and N more"), "Repositories are chosen on GitHub,
  not here.", then "Choose repositories" and "Sign out of GitHub".
- **Choose repositories** shows the QR code of the app's page on GitHub with
  the same three steps as setup, and how many repositories Mantel reads now.
  While it is open the list is read from GitHub every 5 seconds.
- **Deploys, not signed in.** A sentence on where the runs come from (the
  sample, a pushed config file, or a token that was not stored) and "Sign in
  to GitHub", which runs setup's sign-in screens without "Skip".
- **Asking once.** "Remove", "Sign out of GitHub" and "Start over" change
  their words on the first OK ("OK again to remove") and act on the second;
  moving the focus away takes the question back. They change only what this
  display stores. "Sign out" does not end the authorization on GitHub.
- A change to machines or sign-in restarts the display's readings, so the
  charts start again.

Differences from the board: the "Choose repositories" button and its QR
screen, "Sign in to GitHub" and the not-signed-in cards were added at the
owner's request; the board shows only the signed-in card.

### Still proposed, not built

The password prompt is in `docs/design/proposals` with its rules.

## Several machines — the shelf

Approved by the owner and built. Mockups: `Shelf` (a warning and a silent
machine elsewhere), `Shelf-AllWell`, `Shelf-Paused`, `Shelf-Two`, `Shelf-Six`.
Two other directions (a column of machine cells, and a large nameplate with
pips) and an earlier header of machine names were not chosen.

One machine is on screen at a time and they take turns. Every machine is
polled all the time, so its trends are complete when its turn comes.

- With one machine the screen is exactly as approved: no shelf, and "name ·
  threads · GB" in the header.
- With two or more, the header is only the wordmark, weekday and clock. The
  machine's threads and memory are not repeated: the CPU and memory cards
  already say "of 32 cores" and "of 59 GB". Each machine's name appears once,
  on its plaque.
- The shelf is a full-width row of plaques under both columns, 96px tall,
  16px apart. A plaque is a card with a 44px hexagon, the name in 30px bold
  and one 24px sentence. Plaques share the width equally.
- Plaque states, each with its own outline, hexagon and words:
  - **On screen**: amber border, amber hexagon with the glow fill, bright
    name, "On screen · 2 warnings" (or "· all well", "· not answering"), and
    a 6px amber bar along the bottom that fills over the turn.
  - **Well**: card border, plain hexagon, "All well · 5 containers".
  - **Something wrong** (a coral warning): coral border, coral hexagon with
    "!", coral name, and the machine's worst warning as the sentence, in its
    short form ("backup-db is at 92% of its memory cap"). A machine that is
    only busy (amber warnings) keeps the plain plaque with its sentence.
  - **Not answering**: card border, dim dashed hexagon, dim name, "has not
    answered for 4 min", or "has not answered yet" under its address if it
    never has.
- To make room, the hive is drawn at 0.975 scale, the machine's own warnings
  are limited to two lines, and the deploys card no longer reaches into the
  bottom margin, so it shows one row less.
- When more warnings apply than there are lines, on this screen or on the
  one-machine screen, coral ones are listed first, each color in cell order.
  When all fit they stay in cell order.
- The hive, its warnings, the count card and the host cards belong to the
  machine on screen and cross-fade over 0.7s when it changes. The deploys
  card is not tied to a machine and does not move.
- Up to four machines each get a plaque, in their own order. With more, the
  shelf shows the machine on screen, then up to two machines that need
  attention, then one plaque "N more" with "All well", or in coral with "N
  need attention".
- Turns: 30s each; 60s for a machine with a coral warning. A machine that is
  not answering is passed over, because it has nothing new to show, and stays
  on the shelf.
- Remote: the shelf is one focus stop, below the sort chips and the deploys
  card. With focus, the plaque of the machine on screen takes the 3px
  `#FFF8EA` ring, 3px outside it. Left and right change machine at once,
  including to a machine that is not answering (its last reading), and pause
  the turns: the bar turns dim and the sentence reads "On screen · paused".
  The pause ends 2 minutes after the last key press.

Only the first machine in the config is treated as the CI box with expected
runners; the others never show a "missing" cell.

Not built: keeping the machine change and the deploys carousel from moving
in the same moment. Today the two timers run independently.

## Deploy rules

Source is the GitHub Actions REST API, fetched every 45 seconds per repository.

- Every workflow counts. Shown: runs still going, passed and failed. Hidden:
  skipped and cancelled. Nothing older than 7 days.
- A run is **active** while it is going and for one hour after it passes or
  fails. The card has two states:
  - **Active**, while any run is active. Only the repositories with active
    runs, and only those runs. The right column makes room (see "Layout").
  - **Maintenance window**, otherwise. Every repository, with its latest four
    runs.
- The card is a carousel with one page per repository. The header reads
  "Deploys · [repository]", then "past hour" or "latest runs", then one mark
  per page with the current one drawn wide and amber. It moves to the next
  page every 10 seconds. With one page it stays put, so a single repository
  deploying holds the card.
- Within a page: anything still running first, then most recently finished.
  Rows that do not fit whole are left out. A row is the workflow name, then
  the same detail line as before; the repository is in the header.
- GitHub reports no progress for a run. The bar is an estimate: time so far
  over the median duration of that workflow's recent successful runs, capped
  at 95%. No bar when the workflow has not been seen to pass.
- A failed run names the first job that failed.

The two states and the carousel were built straight from the owner's
description and have no mockup yet.

## Not designed yet

Run detail, and what the deploys card shows when GitHub fails for a reason other than the
token. They should follow this language. A table-style container screen exists
on the canvas ("Containers") as a possible drill-in, still in the earlier cold
palette.

The deploys card in the main mockup uses invented sample runs.
