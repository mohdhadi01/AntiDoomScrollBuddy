# Testing

## Unit tests (`./gradlew :app:testDebugUnitTest`)

- `SessionEngineTest` + `SessionEngineRecurringTest`: threshold timing, delayed-poll
  burst suppression, cooldowns, app-switch end reasons, rapid reopen, screen-off,
  untracked apps, quiet hours, daily cap, dismissal grace, long-session single-fire,
  **recurring re-interventions past the ladder end**, cap/grace interaction.
- `ForegroundResolverTest`: the live-resumed set model, late-STOPPED interleavings,
  open→leave→reopen cycles.
- `TimeDataTest`: midnight-split persistence, live-today math, milestone single-fire.
- `ChoreographyTest`: acting pools per progress bucket, anti-repetition,
  gap escalation, hold-time bounds.

The engine and choreography are pure Kotlin — the behavior math is fully covered
without a device. UI/platform layers are verified by inspection + debug tools
(on-device simulators exist in the Developer screen for every intervention level).

## Regression guardrails (code-review invariants)

1. Foreground leave events must clear the poller's current package (the
   works-once-then-never bug lives exactly here).
2. **The tick loop MUST exist.** Thresholds are evaluated ONLY on `SessionInput.Tick`;
   `MonitorService.runTickLoop` sends one per second. It was accidentally dropped in a
   rewrite once and NO intervention could ever fire on-device ("set 1 min, nothing
   shows"). Any service refactor must re-verify: foreground events alone NEVER fire
   thresholds.
3. Engine inputs flow through a single coroutine; no direct `engine.onInput` elsewhere.
4. Overlay/director calls only from Main.
5. No animation coroutines outlive `director.hide()`.

## Session recording durability

Running sessions are checkpointed to Room every 30s (`updateSessionDuration`), so a
hard process kill loses at most ~30s; on graceful session end the checkpoint row is
replaced by the final midnight-split rows. Home's today total live-updates from these
rows (Room Flow invalidates on the checkpoint UPDATE).

## Manual QA checklist (real device, before release)

- Overlay over Instagram/YouTube/TikTok/Reddit: scrolling smooth, keyboard fine,
  video uninterrupted, no focus steal, buddy disappears within ~2s of leaving.
- Repeat the above 10+ cycles — session end/reopen must always re-arm thresholds.
- Screen off/on mid-session; rotation; split screen; PiP; force-stop; reboot.
- Permission revocation mid-session (overlay + usage) must degrade gracefully.
- Frame pacing via `dumpsys gfxinfo com.doombuddy.debug` while the buddy is visible.
