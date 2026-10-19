package com.doombuddy.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doombuddy.character.CharacterPose
import com.doombuddy.character.characterDraw
import com.doombuddy.data.TimerMode
import com.doombuddy.ui.components.MiniCharacter3D
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Root of the persistent intervention overlay (spec §42):
 *
 *   [ live badge + app-independent session/today timer ]
 *   [ timer pod + measured character stage ]
 *   [ short line + hold affordance ]
 *
 * The window is one fixed-size measured card. The old widget stacked independently
 * animated speech, timer, and character columns, so its WRAP_CONTENT bounds changed
 * while acting and clipped the character. This composition keeps all renderable pixels
 * inside a stable compact 224dp x 146dp stage; touches outside the character still pass through.
 */
@Composable
fun OverlayRoot(controller: OverlayController) {
    val state = controller.state
    val character = state.character ?: return
    val director = controller.director
    val scope = rememberCoroutineScope()
    val u = state.uiScale.coerceIn(0.82f, 1.06f)
    val cardWidth = (224 * u).dp
    val cardHeight = (146 * u).dp

    Column(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape((22 * u).dp))
            .background(Color(0xC90D141B))
            .border(1.dp, Color(0xFFB7C6D8).copy(alpha = 0.12f), RoundedCornerShape((22 * u).dp))
            .padding((9 * u).dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size((6 * u).dp).clip(CircleShape).background(Color(0xFFB9E86B)))
            Spacer(Modifier.width((5 * u).dp))
            Text("LIVE", color = Color(0xFFB9E86B), fontSize = (8 * u).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            Text("hold", color = Color(0xFF8291A3), fontSize = (8 * u).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }

        Spacer(Modifier.height((3 * u).dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BuddyTimerCard(
                mode = state.timerMode,
                sessionMs = state.sessionMs,
                todayMs = state.todayMs,
                cycleSeconds = state.cycleSeconds,
                scale = u,
                onCycleFlip = { director?.lookAtTimer() },
            )
            HoldableBuddy(
                state = state,
                director = director,
                controller = controller,
                scope = scope,
                scale = u,
            )
        }

        Spacer(Modifier.height((2 * u).dp))
        Text(
            state.dialogueLine ?: "tap for a reaction · hold to leave",
            modifier = Modifier.fillMaxWidth(),
            color = if (state.dialogueLine != null) Color(0xFFF2F5F7) else Color(0xFF8291A3),
            fontSize = (9 * u).sp,
            lineHeight = (11 * u).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- timer card

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
    val ts = (16 * scale).sp
    when (mode) {
        TimerMode.SESSION_ONLY -> TimerView("SESSION", formatSession(sessionMs), sessionAccent, valueSize = ts)
        TimerMode.STACKED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimerView("SESSION", formatSession(sessionMs), sessionAccent, valueSize = ts)
            Spacer(Modifier.height(4.dp))
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
            AnimatedContent(
                targetState = showToday,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (slideInHorizontally(tween(300)) { it * dir } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it * dir } + fadeOut(tween(200)))
                },
                label = "timerCycle",
            ) { today ->
                if (today) {
                    TimerView("TODAY", formatToday(todayMs), todayAccent, valueSize = ts)
                } else {
                    TimerView("SESSION", formatSession(sessionMs), sessionAccent, valueSize = ts)
                }
            }
        }
    }
}

@Composable
private fun TimerView(label: String, value: String, accent: Color, showLabels: Boolean = true, valueSize: androidx.compose.ui.unit.TextUnit = 19.sp) {
    Box(
        modifier = Modifier
            .widthIn(min = 86.dp, max = 98.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB8141B24))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showLabels) {
                Text(
                    text = label,
                    color = Color(0xFF8B96A5),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(1.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = value,
                    color = accent,
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

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
    val buddySize = (82 * scale).dp
    val ringSize = (90 * scale).dp
    val boxW = (98 * scale).dp
    val boxH = (88 * scale).dp
    var holding by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
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
            .size(width = boxW, height = boxH),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Hold progress ring around the buddy — the dismissal affordance (shared component).
        com.doombuddy.ui.components.HoldRing(
            progress = ringProgress,
            size = ringSize,
            modifier = Modifier.align(Alignment.Center),
        )

        // The buddy itself; whole area is the hold target. The pose lambda has a
        // static fallback so the character ALWAYS renders even if the animation
        // loop hasn't ticked yet (or is wedged) — never an invisible buddy.
        Box(
            modifier = Modifier
                .size(buddySize)
                .then(
                    if (character.modelAsset == null) {
                        Modifier.characterDraw(character) {
                            director?.pose?.takeIf { it.alpha > 0.02f }
                                ?: CharacterPose(alpha = 1f, accessory = state.accessory)
                        }
                    } else Modifier
                )
                .pointerInput(state.holdDurationMs) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            completed = false
                            controller.onHoldStart?.invoke()
                            director?.onHoldStart()
                            try {
                                awaitRelease()
                            } catch (_: Exception) {
                                // dragged away — treat as early release
                            }
                            if (!latestCompleted.value) {
                                holding = false
                                controller.onHoldRelease?.invoke()
                                director?.onHoldRelease()
                            }
                        },
                        onTap = {
                            // A tap NEVER dismisses (spec §33) — it pokes.
                            controller.onPoke?.invoke()
                            director?.let { d ->
                                scope.launch { d.react(com.doombuddy.character.CharacterDirector.Reaction.JUMP) }
                            }
                        },
                    )
                },
        )
        if (character.modelAsset != null) {
            MiniCharacter3D(character, Modifier.matchParentSize(), state.moodLevel)
        }

        AnimatedVisibility(
            visible = holding && ringProgress > 0.08f && ringProgress < 1f,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
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
