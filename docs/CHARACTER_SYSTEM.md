# Character system

A character is **identity data plus a local animated GLB**. The intervention engine
remains separate from playback; named clips implement the shared performance contract.
Species draw routines supply small static thumbnails and animated failure fallbacks.

## Anatomy of a buddy

`CharacterDefinition` = identity (id, name, species, personality, tagline) +
`Palette` (9 colors) + `Proportions` (normalized rig dims) + built-in `Accessory` +
`DialogueBank`.

Current selectable roster:

| Buddy | Species | Personality | Signature |
|---|---|---|---|
| Loui | human intern | Deadpan | coffee break, scrolling report, exhausted slump |
| Buffer | CRT robot | Confused | loading eyes, receipt printer, reboot |
| Goop | slime | Dramatic | white flag, puddle meltdown, spring-back celebration |
| Fenn | fox | Playful / chaotic | tail wag, look-around, bad decisions |

The app bundles the “Fox” GLB by Bake3D with Walk, Tail wag, and Look around clips. It is
licensed under CC BY 4.0; see `docs/ASSET_LICENSES.md` for the required attribution.

The original trio uses articulated rigid nodes, smooth meshes, PBR materials, and
embedded transform-keyframe clips. They are authored by `tools/generate_characters.py`,
not downloaded stock characters. Combined GLB size is approximately 221 KiB.

The roster uses stable IDs `loui`, `buffer`, `goop`, `melt`. Existing `melt` preferences
still select Fenn; new installs and unknown IDs resolve to Loui. All four are selectable
in both onboarding and Settings through `CharacterStudio`. Each has distinct dialogue.

## Named 3D performance contract

`CharacterPlaybackPolicy` is a pure, clock-injected director. The three original GLBs
contain Idle, Entrance, Notice, Waiting, Escalate, Meltdown, Poke, Hold, Dismiss, Celebrate.
Mood 0 rests, 1 notices, 2 waits, 3–4 escalates, 5–6 melts down. Automatic acts are
separated by four-second idle gaps, rather than blindly changing clips every 4.5 seconds.
Repeated poke/preview tokens restart a reaction without rebuilding the renderer.
Holds suspend ordinary acts; dismissal has priority. Session-end celebrations are
forwarded from the overlay. Timer updates do not restart animation.

Fenn resolves events to his three existing clips; he does not have the new trio's props.
Preview chips replay a one-shot and return to Auto after 3.2 seconds. Hold the stage for
1.2 seconds to practise the exit without changing actual session settings.

`MiniCharacterView` renders at at most 30fps and stops frame callbacks when detached or
hidden. Filament's helper must be nonopaque and configured with measured dimensions
before attachment; otherwise an already-created texture receives a zero-size viewport.
Transparent framing geometry must not cast shadows. Failures log `MiniCharacterView`
and switch to an animated procedural renderer, never silently freeze a portrait.

## Legacy procedural choreography

Speech is a garnish; the primary channel is **acting**. The existing `CharacterDirector` runs a
choreography loop whose acting pool is a function of **session progress** (elapsed /
last threshold):

| Progress | What the buddy does |
|---|---|
| 0–0.2 | peeks from the screen edge, waves, scrolls its own tiny phone |
| 0.2–0.45 | yawns, taps a pocket clock, phones-and-chills |
| 0.45–0.7 | popcorn (with crumbs), stretches, pulls up a blanket |
| 0.7–0.9 | pillow + blanket, facepalms, dozes off |
| 0.9–1.0 | full nap with Zzz (one eye opens to check on you), dizzy, dramatic despair |

Rules encoded in `Choreography` (pure, unit-tested): no immediate action repeats,
holds stay comedy-sized (0.5–9s), and acts come more often the longer the session.
Level-1 interventions are **silent** — a peeking buddy IS the message.

This older pose/prop vocabulary remains for procedural drawing and overlay transforms.
It is not the GLB clip list. Native 3D acting is driven by the named contract above.

## Dialogue

`DialogueEngine` picks from per-event banks (`peek/speak/clocking/ridiculous/fedUp/
gaveUp/rapidReopen/longSession/celebration/dismissed/lateNight`) with:
- no immediate repeats per (character, bucket),
- app-specific quips (original copy per doomscroll destination) injected ≤40% of the
  time at levels 2–3,
- late-night overrides for threshold levels ≤2 between 23:30–05:00.

Copy rules: short wins ("Bro." over paragraphs), internet-native, never shame-based,
no forced slang. Lines support `{app}` / `{mins}` substitution.

## Unlockable cosmetics

Time-reclaimed ladder unlocks procedural accessories (hat → glasses → scarf →
sunglasses → bow) rendered on top of built-ins. Transparent thresholds, no gambling.
