package com.doombuddy.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doombuddy.character.CharacterPose
import com.doombuddy.data.TimerMode
import com.doombuddy.ui.components.MiniCharacter3D
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Root of the persistent intervention overlay (spec §42):
 *
 *   [ quiet live badge + app-independent session/today timer ]
 *   [ large measured character stage ]
 *   [ optional spoken line + hold affordance ]
 *
 * This is intentionally a transparent character stage rather than a widget/card. The
 * character is the first read; the timer is a quiet caption that keeps the intervention
 * useful without competing with the performance. The window remains a stable measured
 * stage so animation cannot resize or clip the overlay.
 */
@Composable
fun OverlayRoot(controller: OverlayController) {
    val state = controller.state
    val character = state.character ?: return
    val director = controller.director
    val scope = rememberCoroutineScope()
    val u = state.uiScale.coerceIn(0.82f, 1.06f)
    val stageWidth = (218 * u).dp
    val stageHeight = (246 * u).dp

    Column(
        modifier = Modifier
            .width(stageWidth)
            .height(stageHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width((192 * u).dp)
                .height((38 * u).dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = state.dialogueLine.orEmpty(),
                transitionSpec = {
                    (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(120)))
                },
                label = "buddyDialogue",
            ) { line ->
                if (line.isNotBlank()) {
                    Text(
                        text = line,
                        modifier = Modifier.padding(horizontal = (8 * u).dp),
                        color = Color(0xFFF7FAFC),
                        fontSize = (13 * u).sp,
                        lineHeight = (14 * u).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        style = whisperTextStyle,
                    )
                }
            }
        }

        HoldableBuddy(
            state = state,
            director = director,
            controller = controller,
            scope = scope,
            scale = u,
        )

        BuddyTimerCard(
            mode = state.timerMode,
            sessionMs = state.sessionMs,
            todayMs = state.todayMs,
            cycleSeconds = state.cycleSeconds,
            scale = u,
            onCycleFlip = { director?.lookAtTimer() },
        )

        Spacer(Modifier.height((3 * u).dp))
        Text(
            text = if (state.dialogueLine == null) "tap me · hold to bounce" else "hold to bounce",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = (8 * u).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            style = whisperTextStyle,
        )
    }
}

// ---------------------------------------------------------------- timer whisper

@Composable
fun BuddyTimerCard(
    mode: TimerMode,
    sessionMs: Long,
    todayMs: Long,
    cycleSeconds: Int,
    showLabels: Boolean = true,
    scale: Float = 1f,
    onCycleFlip: () -> Unit = {},
) {
    val ts = (15 * scale).sp
    when (mode) {
        TimerMode.SESSION_ONLY ->
            TimerView("LIVE", formatSession(sessionMs), sessionAccent, valueSize = ts)
        TimerMode.STACKED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            TimerView("LIVE", formatSession(sessionMs), sessionAccent, valueSize = ts)
            TimerSeparator(scale)
            TimerView("TODAY", formatToday(todayMs), todayAccent, valueSize = ts)
        }
        TimerMode.CYCLE -> {
            var showToday by remember { mutableStateOf(false) }
            LaunchedEffect(cycleSeconds) {
                while (true) {
                    kotlinx.coroutines.delay(cycleSeconds * 1_000L)
                    showToday = !showToday
                    onCycleFlip()
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TimerView("LIVE", formatSession(sessionMs), sessionAccent, valueSize = ts)
                TimerSeparator(scale)
                AnimatedContent(
                    targetState = showToday,
                    transitionSpec = {
                        val dir = if (targetState) 1 else -1
                        (slideInHorizontally(tween(280)) { it * dir } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it * dir } + fadeOut(tween(160)))
                    },
                    label = "timerCycle",
                ) { today ->
                    TimerView(
                        if (today) "TODAY" else "NOW",
                        if (today) formatToday(todayMs) else "scrolling",
                        if (today) todayAccent else Color.White.copy(alpha = 0.72f),
                        valueSize = if (today) ts else (10 * scale).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerSeparator(scale: Float) {
    Text(
        text = "·",
        color = Color.White.copy(alpha = 0.5f),
        fontSize = (12 * scale).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = (7 * scale).dp),
        style = whisperTextStyle,
    )
}

@Composable
private fun TimerView(label: String, value: String, accent: Color, showLabels: Boolean = true, valueSize: androidx.compose.ui.unit.TextUnit = 19.sp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showLabels) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                style = whisperTextStyle,
            )
            Spacer(Modifier.height(1.dp))
        }
        Text(
            text = value,
            color = accent,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            style = whisperTextStyle,
        )
    }
}

private val whisperTextStyle = TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.82f),
        offset = Offset(0f, 1.5f),
        blurRadius = 3f,
    ),
)

private val sessionAccent = Color(0xFF7CC7FF)
private val todayAccent = Color(0xFFC6F24E)

internal fun formatSession(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

internal fun formatToday(ms: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

// ---------------------------------------------------------------- holdable buddy

/**
 * The character area: tap = poke (never dismisses), press-and-hold = dismissal with
 * a visible progress ring; releasing early or dragging away resets (spec §33).
 */
@Composable
private fun HoldableBuddy(
    state: OverlayUiState,
    director: com.doombuddy.character.CharacterDirector?,
    controller: OverlayController,
    scope: CoroutineScope,
    scale: Float,
) {
    val character = state.character ?: return
    val buddySize = (132 * scale).dp
    val ringSize = (144 * scale).dp
    val boxSize = (150 * scale).dp
    var holding by remember(character.id) { mutableStateOf(false) }
    var completed by remember(character.id) { mutableStateOf(false) }
    var pokeToken by remember(character.id) { mutableStateOf(0L) }
    val directorCelebrating by remember(director) {
        derivedStateOf { director?.pose?.effects?.confetti == true }
    }
    // pointerInput is keyed only by hold duration, so callbacks must read the
    // latest gesture state rather than a value captured at composition time.
    val latestHolding = rememberUpdatedState(holding)
    val latestCompleted = rememberUpdatedState(completed)
    val ringProgress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) {
            tween(durationMillis = state.holdDurationMs, easing = LinearEasing)
        } else {
            tween(durationMillis = 130)
        },
        finishedListener = {
            if (it >= 1f && latestHolding.value && !latestCompleted.value) {
                completed = true
                controller.onHoldComplete?.invoke()
            }
        },
    )

    Box(
        modifier = Modifier
            .size(boxSize),
        contentAlignment = Alignment.Center,
    ) {
        // Hold progress ring around the buddy — the dismissal affordance (shared component).
        com.doombuddy.ui.components.HoldRing(
            progress = ringProgress,
            size = ringSize,
            trackColor = Color.Transparent,
            modifier = Modifier.align(Alignment.Center),
        )

        // One gesture target contains the stage. A sibling AndroidView above this
        // target would steal taps/holds. Director alpha is authoritative: a deliberately
        // hidden pose must never be replaced with an opaque "fallback" buddy.
        Box(
            modifier = Modifier
                .size(buddySize)
                .pointerInput(character.id, director, state.holdDurationMs) {
                    detectTapGestures(
                        onPress = {
                            if (latestCompleted.value) return@detectTapGestures
                            holding = true
                            completed = false
                            controller.onHoldStart?.invoke()
                            director?.onHoldStart()
                            try {
                                tryAwaitRelease()
                            } finally {
                                if (!latestCompleted.value) {
                                    holding = false
                                    controller.onHoldRelease?.invoke()
                                    director?.onHoldRelease()
                                }
                            }
                        },
                        onTap = {
                            // A tap NEVER dismisses (spec §33) — it pokes.
                            if (latestCompleted.value) return@detectTapGestures
                            pokeToken++
                            controller.onPoke?.invoke()
                            director?.let { d ->
                                scope.launch { d.react(com.doombuddy.character.CharacterDirector.Reaction.JUMP) }
                            }
                        },
                    )
                },
        ) {
            MiniCharacter3D(
                definition = character,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    val pose = director?.pose ?: CharacterPose(alpha = 1f, accessory = state.accessory)
                    alpha = pose.alpha.coerceIn(0f, 1f)
                    scaleX = pose.scaleX
                    scaleY = pose.scaleY
                    translationX = pose.offsetX * size.width
                    translationY = pose.offsetY * size.height
                    rotationZ = pose.rotation
                },
                moodLevel = state.moodLevel,
                useLive3d = true,
                pokeToken = pokeToken,
                holding = holding && !completed,
                dismissing = completed || (director != null && !director.isShown),
                celebration = state.celebration || directorCelebrating,
                interactive = false,
            )
        }

        AnimatedVisibility(
            visible = holding && ringProgress > 0.08f && ringProgress < 1f,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (2 * scale).dp),
        ) {
            Text(
                "keep holding…",
                color = Color(0xFFC6F24E),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}
