# Brigade

**B**idouille **R**apide d'**I**mages et **G**estion d'**A**tmosphère pour **D**euxième **E**cran

A small APK tool to help me host my ttrpg games.

Brigade is a **GM-facing presentation console**, not a VTT. The physical tabletop stays
the game. Brigade's one job is:

> **The GM controls what the players see.**

The tablet is the GM surface; a wired HDMI display is the player surface. They are two
independent render targets of a single presentation state — not a mirror.

## Demo

Tested with a chinese secondary monitor "UPERFECT" and my Samsung Galaxy Tab S7+ (with DisplayPort Alternative Mode, that's a requirement).
It worked flawlessly, even with the app in the background. Failed to take a screenshot of the secondary monitor, but it's exactly as the preview screen.
In the tiled mode, the left part is just Obsidian, reading the same file folders.


<img width="2800" height="1752" alt="Screenshot_20260922_194930_Obsidian" src="https://github.com/user-attachments/assets/f361b5b1-73af-440c-833f-b296282c5272" />
<img width="2800" height="1752" alt="Screenshot_20260922_194753_Gamehost" src="https://github.com/user-attachments/assets/025c63bb-f14e-41a9-b49b-dcb1eb889d96" />


https://github.com/user-attachments/assets/caa28161-16bc-4219-b9c4-7d64ca203d6f




## v0.1

Select → preview → show.

- Pick a campaign folder through the Storage Access Framework; the grant persists.
- Browse it as a tree: enter subfolders, or tap any breadcrumb to jump straight back,
  including to the campaign root from any depth.
- Tap an image to *show it now* or park it in one of **six slots**.
- Tap a slot to put that image on the player display, from anywhere in the tree.
  Long-press a slot to empty it; assigning over one just overwrites it.
- **INFO** replaces the image with a full-screen campaign panel — location, in-world date,
  moon phase, whatever else you configure. It's a mode, not an overlay. With no campaign
  info configured it shows black, so it doubles as the blank control: *nothing specific,
  I'm preparing*.
- A live 16:9-or-whatever-your-display-actually-is preview of the player surface.

No import, no database, no account, no network, no proprietary format. Delete Brigade
and the campaign folder is still a perfectly ordinary folder.

The interface is French. See `DESIGN_REQUIREMENTS.md` §21.

## Transitions

Changing what the players see cross-dissolves rather than cutting. The default is a
**watercolor dissolve**: an animated threshold sweeps across a soft noise field, so the
incoming image bleeds in through an irregular front rather than fading uniformly.

It applies to *every* change of the player surface, uniformly — image to image, image to
the info panel, image to black — because the transition belongs to the surface, not to
images. There is no clamped special case.

The available transitions are `cut`, `fade` and `watercolor`. There is no transition editor
and there won't be one: a campaign picks one by name, and a new one is added to the app when
a real campaign needs it.

## Campaign file

Brigade reads one optional file at the campaign root: **`Campagne.md`**. Everything it
cares about lives in YAML frontmatter under a `brigade:` key, so the file stays a perfectly
ordinary Markdown note that Obsidian renders with properties, and the body is yours.

```markdown
---
brigade:
  transition: watercolor
  info:
    background: Fonds/parchemin.jpg
    show_lunar_state: true
    entries:
      - title: Le Renard de Jade
      - location: Auberge du Héron Noir
      - date: 1137-01-09
      - Saison: Printemps
---

# Notes de campagne

Tout ce qui suit l'en-tête est à toi. Brigade ne le lit pas.
```

- **`transition`** — `cut`, `fade` or `watercolor`. An unrecognised name falls back to `cut`,
  deliberately: a typo should be visible rather than silently pretty. No file at all means
  `watercolor`.
- **`info`** — what the INFO button shows full-screen. Entries appear in the order you write
  them. Most are plain label/value rows, but a few names are understood: `title` becomes the
  panel heading, `location` a headline, and `date` is written as an ISO date (`1137-01-09`)
  and rendered in French with its weekday. With `show_lunar_state: true` the moon is drawn in
  its phase for that date.
- **`background`** — an image behind the info panel, relative to the campaign root. Black if
  omitted.

### Dates before 1582 are Julian

`date:` follows the convention Wikipedia and historical lunar tables use: **Julian before
15 October 1582, Gregorian from then on.** Write the date exactly as your sources give it.

This matters more than it sounds. In the twelfth century the two calendars differ by seven
days, so a full moon listed as 9 January 1137 falls on 16 January in the proleptic Gregorian
calendar `java.time` uses. Brigade converts internally before computing the phase, and
displays the date you wrote — so the panel agrees with your notes and the moon agrees with
the sky.

The phase itself is a mean-synodic model and can be up to about a day out from a true new or
full moon, because it assumes a perfectly uniform cycle and the real orbit is elliptical.

The file is re-read when the campaign folder is opened, when Brigade comes back to the
foreground, when you press **Actualiser**, and each time you press INFO. So editing it in
Obsidian in the DeX split view and switching back is enough — there is nothing to reload. A missing,
malformed, or `brigade`-less file is completely normal and simply means the defaults apply;
Brigade never fails to start because of it. The body below the frontmatter is yours: Brigade
reads only the header, and never writes to this file.

The INFO button is enabled only when there is an `info:` block with something in it.

## Notes

Markdown notes appear in the browser alongside images and go into slots the same way.
Recalling a note shows the players **the first image it links** — and gives you a small bar
above the preview:

```
1137-01-09 · Jade-Fox · ⚔️ · Secte du Lotus
```

That bar is yours alone; nothing of it reaches the player display.

```markdown
---
element: Métal
faction: Secte du Lotus
---

# Jade Fox

![[Jade-Fox.png]]

Le magistrat sait qu'elle ment.
```

- **Name** is the filename, so you rename a character by renaming the file.
- **`element`** — one of `Terre` 🪨 `Feu` 🔥 `Eau` 💧 `Métal` ⚔️ `Bois` 🌳, matched ignoring
  case and accents. An unrecognised value shows as text, so a typo is visible.
- **`faction`** is shown in red.
- **Date** is the campaign's, from `Campagne.md` — not per-note. Slightly wrong semantically,
  consistently right everywhere.
- Keys are English, values are yours.

Both link syntaxes work: Obsidian's `![[Jade-Fox.png]]`, resolved by filename anywhere in the
campaign, and `![](../Portraits/Jade-Fox.png)`, resolved relative to the note. The first link
that actually **resolves** wins, so a broken link or a web URL is skipped rather than leaving
the players on black. A note linking no image at all shows black and still fills the bar —
which is the useful case for a secrets or lore note.

Notes show as filenames in the browser, without a thumbnail: drawing one would mean reading
every note in the folder to paint a single screen. Slots do show the image, since there are
only six.

## Your files stay yours

Campaign content is read where it lies and never copied, moved or modified. `Campagne.md` is
yours — Brigade reads it and never writes to it.

The single file Brigade creates is `.brigade/slots.json` in the campaign root, which holds
the six slots as paths relative to that root — readable, Git-diffable, and portable to
another device. Commit it or ignore it; Brigade does not care.

## Building

Open in Android Studio (JDK 17, `compileSdk 35`, `minSdk 30`). The Gradle wrapper JAR is
not committed yet — Android Studio generates it on first sync, and CI provisions Gradle
directly, so neither path needs it.

```bash
./gradlew test
```

```bash
./gradlew assembleDebug
```

## Installing on the tablet

Every push to `main` builds and publishes a debug APK to the rolling
[`latest`](https://github.com/BenjaminAndre/gamehost/releases/tag/latest) prerelease. The
URL is stable, so it can be bookmarked on the tablet: open it, download `app-debug.apk`,
install. You may need to allow installs from unknown sources for your browser.

## Development without the hardware

Most of the interesting logic is pure Kotlin and runs under `./gradlew test` with no
device. For the player display itself, **Settings → Developer options → Simulate
secondary displays** creates a real display with `FLAG_PRESENTATION` that the production
code path drives unmodified — no second code path, nothing to drift.

Without one, the app still works: the control bar is live and the preview keeps
rendering, with an «Aucun écran joueur» banner over it.

## Documents

- `DESIGN_REQUIREMENTS.md` — what Brigade is, and the ten traps it must not fall into.
- `ARCHITECTURE.md` — how that was translated into code, and the rules that hold it.
