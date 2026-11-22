# Prompt: make a Mantel theme

For Claude Design or Claude Code. Paste everything below the line, then
replace the last paragraph with the look you want.

With Claude Design, add these files to the project first:
`docs/design/themes/README.md`, `theme.schema.json`, `preview.html`, the
theme of the layout you want (`hive.json`, `noc.json` or `neon.json`),
`docs/design/layouts/README.md`, that layout's screenshot from
`docs/design/layouts/previews`, and `docs/design/DESIGN.md`. Claude Code
reads them from the repository.

---

I want a new color theme for Mantel, an always-on Android TV wall display for
a home CI machine, read from across a room and shown for hours.

Read first:

- `docs/design/themes/README.md`: what a theme is, every color role and the
  rules a theme must meet. Follow it exactly.
- `docs/design/layouts/README.md`: the layouts, the rules every layout keeps,
  and what each color role paints in each layout.
- The theme of the layout I name below (`docs/design/themes/hive.json`,
  `noc.json` or `neon.json`): every role means what it means there. Look at
  that layout's screenshot in `docs/design/layouts/previews`.
- `docs/design/themes/theme.schema.json`: the format.
- `docs/design/DESIGN.md`: the Hive design language, for what each part of
  the screen is.

What I need back:

1. One theme file in exactly the format of `hive.json`: `$schema`, `id`,
   `name`, `description`, `author`, `layout` and `color` with every role and
   nothing else. Name it `<layout>-<name>` unless it is for Hive.
   `#RRGGBB` colors; only `chartFill` may be `rgba(...)`.
2. A table of the contrast ratios that rule 4 of the README asks for, so I can
   see each one clears 4.5:1. Change a color rather than miss one.
3. Two or three sentences on the choices: the accent, how the three kinds
   tell apart, how trouble reads.

Keep to colors. Do not change the layout, type, sizes or geometry, and do not
add roles. The ground must be dark (relative luminance at most 0.05): no light
themes. Keep Hive's relationships: missing is a dark hint of trouble, ended is
darker than base, glowHot is brighter than glow, inProgress is the accent.

If you are Claude Code in the Mantel repository: write the file to
`docs/design/themes/<id>.json`, run `./gradlew :app:generateHiveTokens` and fix
every problem it lists, then install the mock build on the emulator, switch to
the theme with
`adb shell am start -n com.blayter.mantel/.MainActivity --es theme <id>`, take
a screenshot and show it to me. Do not commit unless I ask.

If you are Claude Design: show the theme on `preview.html`'s sample screen.

The layout: <hive, noc or neon>.

The look I want: <describe it here, for example "a dark plum ground with a
rose-gold accent, calm enough for a bedroom at night">.
