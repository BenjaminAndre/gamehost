# Gamehost

A small APK tool to help me host my ttrpg games.

Gamehost is a **GM-facing presentation console**, not a VTT. The physical tabletop stays
the game. Gamehost's one job is:

> **The GM controls what the players see.**

The tablet is the GM surface; a wired HDMI display is the player surface. They are two
independent render targets of a single presentation state — not a mirror.

## v0.1

Select → preview → show.

- Pick a campaign folder through the Storage Access Framework; the grant persists.
- Browse it as a tree: enter subfolders, or tap any breadcrumb to jump straight back,
  including to the campaign root from any depth.
- Tap an image to *show it now* or park it in one of **six slots**.
- Tap a slot to put that image on the player display, from anywhere in the tree.
  Long-press a slot to empty it; assigning over one just overwrites it.
- **NOIR** blanks the player display.
- A live 16:9-or-whatever-your-display-actually-is preview of the player surface.

No import, no database, no account, no network, no proprietary format. Delete Gamehost
and the campaign folder is still a perfectly ordinary folder.

The interface is French. See `DESIGN_REQUIREMENTS.md` §21.

## Your files stay yours

Campaign content is read where it lies and never copied, moved or modified. The single
exception is `.gamehost/slots.json` in the campaign root, which holds the six slots as
paths relative to that root — readable, Git-diffable, and portable to another device.
Commit it or ignore it; Gamehost does not care.

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

- `DESIGN_REQUIREMENTS.md` — what Gamehost is, and the ten traps it must not fall into.
- `ARCHITECTURE.md` — how that was translated into code, and the rules that hold it.
