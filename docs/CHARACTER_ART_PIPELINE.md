# Character art pipeline

## Decision

DoomBuddy uses four local animated GLBs rendered by Google Filament: three original toy
performers and the existing licensed fox. The selected preview and overlay use the same
model. Static roster cards use small procedural silhouettes (or Fenn's portrait), avoiding
one GPU scene per card. An animated procedural fallback runs while loading or after failure.

This is deliberate: the overlay must start quickly, remain lightweight over another app,
work offline, and survive Android process/window recreation. The local portrait is a reliable
startup/failure fallback, while the GLB provides real depth, materials, articulated nodes, and authored
motion without a network dependency.

## Options considered

| Option | Strength | Why it is not V1 |
|---|---|---|
| Blender sprite sheets | Highest art direction and predictable overlay cost | Requires an external render/export pipeline and larger binary assets |
| Stock character packs | Ready-made meshes and locomotion | Still need original scrolling comedy, rig adaptation, consistent styling |
| Rive | Strong state machines and authoring workflow | Adds runtime/licensing/dependency surface and still needs an art production pipeline |
| Lottie | Good for authored vector motion | Less suitable for interactive, timer-aware character acting |
| Native Compose Canvas | Offline, small, deterministic, interactive, easy to test | Requires engineering the rig and drawing routines |
| Local GLB + Filament (chosen) | Real depth, articulated animation, existing stack | Requires device-level GPU/visibility/lifecycle checks |

## V1 roster

The roster shares a camera, key light, measured transparent surface, and clip contract:

- Loui: human intern, coffee/report/slump.
- Buffer: CRT robot, loading eyes/receipt/reboot.
- Goop: slime, white flag/puddle/joyful spring.
- Fenn, a playful fox with Walk, Tail wag, and Look around clips.

The clips are real GLB animations. `CharacterPlaybackPolicy` maps events and mood to
named performances with quiet idle gaps. Timer/message state stays separate from playback
so an animation finishing cannot dismiss the intervention.

Each definition supplies its palette, proportions, personality, dialogue banks, and
species-specific details. The renderer owns no product logic. `CharacterDirector` owns
the reusable acting vocabulary and maps threshold/hold/long-session events to poses.

## Runtime contract

The production overlay uses the same `CharacterDefinition`, `MiniCharacter3D`, and
`CharacterPlaybackPolicy` as onboarding and settings. The business state machine owns
visibility independently from animation completion:

`HIDDEN -> ENTERING -> PERSISTENT -> DISMISSING -> HIDDEN`

Idle animation is frame-clock driven only while the small overlay view is attached. The
session timer is derived from monotonic elapsed time; animation frames never increment or
reset usage time.

## Asset and licensing policy

The original trio is generated offline using Python's standard library:

```bash
make assets
make test
```

Source geometry, rigs, palettes, and ten reset-safe clips per model live in
`tools/generate_characters.py`. `make test` rebuilds in memory and byte-compares bundled
GLBs, checking winding, normals, animation timing, and articulated channels. No Blender,
asset service, internet access, or user credentials are needed. The meshes use rigid-node
articulation (not skeletal skins); Goop's deformation is authored node scaling.

Keep the invisible static framing mesh; it stabilises normalization across hidden props.
Disable its shadows. Don't recalculate the normalization transform every animation frame.
The licence/provenance notice is `app/src/main/assets/characters/ORIGINAL_CHARACTERS.md`.

The Fenn model and portrait are external licensed assets documented in
`docs/ASSET_LICENSES.md`; the runtime and companion copy remain authored in this repository.
Any future replacement must be stored under `app/src/main/assets/characters/` or
`app/src/main/res/drawable-nodpi/`, accompanied by source/license notes, and must preserve the
same runtime state-machine contract.

## Quality bar for future art

Any replacement must remain recognizable at roughly 64–120dp, use a shared lighting model,
provide entrance/idle/reaction/hold/exit/celebration states, and be profiled inside a
`TYPE_APPLICATION_OVERLAY` window before it replaces the procedural rig.
