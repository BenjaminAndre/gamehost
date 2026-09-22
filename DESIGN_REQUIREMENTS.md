
# Gamehost

## 1. Product concept

Gamehost is a **GM-facing presentation and interaction layer for tabletop RPGs**.

It is not a VTT.

A VTT attempts to represent the complete state of a tabletop game digitally: characters, maps, tokens, combat, rules, dice, initiative, etc.

Gamehost has a deliberately narrower purpose:

> **The GM controls what the players see and hear.**

The physical tabletop remains the actual game environment.

Gamehost is closer to a **director's console** than a virtual tabletop.

The first version has exactly one job:

> **Show a picture to the players.**

Future capabilities may include music, sound effects, Markdown handouts, scene composition, etc., but these must not distort the architecture of the initial product.

---

# 2. Core principles

### 2.1 Files are the content

Gamehost does not own campaign data.

Campaign content exists as ordinary files in ordinary folders.

Initially:

* PNG
* JPG/JPEG
* WebP
* Markdown (`.md`)

Potential future formats:

* audio
* video
* PDF
* SVG
* other user-readable media

The application is a **view and interaction layer over those files**.

The user should be able to delete Gamehost and retain a perfectly usable campaign folder.

### 2.2 Open formats

Do not create a proprietary campaign database.

Do not require importing content into Gamehost.

Do not duplicate campaign assets into an application-specific library.

Do not make the application's internal data model the source of truth.

A campaign should remain usable with:

* Obsidian
* a normal file manager
* a text editor
* an image viewer
* Git
* any future application

### 2.3 Git-friendly by design

The campaign directory should be suitable for ordinary Git version control.

Gamehost should therefore avoid modifying campaign files unless explicitly requested.

If Gamehost needs application-specific state, keep it separate from campaign content.

Prefer a small optional `.gamehost/` directory for ephemeral/session-specific state rather than embedding metadata into every campaign file.

The application must never require a proprietary database for normal operation.

---

# 3. Platform target

Initial target:

* Android
* Samsung Galaxy Tab S7+
* Samsung DeX
* wired external display through USB-C/HDMI
* external display approximately 16:9
* external display is not touch-capable

Samsung explicitly supports the Tab S7/S7+ for DeX dual mode and external HDMI displays.

The application must therefore treat the tablet display and player display as **two distinct presentation surfaces**.

The external display is not a mirror of the tablet.

---

# 4. Two-display model

The application has two conceptual surfaces.

## GM surface

Displayed on the Galaxy Tab.

Contains:

* folder navigation
* file browser
* thumbnails
* controls
* notes in future versions
* current player-display preview
* session controls

This surface is interactive.

## Player surface

Displayed on the external HDMI display.

Contains:

* the currently presented visual
* black/blank state when appropriate

It must contain:

* no controls
* no file browser
* no thumbnails
* no cursor
* no Android UI where avoidable
* no GM notes
* no interaction requirement

The player display is output-only.

Samsung documents targeting the external display and creating a separate window for it in Samsung Dual Mode.

---

# 5. Player preview

The GM surface must contain a **live preview of the player surface**.

The preview should use a 16:9 aspect ratio.

The preview is not merely a preview of the currently selected file.

It represents the actual presentation state.

For example:

If the player display is showing:

`Portraits/Jade-Fox.png`

the GM preview shows the same image.

If the player display is black, the GM preview is black.

If future versions apply cropping, scaling, transitions, overlays, etc., the preview should represent the resulting player-facing state.

Conceptually:

```text
              PRESENTATION STATE
                      │
             ┌────────┴────────┐
             ▼                 ▼
        GM preview         Player output
          16:9               16:9
```

There should be one presentation state, with two render targets.

Do not implement the preview and external output as two independent pieces of business logic.

### 5.1 The preview never stops rendering

The GM preview renders the presentation state in **every** display state, including when no player display is connected.

A missing player display is signalled by an overlay **on top of** the live preview — never by replacing it, hiding it, or substituting a placeholder.

The GM must be able to keep composing with the cable out: change slots, blank, and see the result. When the display reappears it comes up already showing the composed state.

This also removes the last case in which the preview would stop being a renderer of presentation state.

---

# 6. Initial UI

The first version should be intentionally simple.

Suggested layout:

```text
┌────────────────────────────────────────────┐
│ Folder / navigation (breadcrumb)           │
├────────────────────────────────────────────┤
│                                            │
│ Content browser                            │
│                                            │
│ [img] [img] [img] [img]                    │
│ [img] [img] [img] [img]                    │
│                                            │
├────────────────────────────────────────────┤
│                                            │
│              PLAYER PREVIEW                │
│         (actual player display ratio)      │
│                                            │
├────────────────────────────────────────────┤
│  [BLACK]  [1][2][3][4][5][6]               │
└────────────────────────────────────────────┘
```

The exact visual design can evolve.

The architecture must not depend on this exact layout.

## 6.1 The slot bank

The control bar holds a **blank control plus six slots**, each slot showing a mini-preview of the image assigned to it.

* **Assigning:** in the content browser, tapping an image opens a menu offering *Show now* or *Slot 1…6*.
* **Recalling:** tapping a slot in the control bar presents that image immediately.
* **Clearing:** long-pressing a slot empties it. Assigning to an occupied slot overwrites it.
* *Show now* is transient: it changes what the players see without touching the bank.
* The **blank/black control is permanent** and is not a slot.

There is no previous/next navigation. The slot bank replaces it.

This makes Trap 4 structural rather than aspirational: tapping a thumbnail no longer touches presentation state at all, so *what the GM is browsing* and *what the players see* are separated by the interaction model itself, not merely by discipline. The GM can browse the entire campaign mid-scene with no risk of revealing anything.

The slot bank is also what makes a deep campaign tree usable at the table: a slot is resolved independently of where the GM is currently browsing, so one tap reaches an image several folders away.

The currently live slot (or the blank state) must be visually indicated. That indicator is a readout of **presentation state**, not of what was last tapped.

### Slot persistence

The bank is **application state that points at campaign content**, so it is stored per campaign in the optional `.gamehost/` directory described in §2.3:

```text
<campaign root>/.gamehost/slots.json
```

One bank per campaign root, shared across every subfolder. Navigating the tree never changes which bank is on screen.

Slots must reference content by **path relative to the campaign root**, never by document URI. Document URIs are provider-specific and survive neither a folder move, a reinstall, nor being opened on another device — which would defeat the reason for storing the file with the campaign at all.

A slot whose path no longer resolves must render as **missing**, distinctly from empty. If the GM renamed a file they should see that, not a silent gap.

---

# 7. First-version functionality

The first milestone must support:

1. Choose a campaign/content folder.
2. Browse folders, including **entering subfolders, returning to the parent folder, and returning to the campaign root directly from any depth**. A campaign is a tree, not a flat folder.
3. Display image thumbnails.
4. Select an image.
5. Show that image on the external display.
6. Show the same resulting image in the GM preview.
7. Assign images to the six slots and recall them (§6.1). No previous/next navigation.
8. Blank/black player display.
9. Restore the selected content after ordinary UI recomposition/configuration where practical.

Nothing else is required for v0.1.

---

# 8. Filesystem access

Use Android's Storage Access Framework rather than assuming unrestricted filesystem access.

The user should explicitly select a campaign/content directory using `ACTION_OPEN_DOCUMENT_TREE`.

Persist the resulting URI permission so the folder remains available after restarting the application. Android officially supports persistable URI permissions for this purpose.

Do not request `MANAGE_EXTERNAL_STORAGE` merely because it is convenient.

The campaign is user-owned content, so Gamehost should work through Android's user-granted document access model.

Important architectural consequence:

**Do not make the internal representation of a file equal to a Java/Kotlin `File`.**

Use an abstraction such as:

```text
ContentLocation
    ├── DocumentUri
    ├── DisplayName
    ├── MimeType
    └── relative path / parent information
```

The presentation layer should not care whether an image came from:

* local storage
* SD card
* another document provider
* eventually a synced provider

This preserves the possibility of other storage backends later.

---

# 9. Architecture

Prefer **composition over inheritance**.

Avoid creating deep class hierarchies such as:

```text
GameAsset
  ├── ImageAsset
  │    ├── PngAsset
  │    └── JpegAsset
  ├── MarkdownAsset
  └── AudioAsset
```

Prefer small composable interfaces/components.

Conceptually:

```text
ContentSource
    ↓
ContentBrowser
    ↓
ContentItem
    ↓
PresentationState
    ↓
PresentationRenderer
    ├── GMPreviewRenderer
    └── PlayerDisplayRenderer
```

Possible abstractions:

```text
ContentSource
ContentRepository
ContentItem
MediaDescriptor
PresentationState
PresentationController
PresentationRenderer
DisplayTarget
```

These are conceptual boundaries, not necessarily classes with these exact names.

Keep interfaces small.

Use composition to assemble behaviour.

---

# 10. Presentation architecture

The most important architectural rule:

> **The presentation state must be independent of the UI and independent of the physical display.**

For example:

```text
PresentationState
    content = Jade-Fox.png
    mode = Visible
    scaling = Fit
```

Then:

```text
PresentationState
        │
        ├───────────────► GM preview
        │
        └───────────────► External display
```

The GM UI changes the state.

The renderers consume the state.

Do not make the external display itself the source of truth.

Do not make a Compose screen the source of truth.

Do not make the currently selected thumbnail the source of truth.

---

# 11. External display implementation

Use Android's multi-display APIs / `Presentation` mechanism where appropriate, with Samsung DeX-specific handling where required.

Samsung documents using `DisplayManager`, identifying the desktop display, creating a display-specific context, and attaching a separate window to that display.

The external renderer should be conceptually equivalent to:

```text
ExternalDisplayRenderer
    └── fullscreen visual surface
```

It should not know about:

* folders
* thumbnails
* GM notes
* campaign navigation
* business logic

It receives presentation state and renders it.

The external display should be `NOT_FOCUSABLE` / output-oriented where appropriate, so it does not steal interaction from the tablet. Samsung's documented Dual Mode example uses a non-focusable secondary window.

Handle:

* external display appearing
* external display disappearing
* display rotation/configuration changes
* application pause/resume
* DeX mode changes
* external display resolution changes

gracefully.

---

# 12. Do not mirror the whole application

Do not build the application around Android screen mirroring.

Do not use Chromecast/casting as the fundamental architecture.

Do not assume that the external display is simply a copy of the tablet.

The player display is an independent rendering target.

This is a core requirement.

---

# 13. Rendering and aspect ratio

The player surface is assumed to be approximately 16:9.

Images must preserve their aspect ratio.

Default behaviour:

* fit entire image
* letterbox/pillarbox with black
* never distort the image

Later modes may include:

* Fill/crop
* native resolution
* zoom
* pan

But those are presentation behaviours, not properties of the underlying image.

The GM preview must use exactly the same presentation transformation as the player renderer.

---

# 14. Future audio architecture

Future versions may add:

* music
* Spotify integration
* local audio
* sound effects
* scene-based audio
* independent music/SFX channels

Do not build audio into the image presentation code.

Instead, anticipate a compositional model such as:

```text
Scene
├── VisualPresentation
├── MusicPresentation
└── SoundEffectPresentation
```

The initial implementation may contain only:

```text
Scene
└── VisualPresentation
```

A future scene should be able to compose multiple independent presentation components without requiring a rewrite of the image system.

For local playback, AndroidX Media3/ExoPlayer is the intended Android technology to investigate when audio is actually implemented. Android recommends Media3/ExoPlayer for modern playback and provides MediaSession APIs for separating playback control from UI.

Do not implement Spotify access in v0.1.

---

# 15. Future Markdown architecture

Markdown is content, not application state.

Eventually Gamehost may render Markdown files and provide actions such as:

* show linked image
* show handout
* play linked audio
* navigate to another Markdown file

Example:

```markdown
# Jade Fox

![Portrait](../Portraits/Jade-Fox.png)

The magistrate knows Jade Fox is lying.
```

Gamehost may interpret the Markdown and provide presentation actions.

However, the Markdown file must remain valid Markdown independently of Gamehost.

Do not require special proprietary syntax for basic content.

If Gamehost-specific extensions are eventually required, keep them optional and clearly namespaced.

---

# 16. Architectural traps to avoid

## Trap 1: Building a VTT

Do not add:

* tokens
* initiative
* combat state
* character sheets
* rules engines
* map grids
* fog of war
* dice systems

unless an actual later use case demands them.

The product's simplicity is intentional.

## Trap 2: Building a campaign database

Do not turn folders and files into database rows.

The filesystem is the content model.

## Trap 3: Copying assets into an internal library

Do not import images into an app-managed media directory.

Display the user's existing files.

This is important for Git and Obsidian interoperability.

## Trap 4: Coupling presentation state to navigation state

"Selected file" and "currently displayed file" may initially be identical, but they are conceptually different.

A GM may eventually browse ahead without showing something to players.

Therefore model them separately from the beginning.

```text
BrowsingState
PresentationState
```

## Trap 5: Making the external display the source of truth

It is a renderer.

The application owns presentation state.

## Trap 6: Hard-coding 16:9 into image logic

16:9 is the initial target display shape, not an intrinsic property of content.

Keep presentation dimensions configurable.

## Trap 7: Making every feature a subclass

Prefer composition.

For example:

```text
Presentation
    + Visual
    + Audio
    + Overlay
```

rather than:

```text
Presentation
    ├── ImagePresentation
    ├── ImageWithMusicPresentation
    ├── ImageWithSfxPresentation
    ├── ImageWithMusicAndSfxPresentation
    └── ...
```

Avoid the combinatorial explosion.

## Trap 8: Building synchronization before it is needed

No server.

No WebSocket.

No cloud database.

No multiplayer synchronization.

The tablet and HDMI display are local.

## Trap 9: Premature Git integration

Gamehost should be Git-compatible because it **doesn't interfere with the filesystem**.

Do not implement a Git client initially.

Git can remain completely external.

## Trap 10: Designing for every Android device immediately

First make the Samsung Tab S7+ + Samsung DeX + HDMI path work.

Abstract platform-specific display behaviour behind a small interface so that other devices can be supported later.

Do not sacrifice the working target for hypothetical compatibility.

---

# 17. Suggested high-level module structure

A possible composition-oriented structure:

```text
app
│
├── content
│   ├── ContentSource
│   ├── DocumentTreeSource
│   ├── ContentRepository
│   └── ContentItem
│
├── presentation
│   ├── PresentationState
│   ├── PresentationController
│   ├── VisualPresentation
│   └── PresentationRenderer
│
├── display
│   ├── DisplayManager
│   ├── PlayerDisplay
│   └── ExternalDisplayRenderer
│
├── preview
│   └── PlayerPreviewRenderer
│
└── ui
    ├── FileBrowser
    ├── ThumbnailGrid
    ├── PlayerPreview
    └── Controls
```

This is a suggested separation, not a demand for one class per noun.

Prefer simple components and composition over abstraction for its own sake.

---

# 18. Technology choices

Suggested Android stack:

* Kotlin
* Jetpack Compose for GM UI
* AndroidX lifecycle/ViewModel where useful
* Storage Access Framework for campaign directories
* Android `DisplayManager` / `Presentation` / multi-display APIs for the player display
* Samsung DeX-specific display handling where necessary
* Coil or another lightweight image-loading library for thumbnails and image rendering
* Media3/ExoPlayer only when audio/video actually enters scope

The application should remain mostly local and offline.

The first prototype should minimize dependencies.

---

# 19. Development strategy

Do not build the complete application first.

Build in vertical slices.

### Milestone 1: Display proof

Hard-code one test image.

Success criterion:

```text
Tablet
    │
    ├── GM screen
    │
    └── HDMI
          │
          ▼
      fullscreen image
```

Verify this on the actual Galaxy Tab S7+.

### Milestone 2: Live preview

Render the same presentation state in a 16:9 preview on the tablet.

### Milestone 3: Folder browser

Open a user-selected folder and display image thumbnails.

### Milestone 4: Presentation control

Tap an image → external display changes.

### Milestone 5: Session safety

Add:

* Black screen
* external display disconnect/reconnect
* orientation/configuration handling
* graceful application lifecycle behaviour

### Milestone 6: Dogfood

Use it in an actual RPG session.

Only after this should new features be added.

---

# 20. Definition of success for v0.1

A GM sitting at a table can:

1. Open their campaign folder.
2. Browse existing images, descending into subfolders and returning to the root in one tap.
3. Assign a portrait/map/scene to one of the six slots.
4. Tap that slot.
5. See exactly what the players are seeing in the preview.
6. Have that image appear fullscreen on the external display.
7. Change the displayed image without interacting with the player display.
8. Blank the player display when necessary.

No campaign import.

No database.

No account.

No network connection.

No proprietary content format.

No VTT.

Just:

> **Select → preview → show.**

That is Gamehost v0.1.

---

# 21. Localisation

The application must be localisable. Locale is **configurable, not hard-coded**.

* The default locale is **French**.
* All user-facing text is French.
* The application name — **"Gamehost"** — is never translated.

## 21.1 Architectural consequences

**No user-facing string literal may appear in Kotlin source.** Every string goes through a string resource. This is the load-bearing rule; everything below is resource layout.

French is the **base** resource set (`res/values/strings.xml`), not an override. A device set to any language Gamehost does not ship falls back to French. Adding a language later means adding `res/values-xx/` and changing no code.

`app_name` is marked `translatable="false"`.

Sorting of folder and file names in the content browser must use **locale-aware collation**, not raw string ordering — otherwise `Élise` sorts after `Zorro`.

Any date or number formatting uses locale-aware formatters.

## 21.2 The player surface renders no *application* text

The **blank** state of the player display is pure black — never a localised "no content" message. The player display carries no GM-facing information (§4).

**Campaign info (§22.1) is the one text the player surface renders.** It is campaign content, not application text: Gamehost never translates it (§21.3), and the only strings Gamehost generates for it are locale-formatted dates.

A date produced by `java.time` and a `Locale` is **not** a string literal and needs no string resource — §21.1's rule is about text Gamehost authors, not text it formats.

## 21.3 Campaign content is not localised

File names, folder names and future Markdown are the user's own content.

Gamehost never translates them and never assumes what language they are in.

## 21.4 Localisation applies to the interface, not the codebase

Identifiers, comments, KDoc, README and commit messages are English, consistent with the Android and Kotlin APIs they sit alongside.

Only string resources and on-screen text are French.

---

# 22. v0.2 — campaign info and transitions

Two additions, both driven by actual need at the table.

## 22.1 Campaign info

A single **INFO** control in the bar. When enabled, the player display shows a full-screen campaign panel **instead of** the current image.

It is a *mode*, composed into the scene alongside the visual — not an overlay layered on top. That is what makes "INFO, then back" return to the same image for free, exactly as blanking already does. **NOIR overrides INFO**, because the panic button overrides everything.

The panel may specify its own background image; otherwise it is black.

Content comes from the campaign's own files (§22.3). Entries are free-form label/value pairs in the order the GM wrote them, plus a small set of known fields that earn special treatment by either **computing** something the GM would otherwise maintain by hand, or **formatting** something a generic row would render badly:

* `title` — panel heading
* `location` — promoted headline
* `date` — written out in full, in French, with its weekday
* `show_lunar_state` — draws the Moon in its phase for that date

This is not a general-purpose overlay editor and must not become one. A new known field is justified only by the two tests above.

### Dates before 1582 are Julian

`date` follows the convention of Wikipedia and historical lunar tables: **Julian before 15 October 1582, Gregorian from then on.** The GM writes the date exactly as their sources give it.

In the twelfth century the calendars differ by **seven days**, so a full moon listed as 9 January 1137 falls on 16 January in the proleptic Gregorian calendar `java.time` uses. Gamehost converts internally before computing anything astronomical, and displays the date as written — so the panel agrees with the GM's notes and the Moon agrees with the sky.

The phase is a mean-synodic model, accurate to about a day. That is far beyond what anyone at a table can check, and the honest alternative is a great deal of arithmetic for no visible gain.

## 22.2 Transitions

Changing the player surface cross-dissolves rather than cutting. The first implementation is a **watercolor dissolve**, chosen because it belongs to the visual identity of a Song-dynasty wuxia campaign.

The transition applies to **every** change of the player surface — image to image, image to info, image to black — because it is a property of the surface, not of images. Blanking is clamped to a fraction of the configured duration: nothing is readable in a few frames, and NOIR must stay a panic button.

The app contains a small fixed set of transitions — `cut`, `fade`, `watercolor` — and a campaign picks one **by name**. There is no transition authoring system, and an unrecognised name falls back to `cut` so that a typo is visible rather than silently pretty.

A new transition is added to the app when a real campaign needs one. Not before.

## 22.3 The campaign file

One optional file at the campaign root, `Campagne.md`, with everything Gamehost-specific under a namespaced `gamehost:` key in YAML frontmatter (§15).

It remains a perfectly ordinary Markdown note: Obsidian renders it with properties, the body belongs to the GM, and Gamehost reads it without ever writing to it. A missing or malformed file means the defaults apply and is never an error.

It is re-read whenever INFO is pressed, so an edit made in Obsidian mid-session appears without Gamehost watching the filesystem.
