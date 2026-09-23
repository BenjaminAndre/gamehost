# Changelog

Brigade is versioned `major.minor.patch`. The patch bumps on anything that ships; gaps are
normal. `versionCode` is derived as `major * 10000 + minor * 100 + patch`.

Entries before 0.4.1 are reconstructed from the commit history and the design notes, and are
coarser than what follows.

---

## 0.4.2

- The GitHub repository was renamed to `brigade`. Updated the release link in `README.md`
  and pointed the git remote at the new URL.

## 0.4.1

Documentation only — no behaviour change.

- Rewrote `README.md` as a feature reference rather than a running history.
- Added this changelog.
- Removed `PLAN_V0.2.md` and `PLAN_V0.3.md`; their content lives here and in
  `DESIGN_REQUIREMENTS.md`.
- Started versioning properly. Previous releases all shipped as `0.1.0`.

## 0.4.0 — Brigade

Renamed, and given the one control a session proved it needed.

**Renamed from Gamehost to Brigade** — *Bidouille Rapide d'Images et Gestion d'Atmosphère
pour Deuxième Ecran*.

- New package `com.brigade` and a new `applicationId`, so it installs alongside the old app
  rather than upgrading it. The campaign folder must be re-picked once.
- The slot bank moved from `.gamehost/slots.json` to `.brigade/slots.json`, and the campaign
  frontmatter key from `gamehost:` to `brigade:`. Neither migrates — existing slots are
  re-assigned once.
- New launcher icon, cropped and fitted to an adaptive icon at every density.

**Incense timer.** A stick burning down the edge of the player display, so the table can see
how long is left without being told.

- `SABLIER` beside `INFO`: light 1, 2 or 5 minutes, add a minute, or put it out.
- No numerals for the players — a stick reads faster across a table and keeps the player
  surface free of application text. The GM gets the exact time on the button.
- Extending lengthens the stick in place rather than resetting it, so the players read it as
  mercy. Extending one that already went out lights a fresh minute.
- Burns on regardless of scene changes, INFO, or blanking — it is an overlay, not part of the
  picture. Never restored after a relaunch.

**`NOIR` removed.** `INFO` now doubles as the blank control: a campaign with no info
configured shows black, which is the *nothing specific, I'm preparing* state a separate
button was serving. One control, one meaning, and `blackout` is gone from the state entirely.

**Fixes**

- The preview could overflow its pane in wide-but-short windows and paint over the slot bar
  and the browser. `fillMaxWidth().aspectRatio()` fixes the width, so when the derived height
  exceeded the space available nothing satisfied the constraints and the modifier fell
  through to an unbounded size. The preview is now fitted against both dimensions explicitly.
- `Campagne.md` is re-read when Brigade returns to the foreground and when *Actualiser* is
  pressed, not only on INFO — so editing it in Obsidian beside Brigade needs no reload.

## 0.3.0 — Markdown notes

Notes became presentable content alongside images.

- Notes appear in the browser and go into slots like anything else.
- Recalling a note shows the players the first image it links **that resolves**, so a broken
  link or a web URL is skipped rather than blanking the display.
- Both link syntaxes: Obsidian's `![[Jade-Fox.png]]`, resolved by filename anywhere in the
  campaign, and `![](../Portraits/Jade-Fox.png)`, resolved relative to the note.
- A GM-only bar above the preview: campaign date, character name, element as an emoji,
  faction. Nothing of it reaches the player display.
- A note linking no image shows black and still fills the bar — the useful case for a lore or
  secrets note.

## 0.2.0 — Transitions and campaign info

- **Watercolor dissolve** between everything the player surface shows, driven by an animated
  threshold over a noise field rather than a uniform fade. `cut` and `fade` also available;
  a campaign picks one by name in `Campagne.md`.
- **`INFO`** — a full-screen campaign panel replacing the image: title, location, in-world
  date written out in French, and the moon drawn in its true phase for that date.
- **`Campagne.md`** — one optional file at the campaign root, read for configuration and
  panel content, never written to.
- Dates before 15 October 1582 are read as **Julian**, matching Wikipedia and historical lunar
  tables. In the twelfth century that is a seven-day difference, and getting it wrong would
  silently misdate both the panel and the moon.

## 0.1.0 — Select → preview → show

The first working version.

- Pick a campaign folder through the Storage Access Framework; the grant persists.
- Browse it as a tree, with a breadcrumb back to the campaign root from any depth.
- Six slots holding images by campaign-relative path, saved in the campaign folder.
- A live preview of the player surface on the tablet, at the real display's aspect ratio.
- Fullscreen output to a wired HDMI display via Android's `Presentation` API, with the tablet
  remaining fully interactive.
