# Gamehost v0.2 — implementation plan

Two features: a full-screen **campaign info mode**, and **configurable transitions** whose first
implementation is a watercolor dissolve.

This plan is a working document for one release. Delete it when v0.2 ships; `DESIGN_REQUIREMENTS.md`
and `ARCHITECTURE.md` remain the permanent records.

---

## 1. Decisions already taken

| | Decision |
|---|---|
| Config file | `<campaign root>/Campagne.md`, YAML frontmatter under a `gamehost:` key |
| YAML parser | snakeyaml |
| INFO | **A full-screen mode that replaces the image**, not an overlay. May have its own background image; black otherwise. NOIR overrides it. |
| Info entries | Free-form ordered label/value pairs, plus a few **known fields** with special rendering |
| Known fields (v0.2) | `date` (ISO, rendered French with weekday), `show_lunar_state` (computed moon phase, drawn top-right) |
| Control bar | `[NOIR][INFO][1][2][3][4][5][6]` |
| Transition scope | **Every** change of the player surface — image→image, image→INFO, image→NOIR |
| Watercolor | Portable noise-mask dissolve. **No AGSL/RuntimeShader** (needs API 33, may not exist on the tablet) |

### Amendment to the v0.2 brief

The brief says the info panel is *"an overlay, not a separate screen"*. That was reversed in
conversation: it is a **mode**. This turns out to be the better architecture — it composes into
`Scene` as a sibling field rather than layering on top, which buys "INFO then back returns to the
same image" for free, exactly as `blackout` already does.

---

## 2. What the design pass produced

Three parallel designs were commissioned; **two returned** (transitions, watercolor) and one failed
(campaign-info), then all were critiqued together. The critique's verdict: the two returned designs
are individually strong but are **two competing implementations of the same feature**, not
complementary halves — both rewrite `Transition.kt`, `PresentationState.kt`, `PresentationStore.kt`
and `PresentationSurface.kt` with incompatible declarations.

**The merge, decided here:**

- **Skeleton from the transitions design:** `Frame`, `frame()`, `startNanos` carried in state,
  per-layer opaque black, interrupt-from-the-dominant-layer, eager prefetch.
- **Pixels from the watercolor design:** `generateWashField` with domain warp and binned-CDF
  equalisation, dihedral flips via `DrawScope.scale`, the lambda-valued ramp that keeps progress out
  of composition, and the all-defaults store constructor.
- **Dropped as scope creep:** the wet-edge `rim`, the bank of four wash fields, `WashSpec`'s ten
  tunables, the completion timer and its grace constant, the required `wash` parameter, `byName`
  spelling aliases, and campaign-configurable `band`/`rim`.

**The fix neither design found**, and the most important item in this plan — see §6.

---

## 3. The model

```kotlin
// presentation/Frame.kt — NEW
/**
 * One still picture, fully determined. The type transitions are *between*.
 *
 * A Frame is RESOLVED, not a recipe: anything needing a locale, a calendar, a repository
 * lookup or a font metric has already happened. The renderer may not do that work — it
 * runs twice, in two windows, and any of it could differ between them.
 */
sealed interface Frame {
    data object Black : Frame
    data class Picture(val id: ContentId, val scaling: ScalingMode) : Frame
    data class Info(val panel: InfoPanel, val background: ContentId?) : Frame
}

/** The ONLY function renderers call to decide what to draw. Replaces effectiveVisual(). */
fun PresentationState.frame(): Frame = when {
    blackout -> Frame.Black                                    // FIRST. NOIR overrides INFO.
    scene.mode == SceneMode.Info ->
        scene.info?.let { Frame.Info(it, scene.infoBackground) } ?: Frame.Black
    else -> when (val s = scene.visual.source) {
        VisualSource.None -> Frame.Black
        is VisualSource.Image -> Frame.Picture(s.id, scene.visual.scaling)
    }
}
```

`effectiveVisual()` is deleted — it cannot express "black" distinctly from "no image", which is
precisely why the watercolor design's `effectiveScene()` let INFO survive NOIR.

```kotlin
// presentation/PresentationState.kt — CHANGED
enum class SceneMode { Visual, Info }

data class Scene(
    val mode: SceneMode = SceneMode.Visual,
    val visual: VisualPresentation = VisualPresentation(),
    val info: InfoPanel? = null,
    val infoBackground: ContentId? = null,
)

data class PresentationState(
    val scene: Scene = Scene(),
    val blackout: Boolean = false,
    val liveSlot: SlotId? = null,
    val revision: Long = 0L,
    /** Null when settled — and when null, the renderer composes one layer and ticks no clock,
     *  so a campaign with no transition gets v0.1's render path unchanged. */
    val transition: ActiveTransition? = null,
)
```

---

## 4. Transitions

**Synchronising two windows.** The preview and the player window are separate compositions in
separate windows. `ActiveTransition` carries `startNanos`, stamped once in the store when the state
changes. Each window then computes `progress = f(startNanos, now)` as a pure function while sampling
its own frame clock — synchronised by construction, with no shared animation object and no state
ticking at 60 Hz. A window that attaches mid-transition (HDMI hotplug, routine on DeX) picks up the
correct progress immediately, which a per-window "remember the previous state" approach could not.

**Progress must not be read in composition.** Pass it as a lambda (`progress: () -> Float`) read only
inside `graphicsLayer { }` and `drawWithContent { }`, so the frame clock invalidates the *draw* phase
only. Reading it in composition recomposes both windows' render subtrees ~60×/second and re-invokes
`AsyncImage` with a fresh modifier each frame.

**Holding the outgoing content.** Keep both layers composed. Do **not** use
`GraphicsLayer.toImageBitmap()` — that is a multi-megabyte readback per transition on the player
window.

**Every layer fills its own bounds with opaque black first.** Without this, a transition between
images of differing aspect ends in a visible pop: `DstIn` masks a transparent letterbox region to
still-transparent, so the outgoing image shows through the incoming one's bars until the transition
ends and they snap to black.

**Interruption** (the GM taps slot 3 then slot 5): take the more-opaque layer as the new outgoing
frame. Worst case is a one-frame step at the midpoint. Always taking the in-flight target instead
pops to an image the GM already moved past, on *every* interruption.

**NOIR** keeps the uniform rule but **clamps its duration to 120 ms**. Nothing is readable in seven
frames, and the state changes instantly regardless, so the panic button's responsiveness was never
actually at stake.

---

## 5. The watercolor dissolve

**Technique.** An animated threshold over a soft fractal noise field, used as an alpha mask so image
B bleeds in through the noise. A hard threshold gives crunchy edges, so the ramp is a `smoothstep`
over a band around the threshold, spanning `1 + 2·band` so it saturates at both ends.

**Masking in Compose, no shader.** The idiom is
`graphicsLayer { compositingStrategy = Offscreen }` placed **before**
`drawWithContent { drawContent(); drawImage(mask, blendMode = DstIn) }`. The critique confirmed this
works and that modifier order is the crux — reversed, it silently draws nothing or a black rectangle.

**The field.** One 256×256 field generated procedurally at startup by a process-wide `object` with
`by lazy`, warmed off-main in `GamehostApp.onCreate`. Domain-warped fractal noise, histogram-equalised
by binned CDF so the threshold sweeps at a uniform rate. Variation between consecutive dissolves comes
from **four dihedral orientations via `DrawScope.scale(±1, ±1)`** — not `srcOffset`/`srcSize`, which
can only crop and would change the apparent feature scale rather than mirror it.

**Scale invariance.** The field is stretched across the full surface and sampled in normalised
coordinates, so the ~350 dp preview and the 1920 px window show the same wash pattern at different
scales. A low-resolution field scaled up looks *better* — softer — as well as being cheaper.

**`cut` and `fade` fall out of the same mechanism**: `cut` is duration 0, `fade` is a uniform mask.

---

## 6. The fix neither design found — one decode, two windows

Coil's memory cache is **keyed by request size**, and `AsyncImage` sizes its request to the composable
it is in. The preview (~700×394) and the player window (1920×1080) therefore hold *different cache
entries* and reach `Success` at different moments. A dissolve reveals whatever is in the incoming
layer right now — so the two windows genuinely display different pixels for the first frames of every
cold transition.

This is latent in v0.1 already ([PresentationSurface.kt](app/src/main/java/com/gamehost/render/PresentationSurface.kt)
uses the default `model = { it.value }` at both call sites). A transition is what makes it visible.

**The injection point already exists and neither design used it.** `PresentationSurface` takes
`model: (ContentId) -> Any` from the *caller* — the same mechanism by which `PlayerPreviewPane`
already takes its aspect ratio from the real display. Build **one** `(ContentId) -> ImageRequest` in
`AppGraph` with an explicit, identical `.size(w, h)` from the attached player display, and pass that
same lambda to both `PlayerPreviewPane` and `PlayerPresentation`. One cache key, one decode, both
windows dissolving the same bitmap — and no size read inside the composable, so the KDoc invariant is
untouched. It also makes the prefetch actually warm the entry both windows use.

---

## 7. Campaign info

### File format

```markdown
---
gamehost:
  transition: watercolor
  info:
    background: Fonds/parchemin.jpg
    show_lunar_state: true
    entries:
      - date: 1127-03-12
      - Lieu: Auberge du Héron Noir
      - Saison: Printemps
---

# Campagne du Renard de Jade

Tes notes libres. Gamehost ne lit que l'en-tête.
```

### Reading

The reader lives in **`content/saf/CampaignFile.kt`** — it needs the `ContentResolver` anyway, and
this keeps `org.yaml` out of `content/` and `presentation/`. It returns a plain data class that
`presentation/` consumes.

- Frontmatter is the block between a leading `---` and the next `---`.
- snakeyaml **must** be constructed with `SafeConstructor` + `LoaderOptions`. The default constructor
  can instantiate arbitrary classes from YAML tags; the file is user-authored so the risk is low, but
  there is no reason to accept it.
- Re-read on every INFO toggle, so an edit made in Obsidian in the DeX split view appears with no file
  watching.
- Missing, malformed, or no `gamehost` key → INFO is **disabled** in the control bar rather than
  showing an empty panel. A parse failure surfaces as a GM-side message, never a crash.

### Known fields

A **registry of handlers keyed by field name**, with the generic label/value path as the fallback.
Adding a known field later is one registry entry and cannot disturb the generic path.

**`date`** — value is an ISO date. `java.time.LocalDate` is proleptic ISO-8601, so `1127-03-12`
parses and yields a real weekday with no custom calendar; decision 4's "proleptic Gregorian always"
is satisfied for free. Rendered with
`DayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)` → «jeudi 12 mars 1127». A date formatted via
`java.time` + `Locale` is **not** a string literal and needs no string resource. `minSdk 30` means
`java.time` needs no desugaring, and it is not `android.*`, so `ArchitectureTest` permits it in
`presentation/`.

**`show_lunar_state: true`** — compute the phase and draw the moon top-right of the panel.

```kotlin
private const val SYNODIC = 29.530588853          // days
private const val EPOCH_NEW_MOON = 10962.7597     // 2000-01-06T18:14Z as a fractional epoch day

/** @return phase in [0,1): 0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
fun moonPhase(date: LocalDate): Double =
    (date.toEpochDay() - EPOCH_NEW_MOON).mod(SYNODIC) / SYNODIC
```

> **The single most likely bug in this whole release.** `LocalDate.of(1127,3,12).toEpochDay()` is
> about **−307,900** — negative. Kotlin's `%` returns a negative remainder for a negative dividend,
> which produces a mirrored, garbage phase for *every* pre-1970 date. It must be `.mod()`, which is
> always non-negative for a positive divisor. This needs a regression test with a pre-1970 date
> asserting the result lies in `[0,1)`.

Accuracy: a mean-synodic model drifts up to roughly a day over nine centuries, since the Moon's orbit
is not uniform. For a glyph on a panel at a table that is irrelevant, and it should be said out loud
rather than implied to be astronomical.

**Drawing the phase.** The terminator is an ellipse, not a straight line, so a naive half-circle is
wrong at crescent and gibbous. With `c = cos(2πp)`:

1. Fill the full disc dark.
2. Fill the lit semicircle — right for waxing (`p < 0.5`), left for waning.
3. Overlay an ellipse centred on the disc, horizontal radius `R·|c|`, vertical radius `R`,
   coloured **dark when `c > 0`** (crescent) and **lit when `c < 0`** (gibbous).

### Rendering the panel — the hard part

The panel renders inside `PresentationSurface`, which forbids size-dependent branching. Sizing text
in `sp` would make the preview lie. But scaling the font is **not sufficient either**: line breaking
is quantised, so a value fitting on three lines at 1920 px can break to four at 700 px, and the
preview would show a differently-shaped panel — the §5 failure arriving through a third door.

**Lay the panel out once at a fixed virtual resolution (1920×1080) with a `TextMeasurer`, then draw
the measured result inside `withTransform { scale(size.width / 1920f) }`.** Line breaks are computed
once and merely scaled, so the two windows are identical by construction rather than by hope. This
rule goes into the `PresentationSurface` KDoc, not into the info code where it would be rediscovered.

---

## 8. Documentation amendments

`DESIGN_REQUIREMENTS.md` §21.2 and `ARCHITECTURE.md` currently both state *"The player surface renders
no text, ever."* INFO makes that false. Amend **in the same commit that lands INFO**:

> The **blank** state of the player display is pure black, never a localised message. Campaign info is
> the one text the player surface renders; it is campaign content, is not localised by Gamehost
> (§21.3), and its only Gamehost-generated strings are locale-formatted dates.

Also note in §21.1 that a date formatted via `java.time` + `Locale` is not a string literal and needs
no resource. And rewrite the "never read any size" paragraph of the `PresentationSurface` KDoc **once**
to permit `DrawScope.size` as a uniform scale while still forbidding branching on it.

---

## 9. Defects to fix while passing through

Each was found by the critique in the supplied designs; all would otherwise have been written into
the code.

- `setBlackout(black)` must honour its argument — one design's recommended snippet hardcodes `true`.
- `PresentationSnapshot.toState()` must force `mode = SceneMode.Visual`, alongside the existing
  `forceBlackout` policy. Keeping `mode` while dropping `info` restores to `Info + null`, which
  projects to black — an app that comes up black and stays black, indistinguishable from a bug.
  Add **no** field to the snapshot: the panel derives from `Campagne.md`, and persisting it would be
  the §16 Trap 2 shape.
- The live-control indicator becomes one pure function over state —
  `blackout → NOIR; mode == Info → INFO; else liveSlot` — and is unit-tested. Do **not** clear
  `liveSlot` on INFO; "INFO then back returns to the same slot" is the same free property as blackout.
- `ArchitectureTest`: add `org.yaml` to the forbidden imports for `content` and `presentation`
  **before** writing a line of parser. It would otherwise pass while violating the rule's spirit.
- Add snakeyaml keep rules to `proguard-rules.pro` at the same time, not later. The app does not
  minify today, but reflection-heavy libraries are exactly what breaks when it starts.
- Give every new `PresentationStore` constructor parameter a default, so the nine existing
  `PresentationStore()` call sites in `PresentationStoreTest` still compile.

---

## 10. Files

**New:** `presentation/Frame.kt`, `presentation/Transition.kt`, `presentation/InfoPanel.kt`,
`presentation/MoonPhase.kt`, `content/saf/CampaignFile.kt`, `render/DissolveMask.kt`,
`render/InfoPanelSurface.kt`.

**Changed:** `presentation/PresentationState.kt` (Scene, frame(), delete effectiveVisual),
`presentation/PresentationStore.kt` (stamp transitions, INFO mode), `presentation/PresentationSnapshot.kt`
(force Visual), `render/PresentationSurface.kt` (two layers, mask, KDoc), `AppGraph.kt` (shared image
model, campaign file, prefetch), `ui/SlotBar.kt` (INFO button, active-control function),
`ui/GmScreen.kt` + `ui/GmActivity.kt` (wiring), `ArchitectureTest.kt`, `proguard-rules.pro`,
`libs.versions.toml` + `app/build.gradle.kts` (snakeyaml), `strings.xml`, `DESIGN_REQUIREMENTS.md`,
`ARCHITECTURE.md`.

---

## 11. Verification

**There is no local toolchain** — no usable JDK, no Gradle, no Android SDK. CI and the device are the
only verification. Anything whose correctness cannot be eyeballed needs a JUnit test, because CI
running that test is the only way it is ever checked.

**Unit (CI):** `frame()` projection, *especially* `blackout` overriding `mode == Info` · moon phase
for a pre-1970 date returning `[0,1)` (the `.mod()` regression) · moon phase at a known modern new
moon and the full moon ~14.77 days later · French date formatting · frontmatter extraction including
absent/malformed/no-`gamehost` files · the known-field registry falling back to label/value · the
active-control function · snapshot forcing `Visual` · the transition ramp saturating at both ends ·
the wash field's value distribution.

**Device:** the dissolve actually looking like watercolor rather than mush; preview and player window
staying in step through a dissolve (the §6 fix); INFO legibility across the table; NOIR still feeling
instant at a 120 ms clamp; frame rate on the real HDMI output.

**Measure before assuming.** The two designs disagreed about the player display's resolution
(3840×2160 vs 1920×1080) and each used its own number to win a different argument. The fill-rate
conclusion flips between them. `PlayerDisplayStatus.Attached` already carries `widthPx`/`heightPx` —
log what the real HDMI output reports before accepting any performance budget.

---

## 12. Build order

1. **Shared image model** (§6). Smallest, fixes a latent v0.1 bug, and everything else depends on it.
2. **`Frame` + `frame()` + `Scene.mode`**, with `effectiveVisual()` deleted and tests updated. No
   visible change; pure refactor to make the rest expressible.
3. **Transition skeleton** with `cut` and `fade` only. Proves synchronisation, interruption and the
   opaque-layer rule before any noise maths exists.
4. **Watercolor.** Now only the mask and the ramp are new.
5. **`Campagne.md` reader + `transition:` key.** Transitions become campaign-configurable.
6. **INFO mode**: panel model, known fields, moon, virtual-resolution layout, the bar button.
7. **Documentation amendments**, in the commit that lands INFO.

Steps 1–4 are independent of 5–7 and could ship first if Thursday's session suggests transitions
matter more than the info panel — or the reverse.

---

## 13. Answers

- **Timing:** build now, ship before Thursday. The risk was raised — Thursday becomes the first real
  session *and* the first run of rewritten presentation code — and accepted. Mitigation: preserve a
  v0.1 APK before pushing, since CI's rolling `latest` release overwrites it.
- **Order:** transitions first (steps 1–4), then campaign info (5–7).
- **Known fields:** `date`, `show_lunar_state`, plus `title` (panel heading) and `location`
  (promoted headline). Derived `season` deferred.
- **Duration:** hardcoded. `transition: <name>` is the whole config surface.

---

## 14. Open questions

1. **What is the real player-display resolution?** Gates the performance budget (§11).
2. **Should `setScaling()` stamp a transition?** It does change what players see, so arguably yes —
   but it is a config change, not a scene change. Currently untested either way.
3. **Panel background**: is `background:` per-campaign only, or should INFO be usable with no
   background at all (pure black) as the common case?
4. **Transition duration in `Campagne.md`?** Decision 7 says the campaign picks a transition *by
   name*. A `duration:` key is defensible once a session proves the default wrong; `band:` is not —
   low values cause contour banding whose cause would not be obvious from a config file.
5. **`party` / `title` / derived `season`** — worth adding now, or wait until a session proves the
   panel too bare?
