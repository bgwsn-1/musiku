# Apple Music Android — motion reference (reverse-engineered)

Source artifact: `com.apple.android.music` **7.0.0** (versionCode 1614), `base.apk`
(79,546,986 bytes, sha256 `b0828e99ad00d24cb6e4443900b55a1e…`), minSdk 30.

Extracted with REA `inspect_android_package` / `inspect_android_class` /
`inspect_android_method` (JADX 0.7.1) and `apktool d -s` + `aapt2 dump resources`.
Everything below is a *shipped* fact, quoted where possible. Static analysis only —
the APK was never executed.

Expected value to Musiku: Apple's own numbers for sheets, nav, and the animated
Now Playing backdrop. Musiku does **not** need to copy Apple; these are calibration
references that sit next to the web tokens in `globals.css`.

---

## 0. What Apple actually ships (framework inventory)

| Framework | Present? | Evidence |
|---|---|---|
| `androidx.dynamicanimation` (SpringAnimation/FlingAnimation) | **No** | 0 hits in all 4 dex; only the *framework warning string* `"Incomplete SpringAnimation: Either final position…"` survives (pulled in by Compose) |
| **Lottie / Airbnb / bodymovin** | **No** | 0 hits; no `assets/*.json` animation |
| **MotionLayout / ConstraintLayout motion** | **No** | 0 hits |
| Jetpack **Compose** | **Yes**, limited | 115 refs; used for embedded views + `amsuikit` bottom sheets |
| Classic View anims (`res/anim*`) | **Yes** | 26 app-authored anim + 10 animator XML |
| `com.apple.mediaservices.amsuikit` | **Yes** | Apple's own Compose animation library (`modalbottomsheet`) |
| Material 3 motion tokens | **Yes** | full `m3_sys_motion_*` set (they ship the M3 library) |

So "Apple Music motion" = **M3 motion tokens + a few hand-written anim XMLs +
one custom Compose spring sheet + custom canvas views**. No hidden animation engine.

Apple's own animation classes:

```
com.apple.mediaservices.amsuikit.modalbottomsheet.AnimationSpec        (+ $Forward $Back $NoAnimation)
com.apple.android.music.player.LyricsBackgroundLayerView               ← the animated backdrop
com.apple.android.music.player.PlayerTransitionImageView               ← artwork crossfade bridge
com.apple.android.music.common.views.DancingProgressBar                ← the equalizer
com.apple.android.music.common.views.CircleProgressView                ← seek / progress ring
com.apple.android.music.common.views.ExpandCollapseTextView            ← "more" text expand
com.apple.android.music.common.{PlayerSeekBar,MinimalSeekBar}
com.apple.android.music.common.behavior.{PlayerScrollingViewBehavior,
                                          StaticCollapsedBottomSheetBehavior}
com.apple.android.music.settings2.fragment.MotionSettingsFragment      ← reduce-motion prefs
com.apple.android.music.settings2.model.MotionSettingsViewModel
```

---

## 1. Motion tokens (Material 3, shipped verbatim)

Durations (`res/values/integers.xml`), ms:

```
short1 50   short2 100  short3 150  short4 200
medium1 250 medium2 300 medium3 350 medium4 400
long1 450   long2 500   long3 550   long4 600
extra_long1 700 … extra_long4 1000
```

Easing (`res/values/strings.xml` + `dimens.xml`):

```
standard               cubic-bezier(0.2, 0.0, 0.0, 1.0)
standard_accelerate    cubic-bezier(0.3, 0.0, 1.0, 1.0)
standard_decelerate    cubic-bezier(0.0, 0.0, 0.0, 1.0)
emphasized_accelerate  cubic-bezier(0.3, 0.0, 0.8, 0.2)
emphasized_decelerate  cubic-bezier(0.1, 0.7, 0.1, 1.0)
legacy (M2)            cubic-bezier(0.4, 0.0, 0.2, 1.0)   == fast_out_slow_in
emphasized (3-point, path):
  M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4
         C 0.208333, 0.82, 0.25, 1, 1, 1
```

Custom app interpolators (`res/interpolator`):

```
fast_out_slow_in      (0.4, 0.0, 0.2, 1.0)
mr_fast_out_slow_in   (0.4, 0.0, 0.2, 1.0)   # same
mr_linear_out_slow_in (0.0, 0.0, 0.2, 1.0)
fragment_fast_out_extra_slow_in (res/anim) — 3-point path:
  M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4
         C 0.208333, 0.82, 0.25, 1, 1, 1
```

Other app integers: `slide_animation_duration = 250`,
`config_navAnimTime = 150`.

---

## 2. The modal bottom sheet (Apple Compose, `amsuikit`)

`AnimationSpec` is a sealed type with `Forward`, `Back`, `NoAnimation`.
`Forward.transitionSpec` passes direction index **4**, `Back` passes **5**:

```java
public a0 transitionSpec(H.p<?> p) { return AnimationSpec.INSTANCE.createSlide(p, 4); } // Forward
public a0 transitionSpec(H.p<?> p) { return AnimationSpec.INSTANCE.createSlide(p, 5); } // Back
```

`createSlide` body (decompiled):

```java
long j = 1;                                            // packed IntOffset(1,1)
l0 spec = I.m.e(0.0f, 400.0f, new O1.j(j | (j << 32)), 1);
L0 enter = p.a(dir, spec, H.n.INSTANCE);
N0 exit  = p.f(dir, spec, H.o.INSTANCE);
return p.e(new a0(enter, exit), H.b.c(new X2.h(3), 1));
```

> **Correction to an earlier note.** This is **not** a 400 ms tween. `I.m.e`
> has signature `(float, float, Object, int): I.l0`, and `I.l0` is a 3-field
> `(float, float, T)` spec — i.e. Compose's
> `spring(dampingRatio, stiffness, visibilityThreshold)`. The `1` default-mask
> bit defaults arg0, so Apple's sheet spec is:

```kotlin
spring<IntOffset>(
    dampingRatio      = 1f,            // critically damped → no overshoot
    stiffness         = 400f,          // softer than Compose's 1500f default
    visibilityThreshold = IntOffset(1, 1),
)
```

`NoAnimation` uses the same `L0`/`N0` with a null animation spec (instant).

Direction index 4 = forward, 5 = back ⇒ the sheet slides in the session's
navigation direction, and animates **both** enter and exit with one shared spring.

---

## 3. Fragment / nav transitions (`res/animator`)

All app-authored animator XML — note the **staggered alpha** (a leading
scroll/blank beat, then the fade) plus a 300 ms **scale** on the same curve:

```
fragment_open_enter : alpha 0→0 (50ms linear) then 0→1 (50ms linear)
                      scaleX/Y 0.85→1.0 (300ms, fragment_fast_out_extra_slow_in)
fragment_open_exit  : alpha 1→1 (50ms) then 1→0 (50ms)
                      scaleX/Y 1.0→1.15 (300ms)
fragment_close_enter: alpha 0→0 (66ms) then 0→1 (50ms)
                      scaleX/Y 1.1→1.0 (300ms)
fragment_close_exit : alpha 1→1 (66ms) then 1→0 (50ms)
                      scaleX/Y 1.0→0.9 (300ms)
fragment_fade_enter : alpha 0→1, 150ms linear
fragment_fade_exit  : alpha 1→0, 150ms linear
nav_default_*_anim  : alpha only, config_navAnimTime = 150ms linear
```

So the "open" reads as: old screen scales **up** out (1.0→1.15) while the new
one scales **up** in (0.85→1.0) — a push-through-zoom, 300 ms, on the
`fragment_fast_out_extra_slow_in` curve.

## 4. Activity / dialog transitions (`res/anim`)

```
activity_fade_in       alpha 0→1, 200ms, decelerate_quad
activity_fade_out      alpha 1→0, 150ms, accelerate_quad
activity_slide_from_right  X 100%p→0,   250ms, accelerate_decelerate
activity_slide_from_left   X -100%p→0,  250ms, accelerate_decelerate
activity_slide_to_left     X 0→-100%p,  250ms, accelerate_decelerate
activity_slide_to_right    X 0→100%p,   250ms, accelerate_decelerate
activity_slide_up          Y 100%p→0,   250ms, accelerate
activity_slide_down        Y 0→100%p,   300ms, accelerate
activity_hold              Y no-op,     250ms
dialog_slide_up        Y 100%→0, config_shortAnimTime, decelerate
dialog_slide_down      Y 0→100%, config_shortAnimTime, accelerate
slide_up / slide_down  Y 100%↔0, config_shortAnimTime, no interpolator
slide_in_right         X 100%p→0,   200ms linear
slide_out_left         X 0→-100%p,  200ms linear
slide_out_right        X 0→100%p,   200ms linear
share_lyrics_sheet_fade_in  alpha 0→1, 50ms, linear_out_slow_in
rotation_anim          rotation 0→360, 500ms, accelerate_decelerate, infinite
```

## 5. M3 component anims (shipped, = library defaults)

```
m3_bottom_sheet_slide_in : emphasized easing, duration medium4 (400ms)
                           translate Y 20%p→0 + alpha 0→1
m3_bottom_sheet_slide_out: translate emphasized medium3 (350ms)
                           alpha     emphasized medium2 (300ms), Y 0→20%p
m3_motion_fade_enter     : emphasized_decelerate, medium4 (400ms),
                           alpha 0→1 + scale 0.8→1.0
m3_motion_fade_exit      : emphasized_accelerate, short3 (150ms)
m3_side_sheet_enter_* / m3_side_sheet_exit_*
```

---

## 6. `DancingProgressBar` — the equalizer (best small-widget reference)

Dimens (`res/values`): total height **16dp**, space between bars **1dp**,
single bar width **3dp**.

Fields: `Paint`, `int barWidth`, `int barSpace`, `int min`, `int max`,
`Random`, `a[] bars`. Per-bar `a`:

```
f42289a = current height (int)
f42290b = boolean
f42291c = boolean (active)
f42292d = ValueAnimator
f42293e = peak / target
```

Per-bar animator (inner `a.<init>`):

```java
ValueAnimator v = new ValueAnimator();
v.setDuration(270L);
v.setInterpolator(new LinearInterpolator());
v.setRepeatMode(2);      // REVERSE
v.setRepeatCount(-1);
v.addListener(...);
v.addUpdateListener(anim -> { bar.current = (Integer) anim.getAnimatedValue(); postInvalidate(); });
```

Layout math (`c()`):

```java
barSpace  = res.getDimensionPixelSize(dancing_progress_bar_space_width);   // 1dp
barWidth  = res.getDimensionPixelSize(dancing_progress_single_bar_width);  // 3dp
min       = (int)(getHeight() * 0.1f);
max       = getHeight() - min;
```

Start (`b()`), randomized **once per bar per cycle**, then reversed forever:

```java
for (a bar : bars) {
    bar.active = true;
    float r = random.nextFloat();
    bar.animator.setIntValues(min, (int)(max * r + min));
    if (!bar.animator.isRunning()) bar.animator.start();
}
```

Draw (`onDraw`) — bars anchored to the **bottom**, left-to-right:

```java
int x = 0;
for (a bar : bars) {
    canvas.drawRect(x, getHeight() - bar.current, x + barWidth, getHeight(), paint);
    x += barWidth + barSpace;
}
```

Apple vs. Musiku today: Musiku's `Eq` uses `tween(420 + i*130)` with
`RepeatMode.Reverse` on a height fraction `0.3..1`. Apple uses **270 ms flat,
linear, reverse, randomized peak each cycle**, real dp dimensions
(16dp tall / 3dp wide / 1dp gap). Apple's bar *floor* is `0.1*height`, and the
*peak* is re-randomized on every reverse-cycle, which is why Apple's bars look
less metronomic. Musiku's `Eq` height is 14dp — close; the timing/randomization
is the part worth borrowing.

---

## 7. `LyricsBackgroundLayerView` — the Now Playing animated backdrop

This is the "animated art" behind Now Playing (and lyrics). Constructor:

```java
cornerRadius = R.dimen.player_sheet_corner_radius;
baseAlpha   = 0;
fadeAlpha   = 0;
scale       = f45963b0;   // device-class constant, see <clinit>
saturation  = 2.5f;       // normal; 3.5f when reducedEffects
blurPaint   = 2 Paint(FLAG_FILTER_BITMAP)
fadePaint.setAlpha(0); artPaint.setAlpha(255);

fadeIn = ValueAnimator.ofFloat(1f, 0f);
fadeIn.setDuration(1000L);
fadeIn.setInterpolator(new PathInterpolator(0f, 0f, 0.3f, 1f));   // cubic-bezier(0,0,0.3,1)

rot1 = ValueAnimator.ofFloat(0f, -360f); rot1.setDuration(120000L); rot1.setRepeatCount(-1); // 2 min
rot2 = ValueAnimator.ofFloat(0f,  360f); rot2.setDuration( 90000L); rot2.setRepeatCount(-1); // 1.5 min
rot3 = ValueAnimator.ofFloat(0f,  360f); rot3.setDuration( 70000L); rot3.setRepeatCount(-1); // 1.17 min
// all three: LinearInterpolator
```

Backdrop compose/decode (`b(bitmap, w, h, playTime, ctx)`):

```java
int iw = round(w * 1.3f / scale), ih = round(h * 1.3f / scale);
int big = round(max(iw, ih) * 1.3f);
float s = big / bitmap.getHeight();
float dx = -(big - iw) / 2f, dy = -(big - ih) / 2f;
// three stacked, counter-rotating, overscanned copies of the same artwork:
Matrix m = new Matrix(); m.setScale(s, s);
m.postRotate(angle, big/2f, big/2f);
m.postTranslate(dx, dy);
Paint p = new Paint(FLAG_FILTER_BITMAP);
ColorMatrix cm = new ColorMatrix(); cm.setSaturation(2.5f);   // ← punchy, not the source color
p.setColorFilter(new ColorMatrixColorFilter(cm));
canvas.drawBitmap(bitmap, m, p);
// copy 2 adds postTranslate(-0.95f*iw, -0.7f*ih)
// copy 3 adds postTranslate(-0.7f*iw, 0.95f*ih)   (third offset pair)
```

Blur: `b()` builds an **overscanned (×1.3) ARGB_8888 scratch bitmap**, pitches
the same 3 rotating layers into it, then the view blurs that (downscale/stack)
and the `blurPaint` (alpha 0→255) cross-fades the blurred copy over the sharp
one. The static field `f45965d0` caches a blank bitmap.

Device-class constants (`<clinit>`): if `Configuration.densityDpi >= 0x1A4 (420)`
→ scale `24f`/`48f`, else `16f`/`72f` (i.e. different blur radius / size on
dense vs. normal panels).

Reduced effects (`setReducedEffects(true)`): saturation `2.5 → 3.5`,
scale → the alternate constant. `setSkipInvalidate(true)` freezes the rotations.
Pause/resume: `c(boolean)` pauses all four animators; `e()`/`d()` resume/pause.

**Takeaway for Musiku:** Apple's blurred-artwork backdrop is *not* a static
scaled art + blur. It is **3 counter-rotating copies at 2 min / 90 s / 70 s,
saturation ≈2.5, on a ×1.3 overscan, cross-faded in over 1000 ms on
cubic-bezier(0,0,0.3,1)**. That is the "living backdrop" effect. Musiku today
blurs a single static art via Haze; if the animated backdrop is ever wanted,
this is the recipe.

---

## 8. Motion accessibility settings (Apple)

```
MotionSettingsFragment / MotionSettingsViewModel
  KEY_MOTION_SCREEN, KEY_DISABLE_MOTION
  KEY_REDUCE_PLAYER_MOTION → "Reduce Now Playing Motion"
      summary: "Slow down the animated backdrop in Now Playing."
  disable_motion_setting → "Animated Art"
      summary: "Playlist, album, artist pages, and other artwork will automatically play."
```

Two independent switches: **Animated Art** (artwork motion) and
**Reduce Now Playing Motion** (backdrop speed). A good pattern if Musiku ever
adds a motion pref.

---

## 9. Side-by-side: Apple vs. Musiku today

| Surface | Apple Music 7.0.0 | Musiku (web-derived) |
|---|---|---|
| Bottom sheet motion | `spring(dampingRatio 1f, stiffness 400f, IntOffset(1,1))`, direction-aware | web sheet has no spring; Musiku uses `ModalBottomSheet` M3 defaults |
| Sheet slide distance | 20% of height (M3) / full-height slide (amsuikit) | n/a |
| Nav transition | alpha-only 150 ms linear (fragments); push-zoom 300 ms scale 0.85↔1.15 | `tween(340)` FastOutSlowIn slide |
| Dialog | slide up/down, `config_shortAnimTime` | Musiku dialog uses scale+fade |
| Equalizer | 270 ms linear reverse, randomized peak, 16/3/1 dp | `tween(420 + i*130)` reverse, frac 0.3..1, 14 dp |
| Press feedback | none in anim XML (ripples from M3) | web `active:scale-*` + `ease-spring cubic-bezier(0.34,1.4,0.64,1)` |
| Now Playing backdrop | 3 counter-rotating blurred copies, 2 min/90 s/70 s, sat 2.5, 1 s fade-in | static art + Haze blur |
| Reduce motion | 2 prefs (Animated Art, Reduce Now Playing Motion) | none |

### Notes for Musiku, if any of this is adopted

> **Status: implemented.** Liquid glass (light+dark tokens, saturation-aware fills, themed edges,
> sliding tab thumb) and the animated backdrop (`AppleBackdrop`) are now in the app. See
> `docs/musiku-liquid-glass-notes.md` for what shipped, the Haze ceiling, and what is left.

1. **Sheet**: M3 `emphasized` + `medium4 (400)` is the Material sheet curve, and
   it is *different* from Apple's spring. Prefer matching the **web** sheet
   (source of truth), not Apple, unless Apple's critically-damped spring reads
   better. A `spring(dampingRatio = 1f, stiffness = 400f)` is a legitimate drop-in
   for a Compose sheet if a springier feel is wanted.
2. **Nav push-zoom**: `fragment_open_enter`'s 0.85→1.0 / 1.0→1.15 at 300 ms on
   `fragment_fast_out_extra_slow_in` (≈ M3 emphasized-decelerate) is a strong,
   distinctive reference for Now Playing ↔ Home.
3. **Equalizer**: switching `Eq` to a flat 270 ms linear reverse with a
   per-cycle random peak is a two-line change and makes it look markedly more
   like Apple. Keep the web's 3-bar layout; the web values are the spec.
4. **Animated backdrop**: the single most "Apple" thing here. Requires a real
   blur pass (RenderEffect / bitmap stack); Haze alone can't do it. Treat as
   optional polish, behind a preference.

---

## 10. Raw evidence

- Extracted bundle: `/tmp/opencode/applemusic/{base.apk,info.json}`
- Decoded resources: `/tmp/opencode/applemusic/apktool_txt/res/{anim,animator,interpolator,transition}`
- App class dump: `/tmp/opencode/applemusic/apple_classes.txt` (10,039 names)
- REA package evidence id: `ev_6472ac5c21b32608fa45bb9bf545665ae6765ec03ad17a34b027f48b7cebff81`
- Decompiler: `~/.local/opt/rea-deps/jadx-headless-mcp-0.7.1-all.jar`
