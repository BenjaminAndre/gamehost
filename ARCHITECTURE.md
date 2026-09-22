# Architecture

Companion to `DESIGN_REQUIREMENTS.md`, which is the authority. This file records how the
requirements were translated into code, and the rules that keep them true.

## One Gradle module, package boundaries by rule

Gamehost is a single `:app` module whose packages are laid out as the module graph it
*would* have. What makes code refactorable is the dependency graph, not the module
graph; at this size a four-module split costs four build files, four manifests and an
`api`/`implementation` decision on every new type, paid daily, for a benefit paid never.
Extraction later is `git mv` plus four build files — provided the direction below is
never violated.

```
content/       → kotlin + coroutines only.  NO android.*, NO Compose, NO presentation/
                 (exception: content/saf/** — the single Android-touching leaf)
presentation/  → content/ (ContentId, ContentPath) + coroutines.  NO android.*, NO Compose
render/        → presentation/, content/, Compose, Coil.  NO ui/, NO display/
display/       → render/, presentation/, android.*.       NO ui/
storage/       → adapters: android.* + the interfaces in content/ and presentation/
ui/            → everything
```

`ArchitectureTest` enforces this by reading the source. Forty lines of test in place of
four Gradle modules.

The rule that carries the most weight is the first. Keeping `content/` and
`presentation/` free of `android.*` is what makes the interesting logic unit-testable
here at all, with no Robolectric and no emulator. Three decisions exist mainly to
protect it:

- `ContentId` wraps a `String`, not an `android.net.Uri`;
- display selection is a pure function over a `DisplayInfo` data class, not over
  `android.view.Display`;
- `PresentationStore` holds no repository reference.

## One presentation state, two render targets

`PresentationState` is the single source of truth for what the players can see. It is
owned by `AppGraph` on the `Application` — **not** by a ViewModel.

That is not a style preference. The player window is an `android.app.Presentation`, a
`Dialog` on a secondary display with a lifecycle of its own that outlives Activity
recreation. State scoped to the Activity's `ViewModelStore` would be cleared underneath
it: the players' screen freezes on a stale frame while the GM's UI moves on. Worse, a
`viewModel()` call inside the player composition would silently construct a *second,
independent* state holder — literally the "two independent pieces of business logic"
§5 forbids, one innocent line away. `PlayerPresentation` therefore deliberately does
**not** set a `ViewTreeViewModelStoreOwner`, so that line fails loudly instead.

Both compositions collect the same `StateFlow` instance, and both draw the same
composable:

```
PresentationState (AppGraph, process-scoped)
        │
        ├──────────────► PlayerPreviewPane  ─┐
        │                                    ├─► PresentationSurface
        └──────────────► PlayerPresentation ─┘
```

`PresentationSurface` carries its invariants in its KDoc; they are what make the
identical-output claim a property rather than a convention.

| State | Owner | Lifetime |
|---|---|---|
| `PresentationStore`, `SlotBank`, `ContentRepository`, `PlayerDisplayHost` | `AppGraph` | process |
| `BrowsingState` | `GmViewModel` + `SavedStateHandle` | Activity — dying with the UI is correct |
| grid scroll, open dialog | `remember` / `rememberSaveable` | composition |

## Browsing and presentation cannot touch each other

§16 Trap 4 is structural here rather than aspirational. Tapping a thumbnail opens the
action menu; it has no path to presentation state at all. Only the slot bar and
*Afficher* present anything. The GM can browse the whole campaign mid-scene with no risk
of revealing something.

## Where 16:9 lives

Once: `PlayerDisplayStatus.DEFAULT_PLAYER_ASPECT`, used only when there is no display to
ask. The preview otherwise takes its ratio from the real attached display, so a 16:10
projector previews as 16:10 (§16 Trap 6).

## Where the state lives on disk

| What | Where | Why |
|---|---|---|
| Campaign root URI | app-private `SharedPreferences` | belongs to the install, not the campaign |
| Last presentation | app-private `SharedPreferences` | session scratch; restored blanked |
| **Slot bank** | `<campaign>/.gamehost/slots.json` | points at campaign content, so it travels with it (§2.3) |

Slots store **paths relative to the campaign root, never document URIs**. A document URI
is provider-specific and survives neither a folder move, a reinstall, nor the campaign
being opened on another device — which would defeat the reason for putting the file in
the campaign folder at all. The cost is that a path must be resolved against the tree at
load; the benefit is that a slot bank is as portable as the campaign, and readable in a
Git diff.

Nothing else is ever written to the campaign folder.

## The escape hatch

`PlayerDisplayHost` is all of §16 Trap 10. Whether `Presentation` actually reaches HDMI
in a given Samsung DeX configuration is the largest unknown in the project. If it does
not, the fallback is a `PlayerActivity` launched with `ActivityOptions.setLaunchDisplayId`
rendering the same `PresentationSurface` against the same process-scoped state — a
different implementation of that one interface, and **nothing else changes**.

Related: `pickPlayerDisplay` excludes the display the GM UI is on rather than picking
"the non-default display". In standard DeX the external display *is* the desktop and the
GM Activity launches onto it, so "external" and "player" are not the same thing.

## Invariants worth not breaking

- No `<uses-permission>` in the manifest. SAF grants are not permissions and there is no
  network. The empty list is a feature.
- No user-facing string literal in Kotlin. Everything through `stringResource` (§21).
- The player surface renders no text, ever. Its blank state is pure black.
- `content/saf/DocumentTreeSource` is the only file that knows `DocumentsContract`
  exists, and every `ContentId` is a *document* URI built using the tree — never the raw
  tree URI, which has no document-id segment.
- The GM preview renders presentation state in every display state; a missing player
  display is an overlay on top of it, never a replacement (§5.1).
