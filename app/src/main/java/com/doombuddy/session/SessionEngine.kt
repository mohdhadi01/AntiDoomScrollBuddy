package com.doombuddy.session

import com.doombuddy.core.time.MonotonicClock

/**
 * Pure, deterministic state machine that turns raw foreground-app events into
 * product-level session/intervention outputs.
 *
 * Design rules (see docs/ARCHITECTURE.md):
 *  - Elapsed time is derived from the monotonic clock, never counted tick-by-tick.
 *  - The engine never touches Android APIs; the platform layer adapts inputs/outputs.
 *  - Anti-spam lives here: cooldowns, daily caps, quiet hours, dismissal grace.
 *
 * This class is not thread-safe by design; the owner (MonitorService) funnels all
 * inputs through a single coroutine.
 */
class SessionEngine(
    private val policy: () -> InterventionPolicy,
    private val trackedApps: () -> Set<String>,
    private val quietHours: () -> QuietHours,
    private val clock: MonotonicClock,
    /**
     * Platform hook: was the tracked app's PROCESS killed while the session was
     * paused (e.g. user swiped it from recents, OEM killer, crash)? If true, a
     * return to the app starts a FRESH session instead of resuming — kill = reset.
     * Implemented service-side via ApplicationExitInfo; defaults to false in tests.
     */
    private val appKilledWhileAway: (packageName: String, pausedAtMs: Long) -> Boolean = { _, _ -> false },
    /** Seed for the daily cap so restarts don't reset anti-annoyance limits. */
    private var interventionsTodaySeed: Int = 0,
) {
    private var currentSession: RunningSession? = null
    // Sentinels use MIN_VALUE/2 so arithmetic like `now - sentinel` cannot overflow.
    private var lastEndedPackage: String? = null
    private var lastEndedAtMs: Long = Long.MIN_VALUE / 2
    private var interventionsToday: Int = interventionsTodaySeed
    private var lastInterventionAtMs: Long = Long.MIN_VALUE / 2
    private var dismissedAtMs: Long = Long.MIN_VALUE / 2
    private var screenOn: Boolean = true

    private class RunningSession(
        val packageName: String,
        var startedAtMs: Long,
        val firedThresholdIndexes: MutableSet<Int> = mutableSetOf(),
        var recurringFired: Int = 0,
        var longSessionFired: Boolean = false,
        var interventionCount: Int = 0,
        /** Non-null while the tracked app is backgrounded: the session is frozen. */
        var pausedAtMs: Long? = null,
    )

    /** Number of interventions emitted so far today (for the daily cap persistence). */
    val interventionsTodayCount: Int get() = interventionsToday

    fun onInput(input: SessionInput): List<SessionOutput> {
        val outputs = mutableListOf<SessionOutput>()
        when (input) {
            is SessionInput.ForegroundChanged -> handleForegroundChanged(input, outputs)
            is SessionInput.ScreenTurnedOff -> handleScreenOff(outputs)
            is SessionInput.ScreenTurnedOn -> screenOn = true
            is SessionInput.BuddyDismissed -> dismissedAtMs = input.atMs
            is SessionInput.Tick -> handleTick(input, outputs)
        }
        return outputs
    }

    private fun handleForegroundChanged(input: SessionInput.ForegroundChanged, outputs: MutableList<SessionOutput>) {
        val now = input.atMs
        val tracked = trackedApps()
        val newTracked = input.packageName?.takeIf { it in tracked }
        val running = currentSession
        val p = policy()

        if (running != null && running.packageName == newTracked) {
            val pausedAt = running.pausedAtMs
            if (pausedAt != null) {
                val gap = now - pausedAt
                // The tracked app's process died while away (killed from recents, OEM
                // killer, crash) => the session MUST reset, even within the window.
                val killed = gap > 1_000 && appKilledWhileAway(running.packageName, pausedAt)
                if (!killed && gap in 0..p.resumeWindowMs) {
                    // RESUME: shift the start so elapsed continues exactly where it
                    // froze — no reset, no extra time counted while away.
                    running.startedAtMs += gap
                    running.pausedAtMs = null
                    val elapsed = now - running.startedAtMs
                    val rapid = gap <= p.rapidReopenGapMs
                    if (rapid) outputs += SessionOutput.RapidReopen(running.packageName, now, gap)
                    outputs += SessionOutput.SessionResumed(running.packageName, now, elapsed, rapid)
                } else {
                    // Killed, or away too long: end the old session at its PAUSE point
                    // (away time is never counted) and start fresh at 00:00.
                    endSession(running, pausedAt, outputs, EndReason.USER_LEFT)
                    startSession(newTracked!!, now, outputs)
                }
            }
            return
        }

        if (running != null) {
            if (newTracked != null) {
                // Tracked -> different tracked app: end (at pause point if paused) + start.
                endSession(running, running.pausedAtMs ?: now, outputs, EndReason.REPLACED_BY_OTHER_APP)
                startSession(newTracked, now, outputs)
            } else {
                // Tracked -> launcher/other/screen: PAUSE. Hide buddy, freeze timer.
                if (running.pausedAtMs == null) {
                    running.pausedAtMs = now
                    outputs += SessionOutput.SessionPaused(running.packageName, now, now - running.startedAtMs)
                }
            }
            return
        }

        if (newTracked != null) startSession(newTracked, now, outputs)
    }

    private fun startSession(packageName: String, atMs: Long, outputs: MutableList<SessionOutput>) {
        currentSession = RunningSession(packageName, atMs)
        outputs += SessionOutput.SessionStarted(packageName, atMs, isRapidReopen = false)
    }

    /** A non-null raw foreground package means the user opened *some* app — just not a tracked one. */
    private fun reasonFor(rawForegroundPackage: String?): EndReason =
        if (rawForegroundPackage == null) EndReason.USER_LEFT else EndReason.REPLACED_BY_OTHER_APP

    private fun handleScreenOff(outputs: MutableList<SessionOutput>) {
        screenOn = false
        val running = currentSession ?: return
        if (running.pausedAtMs == null) {
            val now = clock.nowMs()
            running.pausedAtMs = now
            outputs += SessionOutput.SessionPaused(running.packageName, now, now - running.startedAtMs)
        }
    }

    private fun handleTick(input: SessionInput.Tick, outputs: MutableList<SessionOutput>) {
        val running = currentSession ?: return
        val pausedAt = running.pausedAtMs
        if (pausedAt != null) {
            // Paused: no threshold evaluation, no time counted. If the user stays away
            // past the resume window the session closes at its pause point (checked
            // regardless of screen state — wall-clock expiry).
            if (input.nowMs - pausedAt > policy().resumeWindowMs) {
                endSession(running, pausedAt, outputs, EndReason.USER_LEFT)
            }
            return
        }
        if (!screenOn) return
        if (quietHours().contains(input.minuteOfDay)) return

        val p = policy()
        val elapsed = input.nowMs - running.startedAtMs
        if (elapsed < 0) return

        // Long-session flag fires once, independent of intervention caps (it changes the
        // character's mood rather than interrupting).
        if (!running.longSessionFired && elapsed >= p.longSessionMs) {
            running.longSessionFired = true
            outputs += SessionOutput.LongSession(input.nowMs, elapsed, running.packageName)
        }

        if (interventionsToday >= p.maxDailyInterruptions) return
        if (input.nowMs - lastInterventionAtMs < p.repeatCooldownMs) return
        if (input.nowMs - dismissedAtMs < p.dismissalGraceMs) return

        // Base ladder first: fire the deepest reached-but-unfired rung (one per tick —
        // no burst when a poll is delayed).
        val deepestBase = p.thresholdsMs.indices
            .filter { elapsed >= p.thresholdsMs[it] && it !in running.firedThresholdIndexes }
            .maxOrNull()
        if (deepestBase != null) {
            running.firedThresholdIndexes += deepestBase
            // Mark all shallower thresholds as consumed so a later tick can't fire them.
            for (i in 0 until deepestBase) running.firedThresholdIndexes += i
            fireIntervention(level = deepestBase + 1, elapsed = elapsed, running = running, atMs = input.nowMs, outputs = outputs)
            return
        }

        // Ladder exhausted → recurring re-interventions past the last rung. Dismissing
        // must never buy silence for the rest of the session (spec §31).
        if (elapsed <= p.thresholdsMs.last()) return
        val recurringDue = ((elapsed - p.thresholdsMs.last()) / p.repeatIntervalMs).toInt()
        if (recurringDue <= running.recurringFired) return
        running.recurringFired++
        fireIntervention(level = p.maxEscalationLevel, elapsed = elapsed, running = running, atMs = input.nowMs, outputs = outputs)
    }

    private fun fireIntervention(
        level: Int,
        elapsed: Long,
        running: RunningSession,
        atMs: Long,
        outputs: MutableList<SessionOutput>,
    ) {
        val p = policy()
        running.interventionCount++
        interventionsToday++
        lastInterventionAtMs = atMs
        outputs += SessionOutput.ThresholdReached(
            level = level,
            atMs = atMs,
            sessionElapsedMs = elapsed,
            packageName = running.packageName,
        )
    }

    private fun endSession(
        running: RunningSession,
        atMs: Long,
        outputs: MutableList<SessionOutput>,
        reason: EndReason,
    ) {
        currentSession = null
        lastEndedPackage = running.packageName
        lastEndedAtMs = atMs
        val duration = (atMs - running.startedAtMs).coerceAtLeast(0)
        outputs += SessionOutput.SessionEnded(
            packageName = running.packageName,
            atMs = atMs,
            durationMs = duration,
            interventionCount = running.interventionCount,
            endedBy = reason,
        )
    }
}
