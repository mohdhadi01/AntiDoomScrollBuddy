# Character system

A character is **data plus one species draw routine** — the intervention engine, the
choreography and the renderer never change when a buddy is added.

## Anatomy of a buddy

`CharacterDefinition` = identity (id, name, species, personality, tagline) +
`Palette` (9 colors) + `Proportions` (normalized rig dims) + built-in `Accessory` +
`DialogueBank`.

V1 launch roster — THREE characters (all original, procedurally drawn, no assets):

| Buddy | Species | Personality | Signature |
|---|---|---|---|
| The Melt | blob | Lazy / sarcastic | melty asymmetric body with a drip, slow collapse, "Bro." |
| Zobo | alien | Confused observer | floats, pulsing antennae, third-eye dot, head tilts |
| Quackles | duck | Chaotic | parting beak, flapping wings, hair swirl, full panic |

The procedural rig still contains compatibility/future definitions for pre-release
preference values, but they are not presented as launch choices.

3D feel: every body carries a spherical gloss layer (offset radial highlight), 3-stop
base gradient, rim light and contact shading — the premium-"Dot" dimensional read.

The FROG/CAT/ROBOT/GHOST rigs remain in the renderer for future roster expansion.
Each character gets its own entrance style, idle physics, dialogue banks (including
dismissal and today-milestone lines), app quips and escalation flavor — selection
changes real behavior, not just pixels (spec §22).

## Show, don't tell

Speech is a garnish; the primary channel is **acting**. `CharacterDirector` runs a
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

Mood (escalation level 1–6) shapes the resting face: mouth shape, eyelid droop, brows.
Escalation-driven entrance: level 1 peeks in, level 2 pops, later levels randomize.

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
