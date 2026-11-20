# Proposed rules: on-TV setup and settings

Status: step 1 (machines, no machines, enter an address, nothing answered)
was approved by the owner on 2026-11-20 and is built. Its boards moved to
`docs/design/mockups` and its rules, as built, are in `DESIGN.md` under
"On-TV setup"; the sections below are kept as proposed, for the record.

Step 2 (deploys, code ran out, no repositories) and Ready were approved by
the owner on 2026-11-20 and are built; their boards moved to
`docs/design/mockups` too, the earlier drafts `Setup-GitHub` and
`Setup-Ready` were removed, and the rules as built are in `DESIGN.md`.

Settings was approved by the owner on 2026-11-20 and is built, opened with
the Menu button or by holding OK; its board moved to `docs/design/mockups`
and its rules as built are in `DESIGN.md`.

What is left here is proposed, not approved, not built: the password prompt.
Its artboard is in this folder and on the design canvas under "On-TV setup
and settings". It has not been checked rendered.

The several-machines shelf from the same brief was approved and built; its
rules are in `DESIGN.md` under "Several machines — the shelf".

## New values

Everything else uses existing tokens. No new colors.

| Name | Value | Used for |
| --- | --- | --- |
| `qr.size` | 374px, 22px quiet margin | QR tile, `Text secondary` ground with ground-colored squares. |

`focus.offset` (3px), used for the ring around buttons here, was added to
`tokens.json` with the shelf.

## Needs a decision

- Password entry: the proposal has the TV serve a small page on the home
  network during setup, so the password is typed on a phone.
- `mantel.example/start` is a placeholder; a real address is needed. The
  GitHub App's page is decided: `github.com/apps/mantel-ci-cd-display`.
- How settings is opened from the dashboard. Proposed: the Menu button, or
  holding OK.

## On-TV setup

All screens share one frame so nothing jumps between steps: the header holds
two hexagon pips and "Setup · step 1 of 2 · machines"; the title sits at the
same height on every screen; the buttons sit on the bottom edge of the left
column; the right column holds one supporting thing.

- Buttons are cards with a 24px bold label. The main action is amber fill with
  dark text. Focus is the 3px `#FFF8EA` ring, 3px outside the button.
- A QR code is the one large light area allowed, on these screens only.
- Trouble is a coral triangle line with a sentence, as on the dashboard.
- Nothing here can change a machine or GitHub.

### 1 · Machines

- The comb is the picker. Each machine that answers becomes a cell: state word
  ("WATCHING" with a tick, or "NOT WATCHING"), container count as the number,
  name, "containers". Watching: amber 6px outline and glow. Not watching:
  plain outline, dim text. Places not yet filled are dashed empty cells.
- The right column is a card for the cell in focus: name, address, threads,
  memory, containers, and what OK will do. The list of facts is shown once,
  for one machine at a time.
- First focus: the first cell. If exactly one machine answered it is already
  watched and focus starts on "Watch 1 machine".
- Moves: the spinner, and cells appearing as machines answer.

### 1 · No machines

- One sentence on what Glances is, a three-step card, the QR code, and a
  spinner line: the search repeats every 10 seconds by itself.
- First focus: "Look again now".
- `mantel.example/start` is a placeholder; a real short address is needed.

### 1 · Enter an address

- The first three numbers are filled in from the TV's own address and shown
  dim; the person types only the last one. "Change the first part" unlocks the
  rest for another network.
- The port is a quiet 24px label inside the field, changed on request.
- First focus: the "5" key in the middle of the pad.

### 1 · Nothing answered

- The same screen: the field takes a coral border, and under it a coral line
  "192.168.1.57 did not answer on port 61208." with one sentence on what to
  check. No command is shown.
- First focus: "Check again".

### 1 · Password

- The password is typed on a phone. The TV shows a QR code, its own local
  address and a four-digit number; the phone page asks for that number and the
  password. "Type it with the remote" is the fallback.
- First focus: "Skip this machine", so setup can go on without it.
- Needs a decision: this means the TV serves a small page on the home network
  during setup.

### 2 · Deploys

- Two numbered steps, the code in display-xl, a spinner line with the minutes
  left, and the QR code.
- First focus: "Skip, no deploys for now".

### 2 · Code ran out

- The old code stays, dim and struck through, so the person sees which one
  died. The QR tile becomes a dashed empty tile: there is nothing to scan.
- First focus: "Get a new code".

### 2 · No repositories

- Says who is signed in and why nothing shows, then a three-step card and QR
  code to the app's page on GitHub. It checks again by itself.
- First focus: "Check again now". `github.com/apps/mantel` is a placeholder.

### Ready

- Machines on the left and deploys on the right, the same split as the
  dashboard. Each line has a green tick.
- First focus: "Start". Nothing starts on a timer.

## Settings

- Machines card on the left: one focusable row per machine with its address
  and state, and "Remove" at the right; then "Add a machine", which opens
  setup step 1. Deploys card on the right: who is signed in, the repositories
  (not focusable; they are chosen on GitHub), and "Sign out of GitHub".
- "Remove", "Sign out" and "Start over" each ask once before acting. They
  change only what this display stores.
- First focus: "Back to the display".
- Not decided: how settings is opened from the dashboard. Proposed: the Menu
  button, or holding OK.
