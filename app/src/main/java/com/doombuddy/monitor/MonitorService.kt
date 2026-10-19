package com.doombuddy.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.doombuddy.MainActivity
import com.doombuddy.R
import com.doombuddy.character.CharacterDirector
import com.doombuddy.character.DialogueEngine
import com.doombuddy.character.DialogueEvent
import com.doombuddy.core.time.SystemMonotonicClock
import com.doombuddy.core.time.SystemWallClock
import com.doombuddy.data.DoomBuddyDatabase
import com.doombuddy.data.InterventionEntity
import com.doombuddy.data.PrefsRepository
import com.doombuddy.data.SessionEntity
import com.doombuddy.data.SessionSplitter
import com.doombuddy.data.TodayMilestones
import com.doombuddy.data.TimerMode
import com.doombuddy.overlay.OverlayController
import com.doombuddy.overlay.OverlayUiState
import com.doombuddy.session.EndReason
import com.doombuddy.session.InterventionPolicy
import com.doombuddy.session.SessionEngine
import com.doombuddy.session.SessionInput
import com.doombuddy.session.SessionOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalTime

/**
 * specialUse foreground service: owns the foreground poller, the [SessionEngine], the
 * overlay controller and session persistence.
 *
 * Concurrency contract (docs/ARCHITECTURE.md):
 *  - every [SessionInput] funnels through [engineInput]; ONE consumer touches the engine;
 *  - every overlay/director call runs on [mainScope] (Main.immediate).
 *
 * Business vs animation state (spec §14): [interventionState] is the business machine —
 * HIDDEN → ENTERING → PERSISTENT → DISMISSING → HIDDEN. The intervention NEVER
 * auto-dismisses: the only exits
 * are a completed hold (user) or the tracked app losing foreground (platform).
 */
class MonitorService : Service() {

    /** Business state of the intervention — deliberately separate from animation. */
    enum class InterventionState { HIDDEN, ENTERING, PERSISTENT, DISMISSING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineInput = Channel<SessionInput>(Channel.UNLIMITED)

    private lateinit var prefs: PrefsRepository
    private lateinit var overlay: OverlayController
    private lateinit var director: CharacterDirector
    private lateinit var dialogue: DialogueEngine
    private lateinit var engine: SessionEngine
    private lateinit var poller: ForegroundAppPoller

    private var screenReceiver: ScreenStateReceiver? = null
    private var sessionStartedAtMs: Long = 0
    private var interventionState = InterventionState.HIDDEN
    private var currentPackageName: String? = null
    private var currentLevel = 0
    @Volatile private var openInterventionRowId: Long? = null
    @Volatile private var persistedTodayMs: Long = 0
    @Volatile private var persistedTodayStartEpochMs: Long = Long.MIN_VALUE
    private var lastMilestoneMs: Long = 0
    private var lastInterventionAtMs: Long = 0
    @Volatile private var sessionStartWallMs: Long = 0

    // Preference snapshots (hot loops never read DataStore per event).
    @Volatile private var currentPolicy: InterventionPolicy = InterventionPolicy.PLAYFUL
    @Volatile private var currentTrackedApps: Set<String> = emptySet()
    @Volatile private var currentQuietHours: com.doombuddy.session.QuietHours = com.doombuddy.session.QuietHours()
    @Volatile private var currentCharacterId: String = "melt"
    @Volatile private var currentAccessory: com.doombuddy.character.Accessory = com.doombuddy.character.Accessory.NONE
    @Volatile private var overlayXFrac: Float = 0.84f
    @Volatile private var overlayYFrac: Float = 0.80f
    @Volatile private var overlayScale: Float = 1f
    @Volatile private var timerMode: TimerMode = TimerMode.CYCLE
    @Volatile private var timerCycleSeconds: Int = 3
    @Volatile private var holdDurationMs: Int = 1200

    /** Developer-readable snapshot of the whole pipeline (spec §70). */
    data class DebugState(
        val foreground: String? = null,
        val interventionState: InterventionState = InterventionState.HIDDEN,
        val sessionPackage: String? = null,
        val sessionElapsedMs: Long = 0,
        val sessionStartWallMs: Long = 0,
        val sessionPaused: Boolean = false,
        val persistedTodayMs: Long = 0,
        val liveTodayMs: Long = 0,
        val lastLevel: Int = 0,
        val overlayAttached: Boolean = false,
        val usageAccessGranted: Boolean = false,
        val overlayPermissionGranted: Boolean = false,
        val characterId: String = "",
        val thresholdsMs: List<Long> = emptyList(),
    )

    val debugState = MutableStateFlow(DebugState())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        prefs = PrefsRepository(this)
        overlay = OverlayController(this)
        // AndroidUiDispatcher carries the MonotonicFrameClock Compose animations require.
        director = CharacterDirector(
            CoroutineScope(SupervisorJob() + androidx.compose.ui.platform.AndroidUiDispatcher.CurrentThread),
        )
        overlay.director = director
        dialogue = DialogueEngine()
        poller = ForegroundAppPoller(this, SystemWallClock)
        engine = SessionEngine(
            policy = { currentPolicy },
            trackedApps = { currentTrackedApps },
            quietHours = { currentQuietHours },
            clock = SystemMonotonicClock,
            appKilledWhileAway = { pkg, _ -> appDiedWhileAway(pkg) },
        )

        // Tap pokes the buddy — never dismisses (spec §33).
        overlay.onPoke = { overlay.hapticTick() }
        overlay.onHoldStart = { overlay.hapticTick() }
        overlay.onHoldRelease = { /* buddy relieved; nothing business-side */ }
        overlay.onHoldComplete = { onHoldDismissed() }

        registerScreenReceiver()
        startInForeground()
        WatchdogReceiver.scheduleWatchdog(this)

        scope.launch { observePrefs() }
        scope.launch { runPollLoop() }
        scope.launch { runTickLoop() }
        scope.launch { runEngineQueue() }
        instance = this
    }

    /**
     * Kill detection for the resume decision (user rule: killed app = session reset).
     *
     * NOT ACHIEVABLE on stock Android for a third-party app: `getHistoricalProcessExitReasons`
     * requires `android.permission.DUMP` (a signature permission no non-system app can hold)
     * for ANY package other than the caller's own — see
     * https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int).
     * The previous implementation called this for the tracked app anyway; it always threw
     * `SecurityException`, which was silently swallowed, so this check ALWAYS returned false
     * in production — "killed" was never detected and every reopen within [resumeWindowMs]
     * resumed the old session regardless of whether the tracked app's process had actually
     * died. There is no public, non-privileged API that exposes another app's process
     * lifecycle, so we cannot reliably detect a real kill. Instead, [resumeWindowMs] itself
     * is kept short (see InterventionPolicy) so that only genuinely brief interruptions
     * (switching to read a notification, etc.) resume — anything longer, which is what an
     * actual force-stop + relaunch looks like in practice, ends the session and starts fresh.
     */
    private fun appDiedWhileAway(packageName: String): Boolean = false

    private suspend fun observePrefs() {
        prefs.preferences.collect { p ->
            currentPolicy = InterventionPolicy.forIntensity(p.intensity)
                .copy(
                    thresholdsMs = scaledThresholds(p.firstThresholdMinutes, p.intensity),
                    repeatIntervalMs = scaledRepeatInterval(p.firstThresholdMinutes, p.intensity),
                )
            currentTrackedApps = p.targetApps
            currentQuietHours = p.quietHours
            currentCharacterId = p.characterId
            currentAccessory = com.doombuddy.character.CharacterRoster.byId(p.characterId).accessory
            // Character switching is REAL: swap the buddy live, even mid-intervention.
            if (overlay.isAttached && interventionState != InterventionState.HIDDEN) {
                val newChar = com.doombuddy.character.CharacterRoster.byId(p.characterId)
                mainScope.launch {
                    overlay.update(overlay.state.copy(character = newChar, accessory = currentAccessory))
                    director.enter(com.doombuddy.character.Entrance.POP, accessory = currentAccessory)
                }
            }
            val newX = p.overlayXFrac
            val newY = p.overlayYFrac
            val positionChanged = newX != overlayXFrac || newY != overlayYFrac
            overlayXFrac = newX
            overlayYFrac = newY
            overlayScale = p.overlayScalePct / 100f
            // Live reposition/scale while the buddy is on screen.
            if (positionChanged && overlay.isAttached) {
                mainScope.launch { overlay.updatePosition(newX, newY) }
            }
            timerMode = p.timerMode
            timerCycleSeconds = p.timerCycleSeconds
            holdDurationMs = p.holdDurationMs
        }
    }

    /** User picks the FIRST threshold; ladder + repeat interval scale from it. */
    private fun scaledThresholds(firstMinutes: Int, intensity: com.doombuddy.session.Intensity): List<Long> {
        val base = InterventionPolicy.forIntensity(intensity)
        val scale = firstMinutes.coerceIn(1, 30) * 60_000L / base.thresholdsMs.first().toFloat()
        return base.thresholdsMs.map { (it * scale).toLong() }.distinct().sorted()
    }

    private fun scaledRepeatInterval(firstMinutes: Int, intensity: com.doombuddy.session.Intensity): Long {
        val base = InterventionPolicy.forIntensity(intensity)
        val scale = firstMinutes.coerceIn(1, 30) * 60_000L / base.thresholdsMs.first().toFloat()
        return (base.repeatIntervalMs * scale).toLong()
    }

    private suspend fun runPollLoop() {
        poller.loop { fg ->
            engineInput.trySend(SessionInput.ForegroundChanged(fg, SystemMonotonicClock.nowMs()))
        }
    }

    /**
     * Heartbeat: threshold evaluation happens ONLY on Tick inputs. Without this loop
     * no intervention can ever fire (this exact regression shipped once — the engine
     * never evaluates thresholds on foreground events alone).
     */
    private suspend fun runTickLoop() {
        while (true) {
            delay(1_000)
            val minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
            engineInput.trySend(SessionInput.Tick(SystemMonotonicClock.nowMs(), minuteOfDay))
        }
    }

    /** Single consumer: the ONLY place that touches the engine. */
    private suspend fun runEngineQueue() {
        for (input in engineInput) {
            val outputs = engine.onInput(input)
            dispatch(outputs)
            when (input) {
                is SessionInput.Tick -> {
                    updateLiveTimers(input.nowMs)
                    checkTodayMilestone()
                    checkpointSession(input.nowMs)
                }
                is SessionInput.ForegroundChanged -> currentPackageName = input.packageName
                else -> Unit
            }
            publishDebugState()
        }
    }

    private fun dispatch(outputs: List<SessionOutput>) {
        if (outputs.isEmpty()) return
        mainScope.launch {
            for (output in outputs) when (output) {
                is SessionOutput.SessionStarted -> onSessionStarted(output)
                is SessionOutput.SessionEnded -> onSessionEnded(output)
                is SessionOutput.SessionPaused -> onSessionPaused(output)
                is SessionOutput.SessionResumed -> onSessionResumed(output)
                is SessionOutput.ThresholdReached -> onThreshold(output)
                is SessionOutput.RapidReopen -> onRapidReopen(output)
                is SessionOutput.LongSession -> onLongSession(output)
            }
        }
    }

    // ------------------------------------------------------------ session lifecycle

    private var checkpointRowId: Long? = null
    private var lastCheckpointMs: Long = 0
    /** True while the tracked app is backgrounded: session frozen, buddy hidden. */
    @Volatile private var sessionPaused = false
    /** Frozen elapsed duration while away; monotonic wall time must not advance it. */
    @Volatile private var pausedSessionElapsedMs: Long = 0
    private var wasPersistentOnPause = false
    private var lastInterventionUi: OverlayUiState? = null
    private var activeInterventionGeneration: Long = 0
    private var entranceCompletionJob: Job? = null
    private var dismissalJob: Job? = null
    /** Serializes the short hand-off between threshold insertion and hold/exit. */
    private val interventionRowLock = Any()
    private var pendingInterventionDismissedAtMs: Long? = null
    @Volatile private var interventionRequestGeneration: Long = 0
    /** Invalidates an in-flight async Today baseline read when a session ends. */
    @Volatile private var todayLoadGeneration: Long = 0

    private fun onSessionStarted(output: SessionOutput.SessionStarted) {
        sessionStartedAtMs = output.atMs
        sessionPaused = false
        pausedSessionElapsedMs = 0
        lastMilestoneMs = 0
        lastPushedSessionMs = -1L
        lastPushedTodayMs = -1L
        val loadGeneration = todayLoadGeneration + 1
        todayLoadGeneration = loadGeneration
        val todayStart = SessionSplitter.todayStart(java.time.ZoneId.systemDefault())
        if (persistedTodayStartEpochMs != todayStart) {
            persistedTodayMs = 0
            persistedTodayStartEpochMs = todayStart
        }
        scope.launch(Dispatchers.IO) {
            // Baseline for LIVE today: persisted total + running session (spec §7).
            val loaded = DoomBuddyDatabase.get(this@MonitorService).sessionDao()
                .totalUsageSinceOnce(todayStart, currentTrackedApps.toList()) ?: 0
            // Do not let a stale read from the previous session erase a completed
            // session that has already been folded into the in-memory Today total.
            if (loadGeneration == todayLoadGeneration && persistedTodayStartEpochMs == todayStart) {
                persistedTodayMs = maxOf(persistedTodayMs, loaded)
            }
        }
        // Checkpoint row: guarantees a usage record survives a hard process kill.
        val startWall = System.currentTimeMillis() - 0
        scope.launch(Dispatchers.IO) {
            checkpointRowId = DoomBuddyDatabase.get(this@MonitorService).sessionDao().insertSession(
                SessionEntity(
                    packageName = output.packageName,
                    startedAtEpochMs = startWall,
                    endedAtEpochMs = startWall,
                    durationMs = 0,
                    interventionCount = 0,
                    quickExitAfterIntervention = false,
                    isSessionStart = true,
                    isCheckpoint = true,
                ),
            )
            sessionStartWallMs = startWall
            lastCheckpointMs = 0
        }
    }

    /** Every 30s: persist running-session duration so kills lose at most ~30s. */
    private fun checkpointSession(nowMs: Long) {
        if (sessionStartedAtMs <= 0) return
        val rowId = checkpointRowId ?: return
        val elapsed = nowMs - sessionStartedAtMs
        if (elapsed - lastCheckpointMs < 30_000) return
        lastCheckpointMs = elapsed
        val row = rowId
        scope.launch(Dispatchers.IO) {
            DoomBuddyDatabase.get(this@MonitorService).sessionDao().updateSessionDuration(
                id = row,
                endedAtEpochMs = System.currentTimeMillis(),
                durationMs = elapsed,
            )
        }
    }

    /** Replace the checkpoint row with the final (midnight-split) session rows. */
    private suspend fun finalizeSession(
        output: SessionOutput.SessionEnded,
        startWall: Long,
        endWall: Long,
    ) {
        val db = DoomBuddyDatabase.get(this@MonitorService)
        val dao = db.sessionDao()
        checkpointRowId?.let { dao.deleteSession(it) }
        val rows = SessionSplitter.split(startWall, endWall, java.time.ZoneId.systemDefault())
        rows.forEachIndexed { index, (dayStart, chunkMs) ->
            val chunkStart = if (index == 0) startWall else dayStart
            dao.insert(
                SessionEntity(
                    packageName = output.packageName,
                    startedAtEpochMs = chunkStart,
                    endedAtEpochMs = chunkStart + chunkMs,
                    durationMs = chunkMs,
                    interventionCount = if (index == 0) output.interventionCount else 0,
                    quickExitAfterIntervention = index == 0 && output.interventionCount > 0 &&
                        output.endedBy == EndReason.USER_LEFT,
                    isSessionStart = index == 0,
                    isCheckpoint = false,
                ),
            )
        }
        checkpointRowId = null
        sessionStartWallMs = 0
    }

    private fun onThreshold(output: SessionOutput.ThresholdReached) {
        // A threshold can arrive while an older presentation is finishing (for
        // example after a rapid foreground transition). Keep one authoritative
        // intervention instead of letting overlapping windows/callbacks fight.
        if (interventionState != InterventionState.HIDDEN) return
        lastInterventionAtMs = output.atMs
        currentLevel = output.level

        val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
        val minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
        val choice = dialogue.select(
            character = character,
            event = DialogueEvent.THRESHOLD,
            level = output.level,
            foregroundAppName = output.packageName,
            minuteOfDay = minuteOfDay,
        )
        val line = if (choice.showAppQuip) choice.line
        else choice.line
            .replace("{app}", appLabel(output.packageName))
            .replace("{mins}", (output.sessionElapsedMs / 60_000).toString())

        // Record the intervention (dismissal time backfilled on hold-dismiss/exit).
        val requestGeneration = synchronized(interventionRowLock) {
            interventionRequestGeneration++
            pendingInterventionDismissedAtMs = null
            interventionRequestGeneration
        }
        scope.launch(Dispatchers.IO) {
            val dao = DoomBuddyDatabase.get(this@MonitorService).sessionDao()
            val rowId = dao.insertIntervention(
                InterventionEntity(
                    packageName = output.packageName,
                    level = output.level,
                    startedAtEpochMs = System.currentTimeMillis(),
                    characterId = currentCharacterId,
                ),
            )
            val dismissedAt = synchronized(interventionRowLock) {
                if (requestGeneration != interventionRequestGeneration) {
                    // The intervention was hidden by a session transition/debug
                    // teardown before its insert completed; close the late row.
                    System.currentTimeMillis()
                } else {
                    val pending = pendingInterventionDismissedAtMs
                    if (pending == null) openInterventionRowId = rowId else pendingInterventionDismissedAtMs = null
                    pending
                }
            }
            if (dismissedAt != null) dao.markInterventionDismissed(rowId, dismissedAt)
        }

        dismissalJob?.cancel()
        entranceCompletionJob?.cancel()
        interventionState = InterventionState.ENTERING
        val ui = OverlayUiState(
            character = character,
            accessory = currentAccessory,
            moodLevel = output.level,
            // Level 1 is purely visual: a peeking buddy IS the message.
            dialogueLine = if (output.level <= 1) null else line,
            timerMode = timerMode,
            sessionMs = output.sessionElapsedMs,
            todayMs = liveTodayMs(),
            cycleSeconds = timerCycleSeconds,
            holdDurationMs = holdDurationMs,
            uiScale = overlayScale,
        )
        lastInterventionUi = ui
        overlay.show(ui, overlayXFrac, overlayYFrac, interactive = true)
        activeInterventionGeneration = overlay.currentPresentationGeneration
        director.enter(pickEntranceFor(output.level), accessory = currentAccessory)
        director.setMood(output.level)
        director.setSessionProgress((output.level.toFloat() / currentPolicy.maxEscalationLevel).coerceIn(0f, 1f))
        if (output.level >= 2) director.react(CharacterDirector.Reaction.TALK)
        if (output.level >= 4) director.react(CharacterDirector.Reaction.WOBBLE)
        if (output.level >= 5) director.react(CharacterDirector.Reaction.EXHAUSTED)

        // Entrance ends → PERSISTENT. No timer, no coroutine, no timeout will remove it.
        val generation = activeInterventionGeneration
        entranceCompletionJob = mainScope.launch {
            delay(700)
            if (interventionState == InterventionState.ENTERING &&
                overlay.currentPresentationGeneration == generation &&
                overlay.isAttached
            ) {
                interventionState = InterventionState.PERSISTENT
                publishDebugState()
            }
        }
    }

    /** Hold-to-dismiss COMPLETED: react, record, exit. The SESSION CONTINUES (spec §35). */
    private fun onHoldDismissed() {
        if (interventionState == InterventionState.HIDDEN || interventionState == InterventionState.DISMISSING) return
        interventionState = InterventionState.DISMISSING
        entranceCompletionJob?.cancel()
        dismissalJob?.cancel()
        val generation = activeInterventionGeneration
        val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
        val line = dialogue.select(character, DialogueEvent.DISMISSED)
        director.onHoldComplete()

        // Persist dismissal; grant the engine's dismissal grace; session keeps running.
        val dismissedAt = System.currentTimeMillis()
        val rowId = synchronized(interventionRowLock) {
            val row = openInterventionRowId
            if (row == null) pendingInterventionDismissedAtMs = dismissedAt else openInterventionRowId = null
            row
        }
        scope.launch(Dispatchers.IO) {
            if (rowId != null) {
                DoomBuddyDatabase.get(this@MonitorService).sessionDao()
                    .markInterventionDismissed(rowId, dismissedAt)
            }
        }
        engineInput.trySend(SessionInput.BuddyDismissed(SystemMonotonicClock.nowMs()))

        dismissalJob = mainScope.launch {
            overlay.update(
                overlay.state.copy(dialogueLine = line.line, moodLevel = overlay.state.moodLevel),
            )
            overlay.hapticTick()
            delay(900) // let the dismissal line land, then exit
            if (interventionState == InterventionState.DISMISSING &&
                activeInterventionGeneration == generation
            ) {
                interventionState = InterventionState.HIDDEN
                overlay.hideIfGeneration(generation)
                director.hide(com.doombuddy.character.Exit.TUCK_AND_RUN)
                activeInterventionGeneration = 0
                lastInterventionUi = null
                publishDebugState()
            }
        }
        publishDebugState()
    }

    /**
     * Tracked app went background: hide the buddy WITH the app, freeze the timer.
     * Nothing is finalized — the session may resume exactly where it was.
     */
    private fun onSessionPaused(output: SessionOutput.SessionPaused) {
        sessionPaused = true
        pausedSessionElapsedMs = output.elapsedMs
        wasPersistentOnPause = interventionState == InterventionState.PERSISTENT
        entranceCompletionJob?.cancel()
        dismissalJob?.cancel()
        // Backgrounding is a platform-driven hide. Preserve whether a persistent
        // intervention should return, but expose HIDDEN while no window is attached.
        interventionState = InterventionState.HIDDEN
        activeInterventionGeneration = 0
        mainScope.launch {
            overlay.hide()
            director.hide(com.doombuddy.character.Exit.SINK)
        }
        publishDebugState()
    }

    /**
     * User returned within the resume window: continue from the exact paused elapsed
     * (no reset, no extra time). If the buddy was persistent when they left, it comes
     * back immediately with the timer picking up mid-flight.
     */
    private fun onSessionResumed(output: SessionOutput.SessionResumed) {
        sessionPaused = false
        sessionStartedAtMs = output.atMs - output.elapsedMs
        pausedSessionElapsedMs = output.elapsedMs
        if (wasPersistentOnPause) {
            val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
            val ui = (lastInterventionUi ?: OverlayUiState(character = character))
                .copy(
                    character = character,
                    accessory = currentAccessory,
                    sessionMs = output.elapsedMs,
                    todayMs = liveTodayMs(),
                    uiScale = overlayScale,
                )
            interventionState = InterventionState.PERSISTENT
            overlay.show(ui, overlayXFrac, overlayYFrac, interactive = true)
            activeInterventionGeneration = overlay.currentPresentationGeneration
            director.enter(com.doombuddy.character.Entrance.POP, accessory = currentAccessory)
            director.setMood(ui.moodLevel)
        }
        wasPersistentOnPause = false
        publishDebugState()
    }

    private fun onSessionEnded(output: SessionOutput.SessionEnded) {
        todayLoadGeneration++
        synchronized(interventionRowLock) {
            interventionRequestGeneration++
        }
        sessionPaused = false
        pausedSessionElapsedMs = 0
        wasPersistentOnPause = false
        entranceCompletionJob?.cancel()
        dismissalJob?.cancel()
        sessionStartedAtMs = 0
        interventionState = InterventionState.HIDDEN
        activeInterventionGeneration = 0
        // Close any still-open intervention row (session ended while buddy was showing).
        val dismissedAt = System.currentTimeMillis()
        val rowId = synchronized(interventionRowLock) {
            val row = openInterventionRowId
            if (row == null) pendingInterventionDismissedAtMs = dismissedAt else openInterventionRowId = null
            row
        }
        if (rowId != null) {
            scope.launch(Dispatchers.IO) {
                DoomBuddyDatabase.get(this@MonitorService).sessionDao()
                    .markInterventionDismissed(rowId, dismissedAt)
            }
        }

        // Finalize the session record (midnight-split, spec §41) — on IO. The
        // checkpoint row written during the session is replaced by the final rows.
        val endWall = System.currentTimeMillis()
        val startWall = endWall - output.durationMs
        scope.launch(Dispatchers.IO) {
            runCatching { finalizeSession(output, startWall, endWall) }
                .onFailure { Log.w(TAG, "session finalize failed", it) }
        }

        // The buddy hides the INSTANT the tracked app exits — it never lingers over
        // another app, launcher, or a backgrounded host (user directive + spec §36/§50).
        mainScope.launch {
            overlay.hide()
            director.hide(com.doombuddy.character.Exit.SINK)
        }
        // Keep Today live after the session ends. Account only for the portion of
        // a midnight-spanning session that belongs to the current calendar day.
        val todayStart = SessionSplitter.todayStart(java.time.ZoneId.systemDefault(), endWall)
        if (persistedTodayStartEpochMs != todayStart) {
            persistedTodayMs = 0
            persistedTodayStartEpochMs = todayStart
        }
        val todayChunkMs = SessionSplitter.split(startWall, endWall, java.time.ZoneId.systemDefault())
            .firstOrNull { it.first == todayStart }?.second ?: 0
        persistedTodayMs += todayChunkMs
        publishDebugState()
    }

    private fun onRapidReopen(output: SessionOutput.RapidReopen) {
        // If the persistent buddy is about to return on resume, it IS the reaction.
        if (wasPersistentOnPause && sessionPaused) return
        // Never put a temporary reaction over an entering/persistent/dismissing
        // intervention. Its delayed cleanup must not compete with the state machine.
        if (interventionState != InterventionState.HIDDEN) return
        val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
        val line = dialogue.select(character, DialogueEvent.RAPID_REOPEN, level = 2)
        overlay.show(
            OverlayUiState(
                character = character,
                accessory = currentAccessory,
                moodLevel = 2,
                dialogueLine = line.line,
                timerMode = timerMode,
                uiScale = overlayScale,
            ),
            overlayXFrac,
            overlayYFrac,
            interactive = false,
        )
        val generation = overlay.currentPresentationGeneration
        director.enter(com.doombuddy.character.Entrance.POP, accessory = currentAccessory)
        director.setMood(2)
        director.react(CharacterDirector.Reaction.WOBBLE)
        mainScope.launch {
            delay(RAPID_REOPEN_MS)
            if (interventionState == InterventionState.HIDDEN &&
                overlay.currentPresentationGeneration == generation
            ) {
                overlay.hideIfGeneration(generation)
                director.hide(com.doombuddy.character.Exit.HOP_AWAY)
            }
        }
    }

    private fun onLongSession(output: SessionOutput.LongSession) {
        if (!overlay.isAttached) return
        val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
        val line = dialogue.select(character, DialogueEvent.LONG_SESSION)
        overlay.update(overlay.state.copy(moodLevel = 6, dialogueLine = line.line))
        director.setMood(6)
        director.react(CharacterDirector.Reaction.EXHAUSTED)
    }

    // ------------------------------------------------------------ live timers (spec §7, §10)

    private fun liveTodayMs(): Long =
        com.doombuddy.data.ReclaimedCalculator.liveTodayMs(
            persistedTodayMs,
            liveSessionElapsedMs(),
        )

    private fun liveSessionElapsedMs(nowMs: Long = SystemMonotonicClock.nowMs()): Long =
        if (sessionStartedAtMs > 0) {
            if (sessionPaused) pausedSessionElapsedMs
            else (nowMs - sessionStartedAtMs).coerceAtLeast(0)
        } else 0

    private var lastPushedSessionMs = -1L
    private var lastPushedTodayMs = -1L

    /** Push live session/today into the attached overlay once per tick (1s cadence). */
    private fun updateLiveTimers(nowMs: Long) {
        if (sessionStartedAtMs <= 0 || !overlay.isAttached) return
        val sessionMs = liveSessionElapsedMs(nowMs)
        val todayMs = persistedTodayMs + sessionMs
        if (sessionMs == lastPushedSessionMs && todayMs == lastPushedTodayMs) return
        lastPushedSessionMs = sessionMs
        lastPushedTodayMs = todayMs
        mainScope.launch { overlay.update(overlay.state.copy(sessionMs = sessionMs, todayMs = todayMs)) }
    }

    /** Rare, meaningful reactions when TODAY crosses a milestone (spec §56). */
    private fun checkTodayMilestone() {
        val live = liveTodayMs()
        val crossed = TodayMilestones.crossed(lastMilestoneMs, live) ?: return
        lastMilestoneMs = crossed
        if (!overlay.isAttached) return
        val character = com.doombuddy.character.CharacterRoster.byId(currentCharacterId)
        val line = dialogue.select(character, DialogueEvent.TODAY_MILESTONE)
        if (line.line.isEmpty()) return
        mainScope.launch {
            overlay.update(overlay.state.copy(dialogueLine = line.line))
            director.react(CharacterDirector.Reaction.MILESTONE)
        }
    }

    // ------------------------------------------------------------ debug (spec §69/§70)

    private fun publishDebugState() {
        debugState.value = DebugState(
            foreground = currentPackageName,
            interventionState = interventionState,
            sessionPackage = if (sessionStartedAtMs > 0) currentPackageName else null,
            sessionElapsedMs = if (sessionStartedAtMs > 0) SystemMonotonicClock.nowMs() - sessionStartedAtMs else 0,
            sessionStartWallMs = sessionStartWallMs,
            sessionPaused = sessionPaused,
            persistedTodayMs = persistedTodayMs,
            liveTodayMs = liveTodayMs(),
            lastLevel = currentLevel,
            overlayAttached = overlay.isAttached,
            usageAccessGranted = UsageAccess.isGranted(this),
            overlayPermissionGranted = overlay.canDrawOverlays(),
            characterId = currentCharacterId,
            thresholdsMs = currentPolicy.thresholdsMs,
        )
    }

    // --- Dev/debug hooks ---

    fun debugTriggerLevel(level: Int) {
        mainScope.launch {
            onThreshold(
                SessionOutput.ThresholdReached(
                    level = level.coerceIn(1, 6),
                    atMs = SystemMonotonicClock.nowMs(),
                    sessionElapsedMs = level * 5 * 60_000L,
                    packageName = "com.instagram.android",
                ),
            )
        }
    }

    fun debugDismiss() = mainScope.launch { onHoldDismissed() }

    /**
     * Immediately tears down whatever the overlay is currently showing, with no
     * dialogue/exit flourish and no DB writes. Used by onboarding's live-test step
     * (spec: a test buddy shown purely for demo purposes must never linger once the
     * user moves on — it previously stayed floating over Home/Settings forever since
     * nothing ever called hide() for it).
     */
    fun debugForceHideOverlay() {
        mainScope.launch {
            entranceCompletionJob?.cancel()
            dismissalJob?.cancel()
            interventionState = InterventionState.HIDDEN
            activeInterventionGeneration = 0
            synchronized(interventionRowLock) {
                interventionRequestGeneration++
                openInterventionRowId = null
                pendingInterventionDismissedAtMs = null
            }
            overlay.hide()
            director.hide(com.doombuddy.character.Exit.SINK)
        }
        publishDebugState()
    }

    /** Simulates the user leaving the tracked app (session end + persistence). */
    fun debugEndSession() {
        mainScope.launch {
            onSessionEnded(
                SessionOutput.SessionEnded(
                    packageName = "com.instagram.android",
                    atMs = SystemMonotonicClock.nowMs(),
                    durationMs = 6 * 60_000L,
                    interventionCount = if (overlay.isAttached) 1 else 0,
                    endedBy = EndReason.USER_LEFT,
                ),
            )
        }
    }

    fun debugAddFakeToday(minutes: Int) {
        persistedTodayMs += minutes * 60_000L
        mainScope.launch { overlay.update(overlay.state.copy(todayMs = liveTodayMs())) }
        publishDebugState()
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        packageName.substringAfterLast('.').ifEmpty { packageName }
    }

    private fun pickEntranceFor(level: Int) = when {
        level <= 1 -> com.doombuddy.character.Entrance.PEEK_IN
        level == 2 -> com.doombuddy.character.Entrance.POP
        else -> com.doombuddy.character.Entrance.entries.filter { it != com.doombuddy.character.Entrance.PEEK_IN }.random()
    }

    private fun registerScreenReceiver() {
        val receiver = ScreenStateReceiver { screenOn ->
            engineInput.trySend(
                if (screenOn) SessionInput.ScreenTurnedOn else SessionInput.ScreenTurnedOff,
            )
        }
        screenReceiver = receiver
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    private fun startInForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Buddy on duty",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps your buddy watching for doomscroll sessions."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoomBuddy is on duty")
            .setContentText("Your buddy will pop in when the scrolling gets long.")
            .setSmallIcon(R.drawable.ic_stat_buddy)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // User swiped the app away: keep the companion alive (stopWithTask=false keeps
        // this FGS running; the alarm is a belt-and-braces restart in case the system
        // still kills us). Sessions/overlay only work if the service survives.
        WatchdogReceiver.scheduleRestart(this, delayMs = 1_500)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        WatchdogReceiver.scheduleRestart(this, delayMs = 2_000)
        instance = null
        screenReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        engineInput.close()
        mainScope.launch { overlay.hide() }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DoomBuddy"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 42
        private const val CELEBRATION_MS = 3_200L
        private const val RAPID_REOPEN_MS = 3_500L

        @Volatile var instance: MonitorService? = null
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
