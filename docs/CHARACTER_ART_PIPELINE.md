# Character art pipeline

## Decision

DoomBuddy V1 uses one local, animated fox GLB rendered by Google Filament. The Android app
uses the same model in the selector, preview, and floating overlay. A local portrait fallback
is kept underneath the GL surface so the character is never blank while the GPU scene warms
up or if a device cannot render Filament.

This is deliberate: the overlay must start quickly, remain lightweight over another app,
work offline, and survive Android process/window recreation. The local portrait is a reliable
startup/failure fallback, while the GLB provides real depth, materials, skinning, and authored
motion without a network dependency.

## Options considered

| Option | Strength | Why it is not V1 |
|---|---|---|
| Blender sprite sheets | Highest art direction and predictable overlay cost | Requires an external render/export pipeline and larger binary assets |
| Licensed 3D pack + runtime renderer | Real mesh depth, skinning, and authored clips | More native memory and APK size than procedural Canvas |
| Rive | Strong state machines and authoring workflow | Adds runtime/licensing/dependency surface and still needs an art production pipeline |
| Lottie | Good for authored vector motion | Less suitable for interactive, timer-aware character acting |
| Native Compose Canvas | Offline, small, deterministic, interactive, easy to test | Requires engineering the rig and drawing routines |
| Real-time 3D | Maximum depth and camera freedom | Too much startup, memory, APK, and overlay complexity for V1 |

## V1 roster

The launch roster is one character with shared presentation rules:

- Fenn, a playful fox with Walk, Tail wag, and Look around clips.

The clips are real GLB animations. The Filament view rotates through the available clips as
the companion remains visible. The timer/message state stays separate from animation playback
so an animation finishing cannot dismiss the intervention.

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

The Fenn model and portrait are external licensed assets documented in
`docs/ASSET_LICENSES.md`; the runtime and companion copy remain authored in this repository.
Any future replacement must be stored under `app/src/main/assets/characters/` or
`app/src/main/res/drawable-nodpi/`, accompanied by source/license notes, and must preserve the
same runtime state-machine contract.

## Quality bar for future art

Any replacement must remain recognizable at roughly 64–120dp, use a shared lighting model,
provide entrance/idle/reaction/hold/exit/celebration states, and be profiled inside a
`TYPE_APPLICATION_OVERLAY` window before it replaces the procedural rig.
