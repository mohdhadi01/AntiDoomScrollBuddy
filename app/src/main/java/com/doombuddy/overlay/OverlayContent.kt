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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 *   [ one connected speech bubble: line + live/session/today status ]
 *   [ character visually tucked under the bubble tail ]
 *
 * The stage is transparent, but the bubble is deliberately one connected visual unit:
 * the buddy looks like it is speaking, and timing is part of the same little scene rather
 * than a second floating HUD. The window remains a stable measured stage so animation
 * cannot resize or clip the overlay.
 */
@Composable
fun OverlayRoot(controller: OverlayController) {
    val state = controller.state
    val character = state.character ?: return
    val director = controller.director
    val scope = rememberCoroutineScope()
    val u = state.uiScale.coerceIn(0.82f, 1.06f)
    val stageWidth = (224 * u).dp
    val stageHeight = (252 * u).dp
    // Use the brighter body tone for the speech treatment so every character keeps
    // a readable, high-energy accent (Fenn's brown accent is intentionally darker).
    val bubbleAccent = character.palette.bodyTop.copy(alpha = 0.98f)
    val speechLine = overlaySpeechLine(state.dialogueLine, character.displayName)

    Box(
        modifier = Modifier
            .width(stageWidth)
            .height(stageHeight),
    ) {
        SpeechBubble(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width((204 * u).dp)
                .height((126 * u).dp),
            accent = bubbleAccent,
            scale = u,
            line = speechLine,
            timerMode = state.timerMode,
            sessionMs = state.sessionMs,
            todayMs = state.todayMs,
            cycleSeconds = state.cycleSeconds,
            onCycleFlip = { director?.lookAtTimer() },
        )

        HoldableBuddy(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (2 * u).dp),
            state = state,
            director = director,
            controller = controller,
            scope = scope,
            scale = u,
        )
    }
}

@Composable
private fun SpeechBubble(
    modifier: Modifier,
    accent: Color,
    scale: Float,
    line: String,
    timerMode: TimerMode,
    sessionMs: Long,
    todayMs: Long,
    cycleSeconds: Int,
    onCycleFlip: () -> Unit,
) {
    val shape = RoundedCornerShape((26 * scale).dp)
    Box(
        modifier = modifier
            .background(Color(0xE91C2330), shape)
            .border((1.5f * scale).dp, accent.copy(alpha = 0.8f), shape)
            .drawBehind {
                val tailCenter = size.width * 0.5f
                val tailTop = size.height - 2.dp.toPx()
                val tail = Path().apply {
                    moveTo(tailCenter - 14.dp.toPx(), tailTop)
                    lineTo(tailCenter, tailTop + 18.dp.toPx())
                    lineTo(tailCenter + 14.dp.toPx(), tailTop)
                    close()
                }
                drawPath(tail, Color(0xE91C2330))
                drawPath(tail, accent.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f.dp.toPx()))
            }
            .padding(horizontal = (16 * scale).dp, vertical = (12 * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = line,
                transitionSpec = {
                    (slideInVertically(tween(240)) { it / 2 } + fadeIn(tween(240))) togetherWith
                        (slideOutVertically(tween(170)) { -it / 2 } + fadeOut(tween(130)))
                },
                label = "buddyDialogue",
            ) { currentLine ->
                Text(
                    text = currentLine,
                    color = accent,
                    fontSize = (19 * scale).sp,
                    lineHeight = (20 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Cursive,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    style = bubbleTextStyle,
                )
            }
            Spacer(Modifier.height((8 * scale).dp))
            BuddyTimerCard(
                mode = timerMode,
                sessionMs = sessionMs,
                todayMs = todayMs,
                cycleSeconds = cycleSeconds,
                scale = scale,
                accent = accent,
                onCycleFlip = onCycleFlip,
            )
            Spacer(Modifier.height((4 * scale).dp))
            Text(
                text = "tap me  ·  hold to bounce",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = (8 * scale).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                style = whisperTextStyle,
            )
        }
    }
}

internal fun overlaySpeechLine(dialogueLine: String?, characterName: String): String =
    dialogueLine?.takeIf { it.isNotBlank() } ?: when (characterName.lowercase()) {
        "goop" -> "still scrolling? be so for real."
        "buffer" -> "your feed is buffering your life."
        "loui" -> "this scroll could've been an email."
        "fenn" -> "one more scroll? iconic."
        else -> "still here?"
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
    accent: Color = sessionAccent,
    onCycleFlip: () -> Unit = {},
) {
    val ts = (15 * scale).sp
    when (mode) {
        TimerMode.SESSION_ONLY ->
            TimerView("LIVE", formatSession(sessionMs), accent, valueSize = ts)
        TimerMode.STACKED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            TimerView("LIVE", formatSession(sessionMs), accent, valueSize = ts)
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
                TimerView("LIVE", formatSession(sessionMs), accent, valueSize = ts)
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
                        if (today) accent else Color.White.copy(alpha = 0.72f),
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

private val bubbleTextStyle = TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 2f),
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
