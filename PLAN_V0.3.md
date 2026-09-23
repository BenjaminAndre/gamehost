# Brigade v0.3 — Markdown notes

Browse and present Markdown notes, not just images. A note resolves to the first image it
links, and the GM gets a small bar above the preview showing that note's key facts.

Working document for one release. Delete it when v0.3 ships.

---

## 1. Decisions taken

| | Decision |
|---|---|
| Link syntax | Obsidian `![[…]]` **and** standard `![](…)`. Wikilinks resolve by filename across the campaign tree, as Obsidian does; Markdown links resolve relative to the note. |
| GM bar | A fixed set, not free-form frontmatter: **date · name · element · faction**, bullet-separated, horizontally scrollable when it overflows. |
| Date | The **campaign** date from `Campagne.md`, always. Rendered **ISO**, to save room. |
| Name | The **filename**, without extension. Not a frontmatter property. |
| Element | Five phases, French values → emoji: Terre 🪨 · Feu 🔥 · Eau 💧 · Métal ⚔️ · Bois 🌳 |
| Faction | Shown in red — its own theme token, not the error red that marks a missing slot. |
| Keys | **English keys, French values** — `date:`, `element:`, `faction:`. |
| Bar visibility | Only when a note is live. Works for *Afficher* as well as slot recall. |
| No linked image | Players see black. The bar still fills in. |
| Several images | First only. |

---

## 2. What the architecture already absorbs

Most of this feature costs nothing, which is worth stating before the parts that do.

**The two-renderer path needs no change at all.** A note resolves to an image *before* a
`Frame` is built, so `Frame`, `PresentationSurface`, `PlayerPresentation` and
`PlayerPreviewPane` are untouched. The players see a picture; nothing downstream knows a
note was involved.

**The GM bar cannot leak onto the player display.** `Frame` carries no document field, and
`frame()` will ignore the note entirely. That is a structural guarantee, not a discipline —
the same reason §21.2 survives INFO.

**Slots already store paths, not ids.** `.brigade/slots.json` holds campaign-relative paths,
so a slot holding `Personnages/Jade-Fox.md` needs no format change and no migration.

**`ContentClassifier` already recognises Markdown.** The browser simply filters it out today
(`BrowsingState.images`). Showing notes is a filter change, not a classification change.

---

## 3. The model

### Content layer — raw, pure

```kotlin
// content/NoteDocument.kt — pure, no android.*, no YAML
data class NoteDocument(
    /** Filename without extension. This is the character name. */
    val name: String,
    /** Raw French value, e.g. "Métal". Unparsed on purpose. */
    val element: String? = null,
    val faction: String? = null,
    /** Image link targets in the order they appear in the body. */
    val imageLinks: List<String> = emptyList(),
)
```

Same split as `CampaignConfig`: the reader returns what the file *said*, and meaning is
applied one layer up. It is what keeps `org.yaml` and `DocumentsContract` out of everything
above `content/saf/`.

### Presentation layer — resolved

```kotlin
// presentation/NoteBar.kt
enum class WuXing(val emoji: String) {
    Terre("🪨"), Feu("🔥"), Eau("💧"), Metal("⚔️"), Bois("🌳");

    companion object {
        /** Accent- and case-insensitive: «Métal», «metal» and «MÉTAL» all match. */
        fun parse(value: String?): WuXing?
    }
}

data class NoteBar(
    val name: String,
    val dateIso: String? = null,
    val element: WuXing? = null,
    /** Kept raw when [element] is null, so a typo is visible rather than silently dropped. */
    val elementRaw: String? = null,
    val faction: String? = null,
)
```

`Scene` gains `note: NoteBar?`. `frame()` does not consult it.

### Slots

`SlotContent.Filled` currently holds `(path, id, displayName)`. A note slot needs the
*image* to present as well as the note itself:

```kotlin
data class Filled(
    val path: ContentPath,
    val displayName: String,
    /** What the players see. Null for a note that links no image. */
    val imageId: ContentId?,
    /** Present only when the slot holds a note. */
    val note: NoteBar? = null,
) : SlotContent
```

The old `id` becomes `imageId`; `path` already identifies the content. Resolving a note slot
now also reads the note and resolves its first link — six file reads at campaign open, which
is bounded and happens off the main thread.

---

## 4. Link resolution — the part that is actually hard

Obsidian's `![[Jade-Fox.png]]` does not mean a path. It means *find the file with that name
anywhere in the vault*. That is different machinery from `![](../Portraits/Jade-Fox.png)`,
which is relative to the note.

**Pure extraction** (`content/MarkdownLinks.kt`), testable on the JVM:

- Split frontmatter from body first, so a link inside frontmatter is never picked up.
- `![[target]]` and `![[target|300]]` — everything after `|` is a display hint, discarded.
- `![alt](target)` and `![alt](target "title")`.
- Returns targets in document order, untouched.

**Resolution** (`ContentRepository`):

| Target shape | Rule |
|---|---|
| Contains `/` | Treated as a path. Relative to the note's folder, `..` honoured; falls back to campaign-root-relative. |
| Bare filename | Looked up in a **filename index** over the whole tree. |

The index is a `Map<String, List<ContentItem>>` keyed by lowercased filename, built by
walking every folder once, cached alongside the existing per-folder cache and cleared by the
same `invalidate()`. Building it costs one SAF query per folder — bounded, lazy, and off the
main thread.

**Ambiguity rule**, needed because Obsidian allows duplicate filenames: prefer a match in
the note's own folder, then the shallowest path, then first in natural order. Deterministic,
documented, and not a silent coin-flip.

**Extensionless targets**: `![[Jade-Fox]]` retries with each image extension in turn. This is
common in Obsidian and cheap to support once the index exists.

---

## 5. The GM bar

```
1137-01-09 · Jade-Fox · ⚔️ · Secte du Lotus
```

Lives in `ui/NoteBarStrip.kt`, sits directly above `PlayerPreviewPane` in `GmScreen`, and is
composed only when `presentation.scene.note != null`.

- A horizontally scrollable `Row`, so a long faction name never truncates the name before it
  — the GM slides instead.
- Separator `·` in the dim on-surface colour.
- Date dim, name bold, element as emoji, faction in `colorScheme.tertiary`.
- A segment whose value is absent is omitted, along with its separator.
- An unrecognised element shows its raw text rather than an emoji.

The bar is **GM-facing only**. It is not localised French text authored by Brigade — the
values are the GM's own campaign content (§21.3) — and the one thing Brigade generates, the
ISO date, is a formatted date, not a string literal (§21.1).

### The date

Always the campaign date from `Campagne.md` — never a per-note one. Notes are not required
to carry a `date:` at all, and `NoteDocument` does not read one.

Strictly this means the date segment is campaign context rather than a fact about the note,
which is a small semantic fudge. It is the right trade: one meaning everywhere beats a field
that silently means "when this happened" on one note and "today" on the next, with nothing on
screen distinguishing them. Read the bar as *context, then note*.

The value is already available raw in `CampaignInfoConfig.entries`, so neither `InfoPanel`
nor `CampaignInfo` changes — `AppGraph` reads it directly. The Julian rule (§22.1) governs
*interpretation*; the bar prints the ISO string **as written**, so no conversion happens here.

### The faction colour

A dedicated token rather than `colorScheme.error`, which already marks a **missing slot** —
two different reds side by side in the same control area would be confusable in dim light,
and one of them means "something is broken".

Material 3's `tertiary` slot is unused in the theme today and is exactly the "third accent"
this needs, so it becomes the faction red in `Theme.kt`. No parallel colour system, and it
stays themeable.

---

## 6. Browsing

`BrowsingState` gains `documents` beside `folders` and `images`; `isEmpty` accounts for all
three. `ContentGrid` renders a note cell: filename on a tinted card, no thumbnail.

**Deliberately no thumbnail in the grid.** Showing each note's first image would mean reading
every note in the folder to draw one screen — 50 notes, 50 file reads, on every scroll into a
new folder. Slot mini-previews *do* show the image, because there are exactly six of them and
they are already resolved. Revisit only if browsing by name proves annoying at a table.

Tapping a note opens the existing action menu — *Afficher* or *Emplacement n* — unchanged.

---

## 7. Files

**New:** `content/NoteDocument.kt`, `content/MarkdownLinks.kt`, `content/saf/NoteReader.kt`,
`presentation/NoteBar.kt`, `ui/NoteBarStrip.kt`.

**Changed:** `content/ContentRepository.kt` (filename index, link resolution, `markdown()`),
`content/saf/CampaignFile.kt` (extract the frontmatter splitter for reuse),
`presentation/SlotBank.kt` (`Filled` shape), `presentation/PresentationState.kt`
(`Scene.note`), `presentation/PresentationStore.kt` (show-with-note), `AppGraph.kt`
(note reading, resolution, slot resolution, *Afficher*), `ui/GmViewModel.kt` (`documents`),
`ui/ContentGrid.kt` (note cell), `ui/GmScreen.kt` (the strip), `ui/SlotBar.kt` (note badge),
`DESIGN_REQUIREMENTS.md` (§23), `ARCHITECTURE.md`, `README.md`.

---

## 8. Tests

CI is the only check, so anything not eyeballable needs one.

**Pure, on the JVM:** frontmatter/body split · `![[x]]`, `![[x|300]]`, `![](x)`,
`![](x "title")`, links in frontmatter ignored, order preserved, no links → empty ·
`WuXing.parse` for «Métal», «metal», «MÉTAL», «Bois», nonsense → null with raw kept ·
path-vs-filename target classification · the ambiguity rule with duplicate filenames ·
extensionless retry · the bar showing the campaign date even for a note that carries its own
`date:` · `frame()` still ignoring `Scene.note` — the guarantee that the bar cannot reach the
players.

**Device:** bar legibility and scrolling at the table; a slot holding a note recalling its
image; a note with no image showing black while the bar fills.

---

## 9. Build order

1. **Pure parsing** — `MarkdownLinks`, `NoteDocument`, `WuXing`. All testable, nothing wired.
2. **Reading** — `NoteReader` over SAF, reusing the frontmatter splitter.
3. **Resolution** — filename index and link resolution in `ContentRepository`.
4. **Browsing** — notes appear in the grid and can be tapped.
5. **Presenting** — `Scene.note`, slots holding notes, *Afficher*, slot thumbnails.
6. **The bar** — `NoteBarStrip` and its placement.
7. **Docs** — §23, architecture notes, README.

Steps 1–3 are invisible and fully covered by tests; the feature only becomes usable at 4.

---

## 10. Open questions

1. **A note with several images** — first only, confirmed. Worth revisiting after a session:
   if the answer turns out to be "I wanted the second one", arrows in the bar are the cheap fix.
2. **Note cells in the grid show no image.** If browsing notes by filename is annoying in
   practice, the fix is lazy per-cell reads, which is real work — worth knowing before building it.
3. **Should a note's frontmatter feed the player-facing INFO panel?** Currently INFO is
   campaign-level only. Keeping them separate seems right, but a "show this NPC's details to
   the players" case might argue otherwise.
4. **Does the date segment earn its place?** It is campaign context repeated on every note,
   and the INFO panel already shows the players the same date. If it reads as noise at the
   table, dropping it leaves a cleaner `Jade-Fox · ⚔️ · Secte du Lotus`.
