# Android platform notes (verified Sept 2026)

Sources: developer.android.com API references and behavior-change pages for 14/15/16/17.

## Foreground app detection

- **Polling `UsageStatsManager.queryEvents(begin, end)` is the sanctioned mechanism.**
  The push-style listeners (`registerAppUsageObserver`, `registerUsageSessionObserver`,
  `registerAppUsageLimitObserver`, and any `registerEventListener`) are `@SystemApi`
  and require `OBSERVE_APP_USAGE` — Digital Wellbeing's privilege, not third-party apps.
- Requires `PACKAGE_USAGE_STATS` (manifest, `tools:ignore="ProtectedPermissions"`) and
  the **GET_USAGE_STATS appop** granted via Settings → Usage access
  (`Settings.ACTION_USAGE_ACCESS_SETTINGS`); check with
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, …)`.
- Events: `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED`/`ACTIVITY_STOPPED` (API 29+; values 1/2/23;
  pre-29 equivalents MOVE_TO_FOREGROUND/BACKGROUND). `DEVICE_SHUTDOWN/STARTUP` exist (26/27).
  `ACTIVITY_DESTROYED` is a hidden constant — never depend on it.
- DoomBuddy polls every 2s while the screen is on and reconstructs foreground state
  from the newest resume/leave in each batch. **A leave batch with no newer resume must
  clear the current package** (the reliability invariant in ARCHITECTURE.md).
- Dozing/screen-off is handled both by `PowerManager.isInteractive` and the dynamic
  SCREEN_ON/OFF receiver; sessions end with `EndReason.SCREEN_OFF`.
- Future: Android 17's `queryAppUsageDuration(AppUsageDurationQuery)` (+ new
  `QUERY_APP_USAGE` permission) offers pre-aggregated durations — track it, but it
  doesn't provide real-time transitions.

## Detecting a tracked app being KILLED (kill = session reset)

Usage events cannot distinguish "left to launcher, process alive" from "process killed".
For the pause/resume session lifecycle the kill decision uses
**`ActivityManager.getHistoricalProcessExitReasons(packageName, 0, 3)`**
(`ApplicationExitInfo`, API 30+): other packages' exit records are readable to holders
of the GET_USAGE_STATS appop — which DoomBuddy already requires. Any exit recorded
after the session's pause wall-time ⇒ the app died while away ⇒ the next open starts a
fresh session at 00:00 (old one banked at its pause point). On API 26–29 the resume
window alone governs. `REASON_USER_REQUESTED` (recents swipe), `REASON_USER_STOPPED`,
`REASON_LOW_MEMORY`, `REASON_SIGNALED` etc. all count — any exit is death.

## Foreground service

- Type **`specialUse`** (mandatory types since Android 14): `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` (normal permissions), and the manifest property
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with a justification Play reviews.
- **No timeout** applies to specialUse (the 6h/24h caps hit dataSync/mediaProcessing only).
- Boot restart works: `RECEIVE_BOOT_COMPLETED` → `startForegroundService` — Android 15
  blocks boot-start only for dataSync/camera/mediaPlayback/phoneCall/mediaProjection/
  microphone; specialUse is allowed. `START_STICKY` covers process death.
- **Android 15 SAW gotcha:** holding SYSTEM_ALERT_WINDOW exempts background FGS start
  only while a visible overlay window exists — start the service from UI or boot, not
  from a bare background receiver.
- FGS notification is mandatory: create the channel **before** `startForeground`
  (within ~5s of `startForegroundService`), use `ServiceCompat.startForeground` with
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (API 29+). Android 14 makes it user-dismissable —
  dismissal does not stop the service. IMPORTANCE_MIN keeps it out of the way;
  POST_NOTIFICATIONS is a runtime permission on 33+ (service runs even if denied).

## Overlay (TYPE_APPLICATION_OVERLAY)

- `SYSTEM_ALERT_WINDOW` is an appop, granted only via
  `Settings.canDrawOverlays` / `ACTION_MANAGE_OVERLAY_PERMISSION` — no runtime dialog.
- `FLAG_NOT_FOCUSABLE` **implies** `FLAG_NOT_TOUCH_MODAL` → touches outside our
  wrap-content window pass through automatically. Passive mode adds
  `FLAG_NOT_TOUCHABLE`; interactive mode removes only that flag.
- Android 12+ "untrusted touch events": touches passing *through* a visible overlay of
  ours onto other apps are blocked unless the overlay is transparent — our passive HUD
  is fully touch-transparent (flag) and the interactive bubble is small; users' taps on
  the buddy are ours, everything else passes through.
- Compose inside the overlay requires manual wiring:
  `ViewTreeLifecycleOwner` + `ViewTreeSavedStateRegistryOwner` (+ ViewModelStore owner)
  on the ComposeView, `ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool`,
  window context from `createWindowContext(display, type, null)` on API 30+, and all
  add/remove/update calls on the **main thread** (`LifecycleRegistry` asserts this).
  Catch `BadTokenException` for permission revocation races.
- addView/removeView cycles per intervention are cheap; the composition is disposed on
  detach (no leaked recomposers).

## Other

- **Exact alarms** are denied by default (targetSdk 33+) and `USE_EXACT_ALARM` is
  Play-restricted to clock/calendar apps — DoomBuddy uses none; a 1s in-service tick and
  2s poll are all the timing it needs.
- **Battery optimization exemption** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is
  policy-restricted but acceptable for screen-time apps where uninterrupted monitoring is
  the core feature — user-initiated, explained, degrade gracefully. *Not yet wired;
  recommended before Play release on aggressive OEMs.*
- **Play target SDK** (verified): new apps must target API 36+ (Aug 31, 2026 rule) —
  DoomBuddy targets 36, compiles against 37.
- Usage access on Play: no dedicated declaration form; covered by User Data policy —
  core-feature justification + prominent disclosure + Data safety form. Do **not** add
  `QUERY_ALL_PACKAGES` (the `<queries>` launcher intent covers app listing).
