# Product behavior

## The loop

Open a tracked app → buddy counts silently → thresholds escalate → buddy **acts out**
the elapsed time (peek → fidget → popcorn → nest → nap) → user leaves → confetti
celebration + session recorded → reclaimed time accrues → cosmetics unlock.

## Escalation ladder (default: Playful, 5-min first check-in)

| Level | Trigger | Experience |
|---|---|---|
| 1 | first threshold | **Silent peek** from the screen edge — no text |
| 2 | +5 min | Pops in, one short line, talk bounce |
| 3 | +5 min | Pocket clock + timer chip (MM:SS from monotonic clock) |
| 4 | +5 min | Prop comedy (popcorn/blanket), wobble |
| 5 | +5 min | Exhausted face, droopy lids, "done with you" energy |
| 6 | +5 min / long session | Full nap, Zzz, one eye open |

Presets rescale the ladder: Chill (8m start, sparse), Playful (5m), Persistent (3m),
Chaos (90s, unhinged). Cooldowns, per-day caps, dismissal grace and quiet hours are
policy fields — never hard-coded `if (elapsed > …)`.

## PAUSE / RESUME — the session lifecycle (user-directed flow)

- Leaving the tracked app (home, another app, screen off) **pauses** the session:
  the buddy hides WITH the app and the timer freezes. Nothing extra is counted.
- Returning to the SAME tracked app within the resume window (10 min by default)
  **resumes from the exact paused elapsed** — no reset, no away-time counted. If the
  buddy was persistent when the user left, it returns immediately with the timer
  picking up mid-flight.
- Staying away past the window (or the tracked app being killed) closes the session
  at its pause point; the next open starts a fresh session at 00:00 and banks the
  old one. Away time is never attributed to anyone.

## PERSISTENCE & DISMISSAL (spec §13–§35)

- **The intervention is persistent.** After the entrance it stays — no auto-quiet, no
  timeout, no animation-completion teardown. The only exits: a completed HOLD by the
  user, or the tracked app losing foreground.
- **Hold-to-dismiss (~1.2s, configurable):** tap/short-press never dismisses; early
  release or dragging away resets the progress ring; the buddy panics while held and
  delivers a parting line ("Finally.") on completion. Dismissal does NOT end the
  session — the timer keeps counting.
- **Re-intervention:** the escalation ladder repeats past its last rung at the policy's
  repeat interval for as long as the session lives — dismissing never buys silence
  (one hold only grants the policy's dismissal grace, then the buddy returns).
- **Hide with the app:** the moment the tracked app exits/backgrounds/is killed, the
  buddy vanishes with it — it never lingers over another app or the launcher.

## Time presentation (spec §5–§11)

- Session timer (MM:SS from the monotonic clock) and Today total (persisted + live
  session elapsed) are separate concepts, never conflated.
- Overlay modes: SESSION_ONLY / CYCLE (default — alternates every ~3s with a 300ms
  character-driven slide) / STACKED. Configurable in Settings.
- The buddy owns the timer: it glances at the card on every flip and carries the
  pocket-clock prop during CLOCK_TAP acts.

## Anti-annoyance (product-critical)

- One intervention per evaluation tick (deepest reached, no bursts).
- Cooldown between interventions; dismissal adds extra grace.
- Daily interruption cap; quiet hours suppress interventions (sessions still tracked).
- Rapid reopen within the policy window gets a special reaction, not a fresh ladder.
- Today-total milestone reactions are rare and single-shot per threshold.

## Behavior principles

- Awareness, not shame. Copy is short, warm, never guilt-heavy; the buddy never blocks
  the screen and never steals focus.
- The character communicates through acting; text is a garnish (level 1 has none).
- Reclaimed time (trailing-7-day average minus today) is the only reward currency;
  transparent cosmetic unlocks, no loot boxes, no streak shaming.
- Leaving after a nudge triggers a celebration over whatever is on screen — the buddy
  earned it, and so did you.
