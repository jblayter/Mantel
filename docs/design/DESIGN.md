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
  (fills the rest).
- Spacing steps: 8, 10, 16, 20, 24, 32.
- Card: surface fill, 3px surface-border, 8px radius, 18–26px padding.
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

Each cell, top to bottom: kind and uptime (`RUNNER · 4 min`), cores in use
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
(2.6s), the spinner and moving stripes on an in-progress deploy. Nothing else
animates.

## Focus (D-pad)

The main screen needs no interaction. Where something is focusable (sort chips,
later a cell or deploy row), focus is a 3px `#FFF8EA` border. Selected chip is
amber fill with dark text.

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

## Not designed yet

Run detail, first-run setup (TV QR screen and phone form), settings, and the
loading, host-unreachable, token-expired and no-recent-runs states. They should
follow this language. A table-style container screen exists on the canvas
("Containers") as a possible drill-in, still in the earlier cold palette.

The deploys card in the mockup uses invented sample runs.
