# Architecture

## The one diagram

```
Android System
   │ (usage events, screen state, boot)
   ▼
ForegroundAppPoller ──► engineInput (Channel, serialized)
                              │
                        runEngineQueue  ◄─── the ONLY thread touching SessionEngine
                              │
                        SessionEngine (pure, unit-tested)
                              │ SessionOutputs (main thread)
            ┌─────────────────┼──────────────────┐
            ▼                 ▼                  ▼
     CharacterDirector  OverlayController   SessionDao (Room, IO)
     (choreography +     (TYPE_APPLICATION
      spring animation)   OVERLAY + Compose)
```

## Modules & packages

Single `:app` module; layering by package:

| Package | Responsibility | Android dependency |
|---|---|---|
| `core.time` | `MonotonicClock` / `WallClock` abstractions | SystemClock only |
| `session` | `InterventionPolicy`, `SessionEngine`, models | **none** (pure Kotlin) |
| `character` | Definitions, roster, dialogue, director/choreography, renderer | Compose runtime/graphics |
| `monitor` | Poller, `MonitorService`, screen receiver, boot receiver, usage-access helper | full |
| `overlay` | `OverlayController`, lifecycle owner, overlay content | WindowManager + Compose |
| `data` | DataStore prefs, Room DB, `StatsRepository`, reclaimed-time math | Room/DataStore |
| `ui.*` | Theme, onboarding, home, settings, debug, shared components | Compose |

## Concurrency contract (reliability backbone)

These invariants are what make repeated interventions dependable; violations caused
the original "works once, then never again" bug:

1. **Serialized engine inputs.** Poller, tick loop, screen receiver and dismissal all
   `trySend` into `engineInput`; a single consumer coroutine calls `engine.onInput`.
   The engine is deliberately not thread-safe — it must never be touched from two threads.
2. **Main-thread UI.** Every `OverlayController` and `CharacterDirector` public call runs
   on `Dispatchers.Main.immediate`. `WindowManager.add/removeView`, `LifecycleRegistry`
   and Compose composition are main-thread citizens.
3. **The poller must clear state on leave events.** A `ACTIVITY_PAUSED/STOPPED` batch
   with no newer resume sets the foreground package to `null`; otherwise a closed app
   stays "foreground" forever, the session never ends, and thresholds can never re-fire.
4. **Elisped time is derived, never accumulated.** `sessionStartedAtMs` +
   monotonic `elapsedRealtime()`; the tick loop only evaluates thresholds, it never
   counts seconds into state.

## Fractional overlay placement & size (user-controlled)

The buddy's position is stored as **fractions of the usable screen** (`overlayXFrac`,
`overlayYFrac`) plus a size percentage (70–160). Settings offers a draggable mini-screen
map; the overlay window uses TOP|START + pixel coords computed from the fractions and
re-centered after layout measurement (`reposition()`), clamped fully on-screen, and
repositions LIVE when the user drags the map while the buddy is showing. Scale flows
through `OverlayUiState.uiScale` and multiplies every dp in the overlay composables.

## Process resilience (killed/background state)

The companion must survive Android doing its job: `stopWithTask=false` keeps the FGS
alive on task swipe; `onTaskRemoved` schedules a one-shot `setAndAllowWhileIdle`
restart alarm; a 10-minute inexact watchdog alarm re-heals silent process death
(receiver checks service + permissions + onboarding before restarting); BootReceiver
restarts and re-arms the watchdog after reboot; battery-optimization exemption is a
user-initiated flow for aggressive OEMs. Home shows a health row when the service is
down so users can wake it.

## Data model

- **DataStore** (`doombuddy_prefs`): onboarding flag, character id, intensity,
  first-threshold minutes, tracked apps, sound/haptics, overlay position, quiet hours,
  developer mode, unlocked cosmetics.
- **Room** (`doombuddy.db`): `sessions(packageName, startedAt, endedAt, durationMs,
  interventionCount, quickExitAfterIntervention)`. Nothing else is recorded — no
  content, no notifications, no other apps.
- **Reclaimed time** = `max(0, trailing-7-day daily average − today)` over tracked apps.
  Cosmetics unlock from cumulative reclaimed time (transparent ladder, no loot boxes).

## Business state vs animation state (spec §14)

`MonitorService.InterventionState` = HIDDEN → ENTERING → PERSISTENT is the business
machine; the `CharacterDirector` runs its own animation life (idle/choreography/hold).
Animation completion NEVER tears the overlay down — only `InterventionState`
transitions triggered by a completed hold or target-app exit do.

## Session history & opens (UI)

- `sessions` rows carry `isSessionStart` (false for midnight-split continuation rows so
  "opens" never double-counts) and `isCheckpoint` (running-session checkpoint rows are
  invisible to history until finalized into split rows).
- Home surfaces: today total, per-app totals with bars, OPENS TODAY counter,
  SESSIONS counter, a "Sessions today" list with wall-clock start–end timestamps,
  durations and buddy-visit counts, plus a live in-progress row from the service's
  debug state flow.

## Live today & midnight (spec §7, §41)

- `liveToday = persistedTodayForTracked + currentSessionElapsed`; the service caches
  the persisted baseline (Room snapshot) at session start and pushes session/today
  into the overlay once per tick.
- `SessionSplitter` persists a session as ONE ROW PER LOCAL CALENDAR DAY, so a session
  crossing midnight splits its duration across dates while remaining continuous as an
  experience. Interventions are recorded in their own `interventions` table with
  dismissal backfill.

## Policy → behavior mapping

`InterventionPolicy` presets (Chill/Playful/Persistent/Chaos) define the escalation
ladder, cooldown, daily cap, rapid-reopen window, long-session mark and dismissal
grace. The user's "first check-in" setting rescales the ladder's first step and the
rest of the ladder scales proportionally. Thresholds are consumed per session; the
deepest reached-but-unfired threshold fires per tick (no bursts when polls are delayed).

## Overlay lifecycle

`OverlayController.show()` → lazily `attach()`: window context via
`createWindowContext(display, TYPE_APPLICATION_OVERLAY)`, a custom
`OverlayLifecycleOwner` (Lifecycle + SavedStateRegistry + ViewModelStore) wired into a
ComposeView, `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS` (+ `FLAG_NOT_TOUCHABLE` in
passive mode). `hide()` → `removeViewImmediate` → lifecycle destroyed → composition
disposed. BadTokenException (permission revoked mid-flight) is caught and the controller
returns to a detached, retryable state.
