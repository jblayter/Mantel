# Prompt for Claude Design: Mantel setup and several machines

Paste everything below the line into Claude Design, with the files listed
under "Attached" added to the project.

---

I'm continuing the design of Mantel, an open-source Android TV app: an
always-on wall display for a home lab machine. It shows live machine stats and
containers from Glances, and GitHub Actions deploys. The approved design
language is called "Hive" and the main screen is approved and built.

I'm getting the app ready for the Google Play and Amazon stores. Two things
need design, and the first drafts were made in code without a designer's eye.
Treat them as a record of the decisions, not as a look to refine.

## Attached

- `DESIGN.md`: the Hive design language and every rule. Read it first.
- `tokens.json`: type scale, spacing, hex geometry, and `themes/hive.json`:
  the colors. Use only these.
- `Hive.dc.html`: the approved main screen. This is the quality bar.
- `State-*.dc.html`, `Deploys-*.dc.html`: approved setup, loading and failure
  states.
- `Header-Machines.dc.html` and its PNG: a draft I rejected (see job 1).
- `Setup-*.dc.html` and their PNGs: five draft setup screens I have not
  reviewed closely (see job 2).

## Job 1: the dashboard with several machines

One TV can now watch several machines. I decided that the dashboard shows one
machine at a time and takes turns. The draft only changed the header into a
row of machine names, and I think it looks horrible. Before designing, ask me
what I disliked about it, then propose two or three different directions as
full 1920×1080 screens, not header strips.

What the design has to do:

- With one machine, the screen stays exactly as approved.
- It must be obvious from across the room which machine is on screen, and how
  many there are.
- A machine that is in trouble, or not answering, must be visible while a
  different machine is on screen.
- The hive, the warnings, the container count and the host charts belong to
  the machine on screen. The deploys card is not tied to a machine and has its
  own carousel already; two things rotating at once must not feel busy.
- No interaction is needed. With the remote, a person can change machine and
  pause the turns, and the focus state must be obvious.
- Open for you to propose: how long a turn is, whether a machine in trouble
  gets a longer turn, and what happens with more than four machines.

The honeycomb is the signature of this design. A row of text tabs ignores it;
consider whether machines could be shown in the Hive language.

## Job 2: setup on the TV

Today setup is a config file pushed with adb. Store users need to set up with
only the TV remote and their phone. The flow is decided; the look is a draft.

1. **Machines.** The TV searches its own network for Glances and lists what
   answers (name, address, threads, memory, container count). The person
   picks one or more.
2. **No machines.** Explains Glances in a sentence and sends the person to
   their phone, by QR code, for the one command that starts it. A long
   command is never shown on the TV to be retyped.
3. **Enter an address.** A number pad for a machine the search cannot reach.
4. **Deploys, optional.** GitHub device sign-in: a QR code, the address
   `github.com/login/device` and a short code. Skipping must be easy.
5. **Ready.** Lists the machines and repositories, then starts.

Review these five as a set and redesign what is weak. Then design the states
that do not exist yet:

- Nothing answered at the address that was entered.
- Glances asks for a password.
- Signed in to GitHub, but the Mantel app can read no repositories.
- The sign-in code expired.
- A settings screen that reopens all of this later: add or remove a machine,
  sign out of GitHub, start over.

## Rules that do not change

- Android TV, 1920×1080 landscape, 96px side and 54px top and bottom margins
  for overscan.
- D-pad only. Every focusable thing needs an obvious focus state (today a 3px
  `#FFF8EA` border).
- Read from across a room: body text is 24px or larger. Labels inside a hex
  cell are the one exception.
- Warm walnut ground, never black. Amber means busy or in progress. Coral
  means something is wrong. Status never relies on color alone.
- Craftsman, not glossy: 8px corners, 3px solid borders, no decorative
  gradients, glows or drop shadows.
- Fraunces for the wordmark and key numbers, DM Sans for everything else. No
  monospace.
- The app is read-only. No control may start, stop or change anything on a
  machine or in GitHub.
- The screen is on for hours. No large bright static areas on the dashboard.
  The setup screens are short-lived, so a QR code on a light tile is allowed
  there.
- Plain sentences for trouble ("attic-nas has not answered for 4 min"), not
  codes or badges alone.
- Nothing long is typed with the remote, and a token is never typed.

## What I need back

- One artboard per screen and state, 1920×1080, as `.dc.html` like the
  attached ones.
- Any new color, size or spacing called out by name and value, so it can be
  added to `tokens.json`. Prefer the existing ones.
- For each screen, a few sentences of rules in the style of `DESIGN.md`:
  what each state looks like, what has focus first, and what moves.
