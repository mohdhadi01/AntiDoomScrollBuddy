# Character art pipeline

## Decision

DoomBuddy V1 uses a native Compose `Canvas` renderer as its 2.5D runtime instead of a
third-party animation package or a real-time 3D engine. The new cast is humanoid/toy-like
with clothing, articulated limbs, staged props, material lighting, and bounded acting
poses; it is no longer a bubble/blob character rig.

This is deliberate: the overlay must start quickly, remain lightweight over another app,
work offline, and survive Android process/window recreation. A small procedural renderer
keeps the APK free of large binary art packs while still providing gradients, rim light,
specular highlights, silhouettes, props, particles, and animated facial features.

## Options considered

| Option | Strength | Why it is not V1 |
|---|---|---|
| Blender sprite sheets | Highest art direction and predictable overlay cost | Requires an external render/export pipeline and larger binary assets |
| Licensed 3D pack + runtime renderer | Real mesh depth and reusable source models | Online CC0 packs reviewed for this build are static source models, not ready-to-ship Android character performances |
| Rive | Strong state machines and authoring workflow | Adds runtime/licensing/dependency surface and still needs an art production pipeline |
| Lottie | Good for authored vector motion | Less suitable for interactive, timer-aware character acting |
| Native Compose Canvas | Offline, small, deterministic, interactive, easy to test | Requires engineering the rig and drawing routines |
| Real-time 3D | Maximum depth and camera freedom | Too much startup, memory, APK, and overlay complexity for V1 |

## V1 roster

The launch roster is intentionally three characters with shared presentation rules:

- **Marlow** — a slouched hoodie-wearing couch regular; lazy and sarcastic.
- **Orbit** — a compact astronaut with visor, life-support panel, and antenna signal; curious and confused.
- **Riff** — a street-skater with a board, bright jacket, and unstable confidence; energetic and dramatic.

Each definition supplies its palette, proportions, personality, dialogue banks, and
species-specific details. The renderer owns no product logic. `CharacterDirector` owns
the reusable acting vocabulary and maps threshold/hold/long-session events to poses.

## Runtime contract

The production overlay uses the same `CharacterDefinition`, `CharacterDirector`, and
`characterDraw` path as onboarding and settings previews. The business state machine owns
visibility independently from animation completion:

`HIDDEN -> ENTERING -> PERSISTENT -> DISMISSING -> HIDDEN`

Idle animation is frame-clock driven only while the small overlay view is attached. The
session timer is derived from monotonic elapsed time; animation frames never increment or
reset usage time.

## Asset and licensing policy

There are no external character assets in V1. All character shapes and props are original
source code in `character/CharacterRenderer.kt`, and all copy is authored in
`CharacterRoster.kt`. This avoids stock-art provenance, font licensing, and mismatched
multi-image character identity. If V2 moves to authored sprites or Rive, exports must be
stored under `app/src/main/assets/characters/`, accompanied by source/license notes, and
must preserve the same runtime state-machine contract.

## Quality bar for future art

Any replacement must remain recognizable at roughly 64–120dp, use a shared lighting model,
provide entrance/idle/reaction/hold/exit/celebration states, and be profiled inside a
`TYPE_APPLICATION_OVERLAY` window before it replaces the procedural rig.
