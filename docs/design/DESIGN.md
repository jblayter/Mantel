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

Mockups are in `mockups/`: `State-Setup`, `State-Loading`, `State-Unreachable`,
`Deploys-TokenExpired` and `Deploys-NoRuns`. All of them reuse the main
screen's frame, header, cards and type. Nothing in them can be focused.

- **Not set up yet** (no config): the title in Fraunces, one lead sentence, a
  card with the commands to run, and an amber spinner line saying Mantel is
  looking for the file. A corner of empty dashed cells on the right. If a
  pushed file cannot be used, a coral warning line says why. The mockup shows
  one `adb push` command; the built screen shows two, because Android 11 and
  later refuse a push into the app's folder. This is an interim screen until
  the QR setup is designed.
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

Run detail, the QR first-run setup (TV QR screen and phone form), settings,
and what the deploys card shows when GitHub fails for a reason other than the
token. They should follow this language. A table-style container screen exists
on the canvas ("Containers") as a possible drill-in, still in the earlier cold
palette.

The deploys card in the main mockup uses invented sample runs.
