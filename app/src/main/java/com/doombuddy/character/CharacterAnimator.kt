package com.doombuddy.character

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Choreographs everything the buddy DOES. Events set the mood; the session-progress
 * scheduler makes the buddy act it out (peek → fidget → popcorn → nest → nap) so the
 * intervention shows time passing instead of announcing it.
 *
 * All animation jobs live here and are cancelled when hidden — zero rendering cost
 * when the buddy is off screen (docs/PERFORMANCE.md).
 */
class CharacterDirector(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    var pose by androidx.compose.runtime.mutableStateOf(CharacterPose.hidden)
        private set

    // Animatable rig channels — the idle loop composes these into [pose] every frame.
    private val sx = Animatable(1f)
    private val sy = Animatable(1f)
    private val ox = Animatable(0f)
    private val oy = Animatable(0f)
    private val rot = Animatable(0f)
    private val alpha = Animatable(0f)
    private val lookX = Animatable(0f)
    private val lookY = Animatable(0f)
    private val blink = Animatable(0f)
    private val eyeLid = Animatable(0f)
    private val mouthOpen = Animatable(0f)
    private val brow = Animatable(0f)
    private val armL = Animatable(0f)
    private val armR = Animatable(0f)
    private val peekF = Animatable(0f)

    private var idleJob: Job? = null
    private var entranceJob: Job? = null
    private var choreoJob: Job? = null
    private var actionJob: Job? = null

    private var currentMouth = MouthShape.SMALL_SMILE
    private var currentProp = Prop.NONE
    private var currentEffects = Effects()
    private var currentAccessory = Accessory.NONE
    private var currentMood = 0
    private var sessionProgress = 0f
    private var visible = false

    val isShown: Boolean get() = visible

    // ---------------------------------------------------------------- entry/exit

    fun enter(entrance: Entrance = pickEntrance(), accessory: Accessory = currentAccessory) {
        currentAccessory = accessory
        visible = true
        choreoJob?.cancel()
        actionJob?.cancel()
        idleJob?.cancel()
        entranceJob?.cancel()
        // Guarantee visibility BEFORE any coroutine: the buddy must never render
        // at alpha 0 while entrance/idle jobs are still spinning up. (Animatable
        // snapTo is suspend; the pose state alone is what the renderer reads, and
        // entranceSequence snaps the Animatable on its first lines anyway.)
        pose = CharacterPose(
            alpha = 1f,
            accessory = accessory,
            mouth = currentMouth,
            offsetY = -0.15f,
        )
        entranceJob = scope.launch { entranceSequence(entrance) }
        idleJob = scope.launch { startIdleLoop() }
        startChoreography()
    }

    // Motion amplitudes below are deliberately conservative (roughly half of what this
    // rig used to ship). The character is drawn inside a tightly-sized canvas (the real
    // overlay is a WRAP_CONTENT system window with only ~20–40dp of headroom above the
    // body) and Android cannot draw outside a window's own bounds — large offsets/scale
    // peaks here were flying the buddy above the window's actual top edge, so it was
    // invisible (clipped) for the first part of every entrance. Keep excursions within
    // roughly ±0.5x of the canvas size. This also reads as snappier/more premium instead
    // of the previous oversized cartoon-bounce.
    private suspend fun entranceSequence(entrance: Entrance) {
        when (entrance) {
            Entrance.BOUNCE -> {
                snap(offY = -0.5f, sy = 0.82f, sx = 1.14f, a = 1f)
                oy.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                sy.animateTo(1f, spring(0.42f)); sx.animateTo(1f, spring(0.42f))
            }
            Entrance.FALL -> {
                snap(offY = -0.58f, rot = -12f, a = 1f, mouth = MouthShape.OPEN_O)
                oy.animateTo(0f, spring(0.55f, Spring.StiffnessMediumLow))
                sy.snapTo(0.82f); sx.snapTo(1.16f)
                sy.animateTo(1f, spring(0.38f)); sx.animateTo(1f, spring(0.38f))
                rot.animateTo(0f, spring(0.55f))
                mouthOpen.animateTo(0f, tween(220))
            }
            Entrance.SLIDE -> {
                // Same reasoning as Exit.SLIDE_OUT: the window is often barely wider than
                // the character canvas, so a large offX is clipped by the window edge.
                snap(offX = 0.42f, a = 1f)
                alpha.snapTo(1f)
                ox.animateTo(0f, spring(0.6f, Spring.StiffnessMediumLow))
            }
            Entrance.POP -> {
                snap(sx = 0.3f, sy = 0.3f, a = 1f, mouth = MouthShape.OPEN_O)
                sx.animateTo(1.12f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
                sy.animateTo(0.92f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
                sx.animateTo(1f, spring(0.6f)); sy.animateTo(1f, spring(0.6f))
                mouthOpen.animateTo(0f, tween(240))
            }
            Entrance.PORTAL -> {
                snap(sx = 0.4f, sy = 1.2f, a = 0f)
                alpha.animateTo(1f, tween(170))
                sy.animateTo(0.88f, tween(110)); sx.animateTo(1.14f, tween(110))
                sy.animateTo(1f, spring(0.46f)); sx.animateTo(1f, spring(0.46f))
            }
            Entrance.PEEK_IN -> {
                peekF.snapTo(1f)
                alpha.snapTo(1f)
                act(Acting.PEEK, holdMs = 1_400)
            }
        }
    }

    fun hide(exit: Exit = Exit.SINK, onDone: () -> Unit = {}) {
        if (!visible) { onDone(); return }
        visible = false
        choreoJob?.cancel()
        actionJob?.cancel()
        idleJob?.cancel()
        entranceJob?.cancel()
        idleJob = scope.launch {
            when (exit) {
                Exit.SINK -> {
                    sy.animateTo(0.08f, tween(240, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)))
                    alpha.animateTo(0f, tween(180))
                }
                Exit.HOP_AWAY -> {
                    oy.animateTo(-0.42f, tween(170, easing = EaseOutBack))
                    alpha.animateTo(0f, tween(130, delayMillis = 50))
                }
                Exit.PUFF -> {
                    sx.animateTo(1.3f, tween(170)); sy.animateTo(1.3f, tween(170))
                    alpha.animateTo(0f, tween(150))
                }
                Exit.SLIDE_OUT -> {
                    // Horizontally the overlay window is often only ~8–20dp wider than the
                    // character canvas (no speech bubble forcing extra width), so this must
                    // stay small or the slide is clipped by the window edge before it's ever seen.
                    ox.animateTo(0.42f, tween(260))
                    alpha.animateTo(0f, tween(160))
                }
                Exit.TUCK_AND_RUN -> {
                    sx.animateTo(0.7f, tween(120)); sy.animateTo(0.7f, tween(120))
                    ox.animateTo(-0.48f, tween(420, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)))
                    alpha.animateTo(0f, tween(120, delayMillis = 260))
                }
            }
            pose = CharacterPose.hidden
            onDone()
        }
    }

    // ---------------------------------------------------------------- mood/events

    /** Escalation level 1..6 → baseline face + prop. Acting continues on top. */
    fun setMood(level: Int) {
        currentMood = level.coerceIn(0, 6)
        val (mouth, lid, browTarget) = when (currentMood) {
            0 -> Triple(MouthShape.SMALL_SMILE, 0f, 0.25f)
            1 -> Triple(MouthShape.SMALL_SMILE, 0f, 0.45f)
            2 -> Triple(MouthShape.SMILE, 0.05f, 0.55f)
            3 -> Triple(MouthShape.SMIRK, 0.15f, 0.7f)
            4 -> Triple(MouthShape.GRIN, 0.3f, 0.85f)
            5 -> Triple(MouthShape.FLAT, 0.55f, 0.4f)
            else -> Triple(MouthShape.WAVE, 0.75f, -0.2f)
        }
        currentMouth = mouth
        scope.launch {
            eyeLid.animateTo(lid, spring(stiffness = Spring.StiffnessLow))
            brow.animateTo(browTarget, spring(stiffness = Spring.StiffnessLow))
        }
    }

    fun react(reaction: Reaction) {
        scope.launch {
            when (reaction) {
                Reaction.JUMP -> {
                    oy.animateTo(-0.32f, tween(140, easing = EaseOutBack))
                    oy.animateTo(0f, spring(0.42f))
                    sy.snapTo(0.86f); sx.snapTo(1.12f)
                    sy.animateTo(1f, spring(0.3f)); sx.animateTo(1f, spring(0.3f))
                }
                Reaction.WOBBLE -> {
                    rot.animateTo(9f, tween(85)); rot.animateTo(-9f, tween(115))
                    rot.animateTo(5f, tween(95)); rot.animateTo(0f, spring(0.5f))
                }
                Reaction.TALK -> {
                    repeat(3) {
                        mouthOpen.animateTo(0.7f, tween(85))
                        mouthOpen.animateTo(0.12f, tween(85))
                    }
                }
                Reaction.EXHAUSTED -> {
                    brow.animateTo(-0.7f, tween(380))
                    oy.animateTo(0.05f, tween(380))
                    rot.animateTo(4f, tween(380))
                }
                Reaction.CELEBRATE -> act(Acting.CELEBRATE, holdMs = 1_800)
                Reaction.LOOK_AT_USER -> {
                    lookX.animateTo(0f, spring(0.65f, Spring.StiffnessLow))
                    lookY.animateTo(0.25f, spring(0.65f, Spring.StiffnessLow))
                }
                Reaction.MILESTONE -> reactToMilestone()
            }
        }
    }

    fun setLook(x: Float, y: Float) {
        scope.launch {
            lookX.animateTo(x.coerceIn(-1f, 1f), spring(0.7f, Spring.StiffnessLow))
            lookY.animateTo(y.coerceIn(-1f, 1f), spring(0.7f, Spring.StiffnessLow))
        }
    }

    fun setAccessory(accessory: Accessory) { currentAccessory = accessory }

    // ---------------------------------------------------------------- hold-to-dismiss

    private var holding = false

    /** User pressed and is holding: the buddy gets nervous and stops its own antics. */
    fun onHoldStart() {
        holding = true
        choreoJob?.cancel()
        actionJob?.cancel()
        scope.launch {
            brow.animateTo(1f, tween(140))
            eyeLid.animateTo(0f, tween(140))
            mouthOpen.animateTo(0.35f, tween(140))
            rot.animateTo(3f, tween(140))
            // panicked micro-wobble while held
            var i = 0
            while (holding) {
                rot.animateTo(if (i % 2 == 0) -2.5f else 2.5f, tween(110))
                i++
            }
        }
    }

    /** Released early: relief, back to whatever it was doing. */
    fun onHoldRelease() {
        if (!holding) return
        holding = false
        scope.launch {
            brow.animateTo(browForMood(), spring(0.5f))
            mouthOpen.animateTo(0f, tween(160))
            rot.animateTo(0f, spring(0.5f))
        }
        startChoreography()
    }

    /** Hold completed: the buddy accepts its fate (service triggers exit + dismissal line). */
    fun onHoldComplete() {
        holding = false
        scope.launch {
            mouthOpen.animateTo(0f, tween(120))
            rot.animateTo(0f, spring(0.5f))
        }
    }

    private fun browForMood() = when (currentMood) {
        5 -> 0.4f; 6 -> -0.2f; else -> 0.55f
    }

    /** Quick glance toward the timer card — "reading" the time it's holding. */
    fun lookAtTimer() {
        scope.launch {
            lookX.animateTo(0.55f, spring(0.7f, Spring.StiffnessLow))
            lookY.animateTo(0.6f, spring(0.7f, Spring.StiffnessLow))
            delay(900)
            lookX.animateTo(0f, spring(0.7f, Spring.StiffnessLow))
            lookY.animateTo(0.1f, spring(0.7f, Spring.StiffnessLow))
        }
    }

    /** Rare, meaningful reaction to the DAY total crossing a milestone (spec §56). */
    fun reactToMilestone() {
        scope.launch {
            brow.animateTo(-0.6f, tween(360))
            mouthOpen.animateTo(0.1f, tween(360))
            oy.animateTo(0.04f, tween(360))       // small slump
            eyeLid.animateTo((eyeLid.value + 0.5f).coerceAtMost(0.8f), tween(360))
            delay(1_400)
            oy.animateTo(0f, spring(0.6f))
            eyeLid.animateTo(eyeLidForMood(), spring(0.6f))
            brow.animateTo(browForMood(), spring(0.6f))
            mouthOpen.animateTo(0f, spring(0.6f))
        }
    }

    // ---------------------------------------------------------------- choreography

    /**
     * Progress 0..1 across the session's intervention ladder. Resets the acting
     * scheduler so the pool matches the new phase.
     */
    fun setSessionProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped != sessionProgress) {
            sessionProgress = clamped
            if (visible) startChoreography()
        }
    }

    private fun startChoreography() {
        choreoJob?.cancel()
        choreoJob = scope.launch {
            while (visible) {
                val pool = Choreography.poolFor(sessionProgress)
                val action = Choreography.pick(pool, lastAction, random)
                if (action != null) {
                    act(action, holdMs = Choreography.holdMs(action, random))
                    lastAction = action
                }
                delay(Choreography.gapMs(sessionProgress, random))
            }
        }
    }

    private var lastAction: Acting? = null
    private var headTilt: Float by androidx.compose.runtime.mutableStateOf(0f)

    /** Performs one scripted acting beat, then settles back to idle micro-motion. */
    private fun act(action: Acting, holdMs: Long) {
        actionJob?.cancel()
        actionJob = scope.launch {
            when (action) {
                Acting.PEEK -> {
                    currentProp = Prop.NONE
                    peekF.snapTo(if (peekF.value > 0.9f) 1f else 0.55f)
                    lookX.animateTo(0f, tween(200))
                    // eyes dart while hiding
                    delay(holdMs / 3)
                    lookX.snapTo(0.6f); delay(160)
                    lookX.snapTo(-0.6f); delay(200)
                    lookX.animateTo(0f, tween(140))
                    delay(holdMs / 3)
                    peekF.animateTo(0f, tween(320))
                }
                Acting.FIDGET_PHONE -> {
                    currentProp = Prop.TINY_PHONE
                    headTiltPose(-9f)
                    armR.animateTo(0.55f, tween(260))
                    mouthOpen.animateTo(0.05f, tween(200))
                    delay(holdMs)
                    headTiltPose(0f)
                    armR.animateTo(0f, tween(220))
                    currentProp = Prop.NONE
                }
                Acting.POPCORN -> {
                    currentProp = Prop.POPCORN
                    armL.animateTo(0.35f, tween(240))
                    // grab → eat loop
                    repeat((holdMs / 700).toInt().coerceIn(2, 8)) {
                        armR.animateTo(0.75f, tween(180))
                        armR.animateTo(0.25f, tween(160))
                        mouthOpen.animateTo(0.45f, tween(120))
                        mouthOpen.animateTo(0.1f, tween(160))
                    }
                    currentEffects = currentEffects.copy(snackCrumbs = true)
                    armL.animateTo(0f, tween(220))
                    currentEffects = currentEffects.copy(snackCrumbs = false)
                    currentProp = Prop.NONE
                }
                Acting.CLOCK_TAP -> {
                    currentProp = Prop.POCKET_CLOCK
                    armR.animateTo(0.7f, tween(240))
                    repeat(3) {
                        armR.animateTo(0.55f, tween(110))
                        armR.animateTo(0.7f, tween(110))
                    }
                    delay(holdMs / 2)
                    armR.animateTo(0f, tween(220))
                    currentProp = Prop.NONE
                }
                Acting.NEST -> {
                    currentProp = Prop.BLANKET
                    sy.animateTo(0.9f, tween(300))
                    delay(holdMs)
                    if (currentMood < 4) currentProp = Prop.NONE
                    sy.animateTo(1f, tween(260))
                }
                Acting.NAP -> {
                    currentProp = Prop.PILLOW
                    currentEffects = currentEffects.copy(zzz = true)
                    eyeLid.animateTo(0.95f, tween(400))
                    rot.animateTo(-7f, tween(400))
                    mouthOpen.animateTo(0.12f, tween(400))
                    delay(holdMs)
                    // one eye opens to check on you
                    eyeLid.animateTo(0.2f, tween(160))
                    delay(350)
                    eyeLid.animateTo(0.95f, tween(160))
                    delay(500)
                    currentEffects = currentEffects.copy(zzz = false)
                    rot.animateTo(0f, spring(0.5f))
                    eyeLid.animateTo(eyeLidForMood(), spring(0.6f))
                    currentProp = Prop.NONE
                }
                Acting.YAWN -> {
                    mouthOpen.animateTo(0.9f, tween(320))
                    armL.animateTo(0.85f, tween(320))
                    armR.animateTo(0.85f, tween(320))
                    eyeLid.animateTo(0.8f, tween(320))
                    delay(holdMs / 2)
                    mouthOpen.animateTo(0f, tween(280))
                    armL.animateTo(0f, tween(280)); armR.animateTo(0f, tween(280))
                    eyeLid.animateTo(eyeLidForMood(), spring(0.6f))
                }
                Acting.STRETCH -> {
                    armL.animateTo(1f, tween(300)); armR.animateTo(1f, tween(300))
                    sy.animateTo(1.12f, tween(300)); sx.animateTo(0.92f, tween(300))
                    delay(holdMs / 2)
                    armL.animateTo(0f, tween(240)); armR.animateTo(0f, tween(240))
                    sy.animateTo(1f, spring(0.4f)); sx.animateTo(1f, spring(0.4f))
                }
                Acting.FACEPALM -> {
                    armR.animateTo(1f, tween(260))
                    headTiltPose(12f)
                    delay(holdMs)
                    headTiltPose(0f)
                    armR.animateTo(0f, tween(240))
                }
                Acting.CELEBRATE -> {
                    currentEffects = currentEffects.copy(confetti = true, sparkle = true)
                    repeat(2) {
                        oy.animateTo(-0.5f, tween(170, easing = EaseOutBack))
                        oy.animateTo(0f, tween(130))
                    }
                    armL.animateTo(1f, tween(120)); armR.animateTo(1f, tween(120))
                    armL.animateTo(-0.2f, tween(130)); armR.animateTo(-0.2f, tween(130))
                    armL.animateTo(1f, tween(120)); armR.animateTo(1f, tween(120))
                    armL.animateTo(0f, tween(160)); armR.animateTo(0f, tween(160))
                    delay(holdMs / 2)
                    currentEffects = currentEffects.copy(confetti = false, sparkle = false)
                }
                Acting.DIZZY -> {
                    currentEffects = currentEffects.copy(dizzy = true)
                    repeat(2) {
                        rot.animateTo(7f, tween(120)); rot.animateTo(-7f, tween(120))
                    }
                    rot.animateTo(0f, spring(0.5f))
                    delay(holdMs / 2)
                    currentEffects = currentEffects.copy(dizzy = false)
                }
                Acting.WAVE -> {
                    armR.animateTo(0.9f, tween(200))
                    repeat(3) { armR.animateTo(0.7f, tween(110)); armR.animateTo(0.95f, tween(110)) }
                    armR.animateTo(0f, tween(200))
                }
                Acting.IDLE -> Unit
            }
        }
    }

    private fun headTiltPose(deg: Float) { headTilt = deg }

    private fun eyeLidForMood() = when (currentMood) {
        5 -> 0.55f; 6 -> 0.75f; else -> 0f
    }

    // ---------------------------------------------------------------- idle frame loop

    private suspend fun startIdleLoop() {
        var t = 0
        var nextBlink = 50 + random.nextInt(180)
        while (visible) {
            withFrameNanos { }
            t++
            val bob = sin(t / 24f) * 0.016f
            val breathe = sin(t / 42f) * 0.011f
            val sleepBreath = ((sin(t / 95f) + 1f) / 2f)
            pose = CharacterPose(
                scaleX = sx.value,
                scaleY = sy.value + breathe,
                offsetX = ox.value,
                offsetY = oy.value + bob,
                rotation = rot.value,
                lean = sin(t / 60f) * 1.6f,
                alpha = alpha.value,
                lookX = lookX.value,
                lookY = lookY.value,
                blink = blink.value,
                eyeLid = eyeLid.value,
                mouth = currentMouth,
                mouthOpen = mouthOpen.value,
                browRaise = brow.value,
                armLeft = armL.value,
                armRight = armR.value,
                tailWag = t / 9f,
                headTilt = headTilt,
                acting = lastAction ?: Acting.IDLE,
                prop = currentProp,
                effects = currentEffects,
                accessory = currentAccessory,
                sleepBreath = sleepBreath,
                peekFraction = peekF.value,
            )
            if (t >= nextBlink) {
                scope.launch {
                    blink.snapTo(0f)
                    blink.animateTo(1f, tween(70, easing = LinearEasing))
                    blink.animateTo(0f, tween(95, easing = LinearEasing))
                }
                nextBlink = t + if (eyeLid.value > 0.7f) 260 + random.nextInt(300) else 90 + random.nextInt(280)
            }
            if (t % 240 == 0 && (lastAction == null || lastAction == Acting.IDLE)) {
                lookX.snapTo(if (random.nextBoolean()) 0.5f else -0.5f)
            }
        }
    }

    private suspend fun snap(
        sx: Float = this.sx.value, sy: Float = this.sy.value,
        offX: Float = this.ox.value, offY: Float = this.oy.value,
        rot: Float = this.rot.value, a: Float = this.alpha.value,
        mouth: MouthShape? = null,
    ) {
        this.sx.snapTo(sx); this.sy.snapTo(sy)
        ox.snapTo(offX); oy.snapTo(offY)
        this.rot.snapTo(rot); alpha.snapTo(a)
        if (mouth != null) { currentMouth = mouth; mouthOpen.snapTo(0.75f) }
    }

    fun pickEntrance(): Entrance {
        val base = if (sessionProgress < 0.25f) {
            listOf(Entrance.PEEK_IN, Entrance.BOUNCE, Entrance.SLIDE, Entrance.POP)
        } else {
            Entrance.entries.filter { it != Entrance.PEEK_IN }
        }
        val options = base.filter { it != lastEntrance }
        return options[random.nextInt(options.size)].also { lastEntrance = it }
    }
    private var lastEntrance: Entrance? = null

    enum class Reaction { JUMP, WOBBLE, TALK, EXHAUSTED, CELEBRATE, LOOK_AT_USER, MILESTONE }
}

/**
 * Pure choreography rules — unit-testable.
 * As the session progresses the buddy's acts escalate from curious to fully moved in.
 */
object Choreography {

    /** Weighted acting pools per session-progress bucket. */
    fun poolFor(progress: Float): List<Acting> = when {
        progress < 0.2f -> listOf(Acting.PEEK, Acting.WAVE, Acting.FIDGET_PHONE)
        progress < 0.45f -> listOf(Acting.FIDGET_PHONE, Acting.YAWN, Acting.CLOCK_TAP, Acting.WAVE)
        progress < 0.7f -> listOf(Acting.POPCORN, Acting.CLOCK_TAP, Acting.NEST, Acting.YAWN, Acting.STRETCH)
        progress < 0.9f -> listOf(Acting.NEST, Acting.POPCORN, Acting.FACEPALM, Acting.NAP, Acting.CLOCK_TAP)
        else -> listOf(Acting.NAP, Acting.FACEPALM, Acting.NEST, Acting.DIZZY)
    }

    fun pick(pool: List<Acting>, last: Acting?, random: Random): Acting? {
        if (pool.isEmpty()) return null
        val options = pool.filter { it != last }.ifEmpty { pool }
        return options[random.nextInt(options.size)]
    }

    fun holdMs(action: Acting, random: Random): Long = when (action) {
        Acting.PEEK -> 2_200L
        Acting.POPCORN -> 3_200L + random.nextLong(1_600)
        Acting.NAP -> 5_000L + random.nextLong(3_000)
        Acting.NEST -> 3_000L
        Acting.CLOCK_TAP -> 2_400L
        Acting.YAWN -> 1_600L
        Acting.STRETCH -> 1_800L
        Acting.FACEPALM -> 2_600L
        Acting.FIDGET_PHONE -> 2_800L + random.nextLong(1_200)
        Acting.CELEBRATE -> 2_000L
        Acting.DIZZY -> 1_800L
        Acting.WAVE -> 1_200L
        Acting.IDLE -> 800L
    }

    /** More progress → acts come more often. */
    fun gapMs(progress: Float, random: Random): Long {
        val base = (9_000f - 5_000f * progress).toLong()
        return base + random.nextLong(0, 4_000)
    }
}
