# Mantel themes

A theme recolors the whole display: the main screen, setup and settings. It
is colors only, plus the name of the **layout** it is for: Hive, NOC or Neon
(see `../layouts/README.md`). Layouts are code; themes are these JSON files,
so any layout can have as many color themes as people write. Sizes and type
come from the layout (`../tokens.json` for Hive), never from a theme.

Each theme is one JSON file in this folder. Every `.json` file here except
`theme.schema.json` is built into the app and offered in settings. There is no
registry to edit and no Kotlin to write.

| File | What it is |
| --- | --- |
| `hive.json` | The approved Hive palette and the default theme. The reference for every role. |
| `moss.json`, `nightfall.json` | Example themes for the Hive layout. |
| `noc.json` | The NOC layout's theme: graphite and blue. |
| `neon.json` | The Neon layout's theme: magenta, cyan and acid on near black. |
| `theme.schema.json` | The format, as a JSON Schema. The build reads the color roles from it. |
| `preview.html` | Open in a browser, load or paste a theme, see it on a sample of its layout with its checks. No Android tools needed. |
| `PROMPT.md` | A prompt that has Claude Design or Claude Code make a theme in this format. |

## The format

```json
{
  "$schema": "theme.schema.json",
  "id": "nightfall",
  "name": "Nightfall",
  "description": "Deep blue slate with a cool cyan accent, for a dark room.",
  "author": "your-github-login",
  "layout": "hive",
  "color": {
    "ground": "#121A25",
    "surface": "#1C2634",
    "...": "every role below",
    "kind": { "runner": "#B4A6E8", "database": "#9ED19A", "job": "#E8E4DA" },
    "status": { "inProgress": "#6CC8D8", "success": "#9ED19A", "trouble": "#FF8A7A", "inactive": "#8D9BAE" },
    "cell": { "base": "#1E2938", "baseMissing": "#3A1E26", "baseEnded": "#18202B", "glow": "#1D5866", "glowHot": "#26798A", "strokeEnded": "#4A5668" },
    "focus": "#F7FAFF"
  }
}
```

- **`id`**: the file name without `.json`, lowercase letters, digits and
  dashes (`nightfall.json` has `"id": "nightfall"`). The TV stores it, so a
  published theme is never renamed.
- **`name`**: the label on its settings button, at most 16 characters.
- **`description`**: one sentence settings shows under the name, at most 80
  characters.
- **`author`**: your name or GitHub login.
- **`layout`**: `hive`, `noc` or `neon`; leave it out for `hive`. The theme
  is offered under that layout in settings. A theme for one layout is
  usually named `<layout>-<name>.json`.
- **`color`**: every role, and nothing else. Colors are `#RRGGBB`; only
  `chartFill` may be `rgba(r,g,b,a)`.

Editors that read JSON Schema (VS Code and others) complete and check the
roles as you type, from the `$schema` line.

## Color roles

The Hive column is the approved palette; use it to judge what a role is for.
"Used for" is the Hive layout; `../layouts/README.md` has what each role
paints in NOC and Neon.

| Role | Hive | Used for |
| --- | --- | --- |
| `ground` | `#2B1E15` | Screen background. Must be dark. |
| `surface` | `#3A2A1E` | Cards, unselected chips, buttons |
| `surfaceBorder` | `#5C4431` | The 3px card and button border |
| `track` | `#503B2B` | Empty part of bars |
| `text` | `#F2E9DA` | Body text |
| `textBright` | `#FFF8EA` | Key numbers and names |
| `textSecondary` | `#EADFCB` | Supporting lines, button labels |
| `textMuted` | `#D2C0A6` | Labels, captions, card headings |
| `textSoft` | `#E0D3BD` | Unselected chip label, memory line in a cell |
| `textDim` | `#AD9D87` | Ended and inactive things |
| `textOnAmber` | `#1A140C` | Text on the accent (main button, selected chip) |
| `amber` | `#F0B45A` | The accent: wordmark, chart lines, main button, selected chip. Named for Hive; it need not be amber. |
| `amberDeep` | `#C98F3C` | Stripes of a running deploy's bar |
| `amberSoft` | `#FFD08A` | Lighter accent |
| `chartFill` | `rgba(240,180,90,0.22)` | Area under the host charts: the accent at about 0.22 alpha |
| `kind.runner` | `#93BBD0` | Outline of a CI runner's cell |
| `kind.database` | `#AFC487` | Outline of a job database's cell |
| `kind.job` | `#EDE3D0` | Outline of any other container's cell |
| `status.inProgress` | `#F0B45A` | Running. Usually the accent. |
| `status.success` | `#AFC487` | Passed, the ticks of a ticked list |
| `status.trouble` | `#FF8A68` | Failed, missing runner, memory near cap, not answering |
| `status.inactive` | `#AD9D87` | Skipped, waiting. Usually `textDim`. |
| `cell.base` | `#3D2C20` | Fill of a normal cell |
| `cell.baseMissing` | `#3F2018` | Fill of a missing runner's cell, a hint of `trouble` |
| `cell.baseEnded` | `#2F2118` | Fill of a container that ended |
| `cell.glow` | `#8F5F1F` | The glow rising in a busy cell |
| `cell.glowHot` | `#B4701C` | The glow of a very busy cell |
| `cell.strokeEnded` | `#6B5442` | Dashed outline of an ended cell |
| `focus` | `#FFF8EA` | The ring around the focused button |

## Rules every theme meets

The build checks these and stops with every problem listed, in plain words,
when a theme breaks one. `preview.html` runs the same checks.

1. **Every role, nothing else.** A missing or unknown role is an error.
2. **The `id` matches the file name.**
3. **A dark ground.** `ground` has a relative luminance of at most 0.05 (Hive's
   is 0.015). The TV shows it for hours; light themes are out of scope.
4. **Readable from across the room.** Every text role (`text`, `textBright`,
   `textSecondary`, `textMuted`, `textSoft`, `textDim`), the accent, every
   `kind` and `status` color and `focus` has a contrast of at least 4.5:1 on
   `ground`, `surface`, `cell.base`, `cell.baseMissing` and `cell.baseEnded`.
   `textOnAmber` has at least 4.5:1 on `amber`.

And these, which a check cannot judge, so a reviewer will:

5. **Kinds tell apart.** Runner, database and job outlines look different
   from each other at a glance.
6. **Trouble looks like trouble.** `status.trouble` is warm and alarming and
   unlike `success` and `inProgress`. The screen always pairs status with a
   word or icon too, but color should not mislead.
7. **No large bright areas.** `surface`, `track` and the cell fills stay dark;
   brightness belongs to text, lines and the accent.
8. **Keep the relationships.** Missing is a darker hint of trouble; ended is
   darker than base; glowHot is brighter than glow; inProgress is the accent.

## Make a theme by hand

1. Copy the theme of the layout you want: `nightfall.json` (Hive),
   `noc.json` or `neon.json`. For example
   `cp docs/design/themes/neon.json docs/design/themes/neon-dusk.json`.
2. Set `id` to `neon-dusk`, keep `layout`, and set a `name`, `description`
   and `author`.
3. Change the colors. Start with `ground`, `surface` and the accent; then
   the text ramp from brightest (`textBright`) to dimmest (`textDim`); then
   `kind`, `status` and `cell`.
4. Check it in `preview.html` (open the file in a browser, then "Load a
   theme file"), or build: `./gradlew :app:generateHiveTokens` prints every
   problem.
5. See it on the TV or emulator (below).

## Try a theme on a TV or emulator

Build and install (the mock build needs no Glances machine):

```bash
./gradlew :app:installDebug -Pmantel.mock=true
```

Switch to the theme by its id. The display recolors at once and remembers it:

```bash
adb shell am start -n com.blayter.mantel/.MainActivity --es theme neon-dusk
```

Take a screenshot to compare with Hive:

```bash
adb exec-out screencap -p > neon-dusk.png
```

On a display that is set up, the theme is also chosen in settings (Menu, or
hold OK): the Look card has a row of layouts and a row of the chosen
layout's themes. "Start over" goes back to Hive.

## With Claude Code or Claude Design

Both can make a theme from a description such as "a dark plum theme with a
rose accent for the Neon layout". `PROMPT.md` is the prompt; it tells them to read this file,
`hive.json` and `theme.schema.json`, to keep every rule above, and to answer
with one theme file.

- **Claude Code**, in this repository: ask it to "make a new Mantel theme"
  and describe the look. It reads this folder (CLAUDE.md points here), writes
  `docs/design/themes/<id>.json`, runs `./gradlew :app:generateHiveTokens`
  until the checks pass, and can screenshot it on the emulator.
- **Claude Design**: add `README.md`, the layout's theme (`hive.json`,
  `noc.json` or `neon.json`), `theme.schema.json`, `preview.html`,
  `../layouts/README.md`, the layout's screenshot from `../layouts/previews`
  and `../DESIGN.md` to the project, paste `PROMPT.md`, and
  describe the look. Paste the theme it gives you into `preview.html`, then
  save it as `docs/design/themes/<id>.json`.

A theme made either way is an ordinary file: check and submit it like one
made by hand.

## Submit a theme

Themes are welcome by pull request. One theme per pull request.

1. Fork the repository and make a branch, for example `theme/dusk`.
2. Add `docs/design/themes/<id>.json`. Change no other file: the build picks
   the theme up by itself.
3. Run the checks and the tests:

   ```bash
   ./gradlew :app:generateHiveTokens :app:testDebugUnitTest
   ```

4. Take two screenshots on the 1080p emulator or a TV, using the mock build
   so the screen is the same for everyone: the dashboard in the theme's layout
   (`--es theme <id>` as above) and, if you can,
   settings. If you cannot run Android tools, a screenshot of `preview.html`
   is enough.
5. Open the pull request titled `Theme: <Name> (<layout>)`, with the screenshots and
   one line on what the theme is for (a dark room, a color-blind friendly
   kind set, a match for a living room).

A pull request is merged when:

- the build passes (rules 1 to 4),
- a reviewer agrees with rules 5 to 8 from the screenshots,
- the name is not already taken and is not a brand or trademark,
- it is the author's own work, offered under the project's Apache 2.0 license.

Changes to Hive itself (`hive.json`) are design changes, not new themes, and
go through the design process in `../DESIGN.md` instead. Adding or renaming a
color role changes every theme and the app's code; open an issue first. A
new layout is code, not a theme: see "Add a new layout" in
`../layouts/README.md`.
