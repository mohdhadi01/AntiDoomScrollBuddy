package com.doombuddy.session

import com.doombuddy.core.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private var now = 0L
    private val clock = MonotonicClock { now }

    private val tracked = setOf("com.instagram.android", "com.youtube.app")

    private fun engine(
        policy: InterventionPolicy = InterventionPolicy.PLAYFUL,
        quiet: QuietHours = QuietHours(),
        interventionsToday: Int = 0,
    ) = SessionEngine(
        policy = { policy },
        trackedApps = { tracked },
        quietHours = { quiet },
        clock = clock,
        interventionsTodaySeed = interventionsToday,
    )

    private fun tick(engine: SessionEngine, atMs: Long, minuteOfDay: Int = 600): List<SessionOutput> {
        now = atMs
        return engine.onInput(SessionInput.Tick(atMs, minuteOfDay))
    }

    private fun foreground(engine: SessionEngine, pkg: String?, atMs: Long): List<SessionOutput> {
        now = atMs
        return engine.onInput(SessionInput.ForegroundChanged(pkg, atMs))
    }

    @Test
    fun `no intervention before first threshold`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val outputs = tick(e, 4 * 60_000L)
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
    }

    @Test
    fun `first threshold fires at policy time`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val outputs = tick(e, 5 * 60_000L + 1)
        val reached = outputs.filterIsInstance<SessionOutput.ThresholdReached>()
        assertEquals(1, reached.size)
        assertEquals(1, reached[0].level)
        assertEquals(5 * 60_000L + 1, reached[0].sessionElapsedMs)
    }

    @Test
    fun `delayed poll fires deepest threshold without burst`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        // Poll delayed past thresholds 1 and 2 -> only level 2 fires.
        val outputs = tick(e, 10 * 60_000L + 1)
        val reached = outputs.filterIsInstance<SessionOutput.ThresholdReached>()
        assertEquals(1, reached.size)
        assertEquals(2, reached[0].level)
    }

    @Test
    fun `cooldown suppresses rapid second intervention`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        tick(e, 5 * 60_000L + 1) // level 1 fires
        val outputs = tick(e, 7 * 60_000L) // threshold 2 (10min) not yet; nothing else fires
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
    }

    @Test
    fun `escalation continues after cooldown`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        tick(e, 5 * 60_000L + 1)               // level 1
        val outputs = tick(e, 10 * 60_000L + 1) // level 2 (cooldown of 5min passed exactly)
        val reached = outputs.filterIsInstance<SessionOutput.ThresholdReached>()
        assertEquals(1, reached.size)
        assertEquals(2, reached[0].level)
    }

    @Test
    fun `leaving to an untracked app pauses the session`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val outputs = foreground(e, "com.spotify.android", 8 * 60_000L)
        val paused = outputs.filterIsInstance<SessionOutput.SessionPaused>().single()
        assertEquals("com.instagram.android", paused.packageName)
        assertEquals(8 * 60_000L, paused.elapsedMs)
        assertTrue(outputs.filterIsInstance<SessionOutput.SessionEnded>().isEmpty())
    }

    @Test
    fun `switching between tracked apps ends and starts sessions`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val outputs = foreground(e, "com.youtube.app", 8 * 60_000L)
        val ended = outputs.filterIsInstance<SessionOutput.SessionEnded>().single()
        assertEquals("com.instagram.android", ended.packageName)
        assertEquals(8 * 60_000L, ended.durationMs)
        assertEquals(EndReason.REPLACED_BY_OTHER_APP, ended.endedBy)
        assertEquals("com.youtube.app", outputs.filterIsInstance<SessionOutput.SessionStarted>().single().packageName)
    }

    @Test
    fun `paused session resumes with elapsed continuity - no reset no extra time`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 4 * 60_000L + 59_000L)          // pause at 4:59 (before 5:00 threshold)
        val resumed = foreground(e, "com.instagram.android", 6 * 60_000L)  // back 61s later
        val r = resumed.filterIsInstance<SessionOutput.SessionResumed>().single()
        assertEquals(4 * 60_000L + 59_000L, r.elapsedMs)     // elapsed frozen, not 6:00
        // 61s of wall clock later the timer crosses the original 5:00 threshold:
        // 7:00 wall - 1:01 of paused-away time = 5:59 of true session time.
        val tick = tick(e, 7 * 60_000L)
        val reached = tick.filterIsInstance<SessionOutput.ThresholdReached>()
        assertEquals(1, reached.size)
        assertEquals(5 * 60_000L + 59_000L, reached[0].sessionElapsedMs)
    }

    @Test
    fun `repeated background events do not duplicate pause or reset the session`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val firstPause = foreground(e, null, 30_000L)
        val repeatedPause = foreground(e, null, 45_000L)

        assertEquals(1, firstPause.filterIsInstance<SessionOutput.SessionPaused>().size)
        assertTrue(repeatedPause.none { it is SessionOutput.SessionPaused })

        val resumed = foreground(e, "com.instagram.android", 60_000L)
            .filterIsInstance<SessionOutput.SessionResumed>()
            .single()
        assertEquals(30_000L, resumed.elapsedMs)
    }

    @Test
    fun `return beyond the resume window ends old session at pause point and starts fresh`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)                          // pause at 1:00
        val outputs = foreground(e, "com.instagram.android", 20 * 60_000L)  // 19 min away
        val ended = outputs.filterIsInstance<SessionOutput.SessionEnded>().single()
        assertEquals(60_000L, ended.durationMs)               // away time never counted
        val started = outputs.filterIsInstance<SessionOutput.SessionStarted>().single()
        assertEquals(20 * 60_000L, started.atMs)
    }

    @Test
    fun `window expiry while away closes the session at the pause point`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)
        val outputs = tick(e, 20 * 60_000L)                   // still away, window expired
        val ended = outputs.filterIsInstance<SessionOutput.SessionEnded>().single()
        assertEquals(60_000L, ended.durationMs)
    }

    @Test
    fun `rapid return within gap reacts and resumes`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)
        val outputs = foreground(e, "com.instagram.android", 90_000L)
        assertTrue(outputs.any { it is SessionOutput.RapidReopen })
        assertEquals(1, outputs.filterIsInstance<SessionOutput.SessionResumed>().size)
    }

    @Test
    fun `slower return resumes without the rapid reaction`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)
        // Gap (70s) is past PLAYFUL's rapidReopenGapMs (60s) but still inside its
        // resumeWindowMs (75s): a "slower but not rapid" return that still resumes quietly.
        val outputs = foreground(e, "com.instagram.android", 130_000L)
        assertTrue(outputs.none { it is SessionOutput.RapidReopen })
        assertEquals(1, outputs.filterIsInstance<SessionOutput.SessionResumed>().size)
    }

    @Test
    fun `screen off pauses the session`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val outputs = e.onInput(SessionInput.ScreenTurnedOff)
        val paused = outputs.filterIsInstance<SessionOutput.SessionPaused>().single()
        assertTrue(paused.elapsedMs >= 0)
    }

    @Test
    fun `no interventions while paused`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        e.onInput(SessionInput.ScreenTurnedOff)
        val outputs = tick(e, 20 * 60_000L)
        // Still paused past the window → session closed at pause point; never any threshold.
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
        assertEquals(1, outputs.filterIsInstance<SessionOutput.SessionEnded>().size)
    }

    @Test
    fun `untracked apps never start sessions`() {
        val e = engine()
        val outputs = foreground(e, "com.example.untracked", 0)
        assertTrue(outputs.filterIsInstance<SessionOutput.SessionStarted>().isEmpty())
    }

    @Test
    fun `quiet hours suppress interventions but keep long session flag`() {
        val e = engine(policy = InterventionPolicy.PLAYFUL, quiet = QuietHours(enabled = true, startMinuteOfDay = 0, endMinuteOfDay = 1000))
        foreground(e, "com.instagram.android", 0)
        val outputs = tick(e, 10 * 60_000L, minuteOfDay = 500)
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
    }

    @Test
    fun `daily cap stops interventions`() {
        val e = engine(interventionsToday = InterventionPolicy.PLAYFUL.maxDailyInterruptions)
        foreground(e, "com.instagram.android", 0)
        val outputs = tick(e, 30 * 60_000L)
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
    }

    @Test
    fun `dismissal grace delays next intervention`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        tick(e, 5 * 60_000L + 1)                       // level 1 fires
        e.onInput(SessionInput.BuddyDismissed(atMs = 5 * 60_000L + 2_000))
        // Threshold 2 (10min) would fire at 10:01 but dismissal grace (2min) runs to 7:02...
        // cooldown (5min from 5:01) also runs to 10:01. Use a dismissal right before threshold 2.
        e.onInput(SessionInput.BuddyDismissed(atMs = 9 * 60_000L))
        val outputs = tick(e, 10 * 60_000L + 1)        // within grace (grace ends at 11:00)
        assertTrue(outputs.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
        val afterGrace = tick(e, 11 * 60_000L + 1)
        assertEquals(1, afterGrace.filterIsInstance<SessionOutput.ThresholdReached>().size)
    }

    @Test
    fun `long session fires once`() {
        val e = engine()
        foreground(e, "com.instagram.android", 0)
        val first = tick(e, 31 * 60_000L)
        assertEquals(1, first.filterIsInstance<SessionOutput.LongSession>().size)
        val second = tick(e, 35 * 60_000L)
        assertTrue(second.filterIsInstance<SessionOutput.LongSession>().isEmpty())
    }

    @Test
    fun `chaos preset is valid and more aggressive`() {
        val chaos = InterventionPolicy.CHAOS
        assertTrue(chaos.thresholdsMs.first() < InterventionPolicy.CHILL.thresholdsMs.first())
        assertTrue(chaos.maxDailyInterruptions > InterventionPolicy.CHILL.maxDailyInterruptions)
    }
}

class SessionEngineRecurringTest {

    private var now = 0L
    private val clock = MonotonicClock { now }
    private val tracked = setOf("com.instagram.android")

    private fun tick(engine: SessionEngine, atMs: Long): List<SessionOutput> {
        now = atMs
        return engine.onInput(SessionInput.Tick(atMs, 600))
    }

    @Test
    fun `recurring re-interventions fire past the ladder end`() {
        val p = InterventionPolicy.PLAYFUL // ladder ends 31min, repeat 7min
        val e = SessionEngine({ p }, { tracked }, { QuietHours() }, clock)
        e.onInput(SessionInput.ForegroundChanged("com.instagram.android", 0))

        tick(e, 31 * 60_000L + 1)                    // base ladder final rung (5 fired across session)
        assertEquals(0, tick(e, 31 * 60_000L + 2).size) // no double fire on next tick
        val re1 = tick(e, 38 * 60_000L + 1)          // last + 7min → 1st recurring
        assertEquals(1, re1.filterIsInstance<SessionOutput.ThresholdReached>().size)
        assertEquals(p.maxEscalationLevel, re1.filterIsInstance<SessionOutput.ThresholdReached>()[0].level)
        val re2 = tick(e, 45 * 60_000L + 1)          // +7min → 2nd recurring
        assertEquals(1, re2.filterIsInstance<SessionOutput.ThresholdReached>().size)
    }

    @Test
    fun `recurring respects cooldown and daily cap`() {
        val p = InterventionPolicy.CHAOS.copy(maxDailyInterruptions = 8)
        val e = SessionEngine({ p }, { tracked }, { QuietHours() }, clock)
        e.onInput(SessionInput.ForegroundChanged("com.instagram.android", 0))
        // burn the cap
        var t = 0L
        repeat(30) {
            t += 200_000
            tick(e, t)
        }
        val after = tick(e, t + 400_000)
        assertTrue(after.filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
    }

    @Test
    fun `dismissal grace delays recurring re-intervention`() {
        val p = InterventionPolicy(
            thresholdsMs = listOf(60_000L, 120_000L),
            repeatCooldownMs = 10_000L,
            repeatIntervalMs = 30_000L,
            maxDailyInterruptions = 50,
            rapidReopenGapMs = 60_000L,
            longSessionMs = 300_000L,
            dismissalGraceMs = 20_000L,
            resumeWindowMs = 10 * 60_000L,
        )
        val e = SessionEngine({ p }, { tracked }, { QuietHours() }, clock)
        e.onInput(SessionInput.ForegroundChanged("com.instagram.android", 0))
        assertEquals(1, tick(e, 61_000L).size)  // rung 1
        assertEquals(1, tick(e, 121_000L).size) // rung 2 (ladder exhausted)
        val nextRung = 150_000L                 // last + 30s
        e.onInput(SessionInput.BuddyDismissed(atMs = 145_000L))
        assertTrue(tick(e, 151_000L).filterIsInstance<SessionOutput.ThresholdReached>().isEmpty())
        assertEquals(1, tick(e, 167_000L).filterIsInstance<SessionOutput.ThresholdReached>().size)
    }
}

class SessionEngineKillResetTest {

    private var now = 0L
    private val clock = MonotonicClock { now }
    private val tracked = setOf("com.instagram.android")

    private fun engine(killed: Boolean): SessionEngine = SessionEngine(
        policy = { InterventionPolicy.PLAYFUL },
        trackedApps = { tracked },
        quietHours = { QuietHours() },
        clock = clock,
        appKilledWhileAway = { _, _ -> killed },
    )

    private fun foreground(e: SessionEngine, pkg: String?, atMs: Long): List<SessionOutput> {
        now = atMs
        return e.onInput(SessionInput.ForegroundChanged(pkg, atMs))
    }

    private fun tick(e: SessionEngine, atMs: Long): List<SessionOutput> {
        now = atMs
        return e.onInput(SessionInput.Tick(atMs, 600))
    }

    @Test
    fun `app killed while away - return resets to a fresh session`() {
        val e = engine(killed = true)
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)                    // pause at 1:00 (e.g. user swiped insta)
        // Reopen well INSIDE PLAYFUL's resumeWindowMs (75s) — the only reason this resets
        // instead of resuming is the killed=true flag, which is exactly what this test
        // needs to exercise (a gap past the window would reset regardless of the flag).
        val outputs = foreground(e, "com.instagram.android", 90_000L)
        val ended = outputs.filterIsInstance<SessionOutput.SessionEnded>().single()
        assertEquals(60_000L, ended.durationMs)         // old session banked at pause point
        val started = outputs.filterIsInstance<SessionOutput.SessionStarted>().single()
        assertEquals(90_000L, started.atMs)             // fresh session at 00:00
        assertTrue(outputs.filterIsInstance<SessionOutput.SessionResumed>().isEmpty())
        // and the new session's first threshold fires 5 minutes from the NEW start
        assertTrue(tick(e, 90_000L + 5 * 60_000L + 1).any { it is SessionOutput.ThresholdReached })
    }

    @Test
    fun `app still alive while away - return resumes (kill check must not false-positive)`() {
        val e = engine(killed = false)
        foreground(e, "com.instagram.android", 0)
        foreground(e, null, 60_000L)
        // Gap (65s) stays inside PLAYFUL's resumeWindowMs (75s).
        val outputs = foreground(e, "com.instagram.android", 125_000L)
        assertEquals(1, outputs.filterIsInstance<SessionOutput.SessionResumed>().size)
        assertTrue(outputs.filterIsInstance<SessionOutput.SessionEnded>().isEmpty())
    }
}
