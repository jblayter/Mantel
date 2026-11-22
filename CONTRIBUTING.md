# Contributing to Mantel

Thanks for helping. Mantel is a read-only wall display: it never starts, stops
or changes a container, a machine or a workflow, and contributions keep it
that way.

## Before you start

- Read [`docs/design/DESIGN.md`](docs/design/DESIGN.md). Every screen follows
  the Hive design language: 10-foot text (24dp body minimum), D-pad only with
  an obvious focus state, status never shown by color alone.
- For anything larger than a fix, open an issue first so we can agree on it.
- Work on a branch, not `main`.

## Build and test

See [Build](README.md#build) in the README. Before opening a pull request:

```bash
./gradlew :app:testDebugUnitTest
```

Check screen changes on a 1080p Android TV emulator with a screenshot
(`adb exec-out screencap -p > screen.png`) and attach it to the pull request.
If the change shows in a README screenshot (`docs/screenshots`), replace that
screenshot in the same pull request.

## Themes

New color themes are the easiest way to contribute: one JSON file, no code.
Follow [`docs/design/themes/README.md`](docs/design/themes/README.md); its
"Submit a theme" section is the checklist for the pull request. In short:

1. Add `docs/design/themes/<id>.json` and change nothing else.
2. `./gradlew :app:generateHiveTokens :app:testDebugUnitTest` passes.
3. Attach a screenshot of the dashboard in your theme (mock build), or of
   `docs/design/themes/preview.html` if you cannot run Android tools.
4. Title the pull request `Theme: <Name> (<layout>)`.

## Layouts

A layout is a new arrangement of the main screen, in Compose. Follow "Add a
new layout" in [`docs/design/layouts/README.md`](docs/design/layouts/README.md).
In short:

1. Open an issue with a mockup (Claude Design or HTML) before building.
2. Keep every rule in that file: 10-foot text, status in words, the overscan
   margin and drift, no large bright areas, every state, read-only, colors
   only from the theme.
3. Add the layout id to `theme.schema.json` and `MantelLayout`, the layout in
   `ui/layout`, and a default theme named after it.
4. Attach screenshots of one machine, three machines with one not answering,
   and both deploy states, and title the pull request `Layout: <Name>`.

## License

Mantel is under the [Apache License 2.0](LICENSE). By opening a pull request
you agree that your contribution is offered under the same license.
