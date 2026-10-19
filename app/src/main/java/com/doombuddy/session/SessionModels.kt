package com.doombuddy.session

/** Events fed into the [SessionEngine] by the platform layer. */
sealed interface SessionInput {
    /**
     * The foreground app changed. `packageName == null` means the launcher / no app
     * (or an app we can attribute). Delivered for every change, tracked or not; the
     * engine filters against the tracked-app set.
     */
    data class ForegroundChanged(val packageName: String?, val atMs: Long) : SessionInput

    object ScreenTurnedOff : SessionInput
    object ScreenTurnedOn : SessionInput

    /** The user dismissed / tapped the buddy. Grants extra quiet time per policy. */
    data class BuddyDismissed(val atMs: Long) : SessionInput

    /** Periodic re-evaluation; drives threshold checks. Cheap and idempotent. */
    data class Tick(val nowMs: Long, val minuteOfDay: Int) : SessionInput
}

/** Events produced by the [SessionEngine]; consumed by the overlay orchestrator and persistence. */
sealed interface SessionOutput {
    data class SessionStarted(
        val packageName: String,
        val atMs: Long,
        val isRapidReopen: Boolean,
    ) : SessionOutput

    data class SessionEnded(
        val packageName: String,
        val atMs: Long,
        val durationMs: Long,
        val interventionCount: Int,
        val endedBy: EndReason,
    ) : SessionOutput

    /**
     * The tracked app went background: the session FREEZES (buddy hides, timer stops,
     * nothing extra counted). No record is written yet — the session may resume.
     */
    data class SessionPaused(
        val packageName: String,
        val atMs: Long,
        val elapsedMs: Long,
    ) : SessionOutput

    /**
     * The user returned to the tracked app within the resume window: the session
     * continues from the exact paused elapsed — no reset, no extra time.
     */
    data class SessionResumed(
        val packageName: String,
        val atMs: Long,
        val elapsedMs: Long,
        val isRapid: Boolean,
    ) : SessionOutput

    /** A threshold fired. `level` is 1-based; higher = more escalated. */
    data class ThresholdReached(
        val level: Int,
        val atMs: Long,
        val sessionElapsedMs: Long,
        val packageName: String,
    ) : SessionOutput

    /** Session duration crossed the policy's long-session mark (fires once per session). */
    data class LongSession(val atMs: Long, val sessionElapsedMs: Long, val packageName: String) : SessionOutput

    /** The user returned to a tracked app within [InterventionPolicy.rapidReopenGapMs] of leaving. */
    data class RapidReopen(val packageName: String, val atMs: Long, val gapMs: Long) : SessionOutput
}

enum class EndReason { USER_LEFT, SCREEN_OFF, REPLACED_BY_OTHER_APP }
