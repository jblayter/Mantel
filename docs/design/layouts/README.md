# Mantel layouts

A layout is how the main screen is arranged: what sits where, its shapes and
its type. A theme (`../themes`) is the colors. Each theme names one layout,
so every layout can have as many color themes as people write.

| Layout | Id | Default theme | What it is |
| --- | --- | --- | --- |
| Hive | `hive` | `hive.json` (also `moss`, `nightfall`) | The approved design: honeycomb cells, craftsman cards, Fraunces and DM Sans. `../DESIGN.md`. |
| NOC | `noc` | `noc.json` (also `noc-amber`, `noc-contrast`, `noc-solar`) | A network-operations wall: severity counts, a table of every container worst first, host tiles, an event list, every deploy in one table. |
| Neon | `neon` | `neon.json` (also `neon-synthwave`, `neon-ice`, `neon-phosphor`) | Cyberpunk: chamfered tiles with lit outlines and segment bars, a CPU ring, a deploy log, monospace throughout. |

Screenshots from the 1080p emulator are in `previews/`: each layout
on the sample screen, NOC and Neon with a machine that has stopped
answering, and every NOC and Neon theme (`previews/<theme id>.png`).

| NOC theme | Look |
| --- | --- |
| NOC | Graphite and steady blue. |
| Amber Ops | Warm amber on charcoal, like an old ops console. |
| High Contrast | Black, white and bold signal colors, for far away or low vision. |
| Solar | Deep teal with warm yellow, after the Solarized palette. |

| Neon theme | Look |
| --- | --- |
| Neon | Magenta, cyan and acid on near black. |
| Synthwave | Sunset orange and electric cyan on deep purple. |
| Ice | Cold cyan and violet on blue-black. |
| Phosphor | Phosphor green terminal on black, with cyan and yellow kinds. |

Settings has a Look card: a row of layouts and a row of the chosen layout's
themes, the layout's own theme first. Choosing a layout opens it on the
theme whose id is the layout's id.
From a computer: `adb shell am start -n com.blayter.mantel/.MainActivity --es theme noc`.

Only the main screen has layouts. Setup, settings, loading and the machine
shelf keep the Hive arrangement and take the theme's colors (the shelf uses
`cell.glow` and `cell.strokeEnded` in every layout).

## Rules every layout keeps

These are the product rules from the repository's `CLAUDE.md` and
`DESIGN.md`. A layout that breaks one is not merged.

1. **Readable from across the room with no interaction.** Body text 24dp or
   more; small labels inside a tile or cell may be 20dp, as hex cell labels
   are. Nothing needs focus to be read.
2. **Status never by color alone.** Every state has a word, a mark or a line
   style too: MISSING, NEAR CAP, BUSY, ENDED, OK; RUNNING, PASSED, FAILED;
   dashed outlines for missing and ended.
3. **Inside the overscan margin, with the slow drift.** Use `ScreenFrame`
   (`ui/HiveFrame.kt`), which every layout shares.
4. **No large static bright areas.** Bright color is lines, text and small
   marks. Fills are `surface`, `track` and the `cell` colors.
5. **Every state the Hive screen handles:** a machine that stopped answering
   (dim the machine's parts, say how long in words), the warnings, active and
   maintenance deploys, waiting, token problems and no recent runs, and
   several machines (the shelf under the screen, the machine's parts
   cross-fading on a change, deploys not moving).
6. **Read-only.** No control that starts, stops or changes anything.
7. **Colors only from the theme.** Every color is a role from
   `../themes/theme.schema.json`, read through `HiveTokens.Color`, so every
   theme of the layout works and switching is live.

## How each layout uses the color roles

Every theme has the same roles, so the same file format works for every
layout. What a role paints depends on the layout:

| Role | Hive | NOC | Neon |
| --- | --- | --- | --- |
| `ground` | Screen | Screen | Screen |
| `surface`, `surfaceBorder` | Cards | Panels | Deploy log, the "more" tile |
| `amber` (accent) | Wordmark, chart lines, main buttons | Wordmark, sparklines | Wordmark, headings, CPU ring, CPU trend |
| `amberSoft` | Busy cells | — | Memory and load segment bars |
| `amberDeep` | Running deploy stripes | — | — |
| `kind.*` | Cell outlines | Kind column | Tile outlines and segment bars |
| `status.*` | Deploy icons, warnings | Status words, severity counts, events | Tags in the log, alerts, header status |
| `cell.base` | Cell fill | Striped table rows | Tile fill |
| `cell.baseMissing` | Missing cell | Missing row | Missing tile |
| `cell.glow`, `glowHot` | Cell glow | — | — |
| `track` | Empty bar | Unlit segments | Unlit segments, ring track |
| `chartFill` | Under host charts | Under sparklines | Under the CPU trend |

So a theme made for one layout can be copied for another, and changing its
`layout` is enough to try it. Still check it there: Neon draws `kind` colors
as long bright outlines, NOC uses them only as words.

## Make a theme for a layout

Exactly as `../themes/README.md` describes, with `"layout"` set:

```json
{
  "$schema": "theme.schema.json",
  "id": "neon-vapor",
  "name": "Vapor",
  "description": "Teal and pink on deep violet.",
  "author": "your-github-login",
  "layout": "neon",
  "color": { "...": "every role" }
}
```

Start from the layout's own theme (`noc.json`, `neon.json`) and change it.
Name a theme for a layout `<layout>-<name>.json` so the folder groups them.

## Add a new layout

A layout is Compose code, so it is a bigger change than a theme. Open an
issue first with a sketch or a Claude Design mockup, and wait for agreement
before building.

1. **Design it.** Make an HTML mockup of the 1920 x 1080 screen in
   `../mockups` (`Layout-<Name>.dc.html`), from the sample data in
   `Hive.dc.html`, covering the normal screen, a stale machine and the
   active deploys state. With Claude Design: attach `DESIGN.md`, this file,
   `../themes/hive.json` and `previews/`, and ask for "a new Mantel layout
   that keeps every rule in layouts/README.md".
2. **Add the id** to the `layout` enum in `../themes/theme.schema.json` and to
   `MantelLayout` in `app/src/main/java/com/blayter/mantel/ui/theme/MantelLayout.kt`.
3. **Build it** in `app/src/main/java/com/blayter/mantel/ui/layout/<Name>Dashboard.kt`
   with the same parameters as `NocDashboard`, and add it to the `when` in
   `ui/DashboardScreen.kt` (the compiler points there). Reuse `ScreenFrame`,
   `LayoutParts.kt` and the pure rules in `domain/` (`deployRowsAtOnce`,
   `flagOf`, `severityCounts`, `warningsFor`); add new pure rules to
   `domain/` with unit tests.
4. **Add its default theme**, `../themes/<id>.json` with `"layout": "<id>"`.
5. **Check every state** on the 1080p emulator with the mock build: one
   machine, three machines (`-Pmantel.mockMachines=3`, which includes a
   machine that stops answering after 20 s), and both deploy states (the
   mock loops between them every five minutes).
6. **Update** the table at the top of this file, the role table, and
   `previews/` with the screenshots.

A layout pull request is titled `Layout: <Name>` and includes the mockup, the
screenshots of every state from step 5, and passing
`./gradlew :app:testDebugUnitTest`.

## Status

NOC and Neon were built from the owner's description, not from a Claude
Design mockup, and have not been reviewed on a TV. Hive is the approved
design.
