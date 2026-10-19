# Animation system & technology decision

## Decision: procedural Compose Canvas characters (no Rive, no Lottie)

Researched September 2026 against the overlay use case:

| Criterion | Rive | Lottie | **Native Compose (chosen)** |
|---|---|---|---|
| Commercial license | `.riv` **production export requires a paid plan** (Cadet $9/seat/mo+, verified rive.app/pricing) | Free runtime; assets need an AE designer pipeline | **none** |
| Runtime cost | ~1.5–2MB native lib (NDK) | ~500KB + json parsing | **0 extra** (Compose already required) |
| State machine control | excellent (proprietary format) | none at runtime (property remapping only) | **total** (Kotlin) |
| Idle/battery | renderer pausable | pausable | **only draw-on-invalidate; zero frames when hidden** |
| Overlay suitability | works | works | works with manual ViewTree wiring |
| Asset workflow | editor + paid export | After Effects + plugin | **code only** |
| Expressiveness for 2.5D | high | medium (baked) | **high** (gradients, springs, per-part rig) |

Rive was explicitly *not* chosen just because it was mentioned earlier — its paid
production-export gate and NDK dependency lose to a fully-controlled procedural rig
for a six-character roster that must live inside a lightweight overlay.

## How the 2.5D feel is built (no game engine)

- **3-stop vertical body gradient** (lit top → saturated core → shaded base) for
  dimensional lighting; top **rim light** band clipped to the body path.
- **Contact ambient-occlusion** gradient where the body meets the ground + soft radial
  **ground shadow** that shrinks and fades as the buddy jumps (jank-free lift reading).
- **Specular highlight** on the body's upper-left, kept off the robot's dark face screen.
- **Squash & stretch** anchored at the feet (`scale` pivot), spring-driven with
  underdamped ratios for cartoon overshoot; entrances (bounce/fall/slide/pop/portal/peek)
  keyframe impact squashes explicitly.
- **Species life**: cat tail wag + ear rig, frog throat bubble synced to breath,
  ghost hover wisps, duck wing flaps + parting beak, robot blinking LED, blob jiggle.
- **Particles** (procedural, bounded): Zzz glyph trails, confetti, dizzy stars, sparkles,
  snack crumbs — all drawn from a looping time phase, no allocations per frame.

## Performance contract

- `Modifier.characterDraw` uses `drawWithCache`: paths/brushes are cached per size;
  pose reads happen **at draw time**, so 60fps idle motion costs zero recomposition.
- All animation coroutines live in `CharacterDirector` and are **cancelled on hide** —
  when the buddy is not on screen there are no frames, no tickers, no wakelocks.
- No per-frame allocations in steady state (pose is an immutable data class swapped
  atomically; particles use indexed loops).
- Main thread only; the director's frame clock comes from `AndroidUiDispatcher`
  (a plain `Dispatchers.Main` scope crashes on `withFrameNanos`).
