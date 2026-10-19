# Performance

The product lives **on top of other apps** — budgets are first-class.

## Idle (buddy hidden, no session)

- Poll cadence 2s (a native queryEvents call; no wakeups when the screen is off — the
  poll short-circuits on `isInteractive`).
- Zero Compose work: overlay view detached, director's frame loop cancelled, no
  Animatables running. CPU ≈ poll cost only.

## Visible session

- Overlay renders only on invalidation; idle bob/blink is a `withFrameNanos` loop that
  exists **only while the buddy is visible**.
- Draw path is allocation-free steady state (cached paths/brushes via `drawWithCache`;
  pose is one small immutable object per frame).
- Timer text updates 1×/s (string compare gates redundant updates); thresholds are
  evaluated from the monotonic clock, never by counting ticks.

## Budgets (enforced by design, verify with profilers per release)

| Metric | Budget |
|---|---|
| Idle CPU | < 0.5% average (poll-only) |
| Visible CPU | no sustained core saturation; no main-thread jank |
| Memory | overlay composition < ~10MB; released on every hide |
| APK delta | 0 third-party animation/runtime deps |
| Battery | no wakeups while screen off; no network, ever |

## Main-thread rules

- Room writes on `Dispatchers.IO`; DataStore reads are Flow-based off-main.
- `handleOutputs` hops to Main but does no disk/network work.
- Poller runs on `Dispatchers.Default`; engine inputs serialized through a Channel.
