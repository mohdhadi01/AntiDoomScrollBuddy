package com.doombuddy.session

/**
 * The configurable brain of the intervention system. Nothing in the codebase is allowed to
 * hard-code `if (elapsed > 300_000)` style checks — everything reads from a policy.
 */
data class InterventionPolicy(
    /**
     * Elapsed-session times (ms, ascending) at which an intervention fires.
     * Index 0 is the first subtle appearance; later entries escalate.
     * After the ladder is exhausted, re-interventions recur at [repeatIntervalMs]
     * past the last threshold for as long as the session continues (capped by the
     * daily limit and cooldowns) — dismissing must never buy silence forever.
     */
    val thresholdsMs: List<Long>,
    /** Minimum gap between two interventions within the same session. */
    val repeatCooldownMs: Long,
    /** Spacing of recurring re-interventions after the ladder is exhausted. */
    val repeatIntervalMs: Long,
    /** Hard cap of interventions per day across all sessions (anti-annoyance). */
    val maxDailyInterruptions: Int,
    /** Reopening a tracked app within this window after leaving counts as a rapid reopen. */
    val rapidReopenGapMs: Long,
    /** A single continuous session longer than this is flagged as a long session. */
    val longSessionMs: Long,
    /** Extra quiet time granted after the user dismisses the buddy. */
    val dismissalGraceMs: Long,
    /**
     * Leaving the tracked app PAUSES the session (timer freezes, buddy hides).
     * Returning within this window RESUMES from the exact paused elapsed; staying
     * away longer ends the session at the pause point (spec: only a genuinely
     * gone/killed app stops and resets).
     *
     * Android gives third-party apps no reliable way to detect that another app's
     * process was actually killed while backgrounded (see
     * `MonitorService.appDiedWhileAway`), so this window is deliberately kept short
     * — long enough to survive a quick notification check or app-switcher flick, but
     * short enough that an actual force-stop/swipe-away + later relaunch (which in
     * practice always takes longer than this) reliably starts a fresh session instead
     * of resuming the old one. Must stay ≥ [rapidReopenGapMs] so rapid-reopen reactions
     * keep firing.
     */
    val resumeWindowMs: Long,
) {
    init {
        require(thresholdsMs.isNotEmpty()) { "thresholdsMs must not be empty" }
        require(thresholdsMs.zipWithNext().all { (a, b) -> a < b }) { "thresholdsMs must be ascending" }
    }

    val maxEscalationLevel: Int get() = thresholdsMs.size

    /** Full ladder including recurring re-interventions after the base rungs. */
    fun recurringThresholds(extraCount: Int): List<Long> =
        List(extraCount) { i -> thresholdsMs.last() + (i + 1) * repeatIntervalMs }

    companion object {
        val CHILL = InterventionPolicy(
            thresholdsMs = listOf(8, 16, 28, 45).map { it * 60_000L },
            repeatCooldownMs = 8 * 60_000L,
            repeatIntervalMs = 12 * 60_000L,
            maxDailyInterruptions = 12,
            rapidReopenGapMs = 60_000L,
            longSessionMs = 45 * 60_000L,
            dismissalGraceMs = 3 * 60_000L,
            resumeWindowMs = 75_000L,
        )

        val PLAYFUL = InterventionPolicy(
            thresholdsMs = listOf(5, 10, 15, 22, 31).map { it * 60_000L },
            repeatCooldownMs = 5 * 60_000L,
            repeatIntervalMs = 7 * 60_000L,
            maxDailyInterruptions = 20,
            rapidReopenGapMs = 60_000L,
            longSessionMs = 30 * 60_000L,
            dismissalGraceMs = 2 * 60_000L,
            resumeWindowMs = 75_000L,
        )

        val PERSISTENT = InterventionPolicy(
            thresholdsMs = listOf(3, 7, 11, 16, 22, 30).map { it * 60_000L },
            repeatCooldownMs = 3 * 60_000L,
            repeatIntervalMs = 5 * 60_000L,
            maxDailyInterruptions = 30,
            rapidReopenGapMs = 90_000L,
            longSessionMs = 25 * 60_000L,
            dismissalGraceMs = 90_000L,
            resumeWindowMs = 110_000L,
        )

        val CHAOS = InterventionPolicy(
            thresholdsMs = listOf(90, 180, 300, 480, 720, 1_020, 1_380).map { it * 1_000L },
            repeatCooldownMs = 90_000L,
            repeatIntervalMs = 150_000L,
            maxDailyInterruptions = 60,
            rapidReopenGapMs = 120_000L,
            longSessionMs = 20 * 60_000L,
            dismissalGraceMs = 45_000L,
            resumeWindowMs = 140_000L,
        )

        fun forIntensity(intensity: Intensity): InterventionPolicy = when (intensity) {
            Intensity.CHILL -> CHILL
            Intensity.PLAYFUL -> PLAYFUL
            Intensity.PERSISTENT -> PERSISTENT
            Intensity.CHAOS -> CHAOS
        }
    }
}

enum class Intensity {
    CHILL, PLAYFUL, PERSISTENT, CHAOS;

    companion object {
        fun fromIndex(index: Int): Intensity = entries.getOrElse(index) { PLAYFUL }
    }
}

/**
 * User-defined window during which the buddy does not interrupt (sessions are still tracked).
 * Minutes-of-day, inclusive start, exclusive end. `end <= start` means the window wraps midnight.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinuteOfDay: Int = 23 * 60,
    val endMinuteOfDay: Int = 7 * 60,
) {
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        return if (startMinuteOfDay < endMinuteOfDay) {
            minuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
    }
}
