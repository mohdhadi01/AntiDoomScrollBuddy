package com.doombuddy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.doombuddy.character.CharacterPerformance
import com.doombuddy.character.CharacterRoster
import kotlinx.coroutines.delay

private val previewPerformances = listOf(
    CharacterPerformance.NOTICE, CharacterPerformance.WAITING, CharacterPerformance.ESCALATE,
    CharacterPerformance.MELTDOWN, CharacterPerformance.CELEBRATE,
)

private fun CharacterPerformance.previewLabel() = when (this) {
    CharacterPerformance.NOTICE -> "Hello"
    CharacterPerformance.WAITING -> "Still here?"
    CharacterPerformance.ESCALATE -> "Too long"
    CharacterPerformance.MELTDOWN -> "Meltdown"
    CharacterPerformance.CELEBRATE -> "We escaped!"
    else -> clipName
}

private fun buddyRole(id: String) = when (id) {
    "loui" -> "The intern"
    "buffer" -> "The robot"
    "goop" -> "The drama"
    else -> "The fox"
}

private fun performanceCaption(id: String, performance: CharacterPerformance?): String {
    if (id == "melt") return "Fenn keeps his original walk, tail-wag and look-around clips. The new cast has full comedy performances."
    return when (performance) {
        null -> "Short acts, quiet gaps. Real interventions follow your scrolling thresholds."
        CharacterPerformance.NOTICE -> "A little wave before the situation gets ridiculous."
        CharacterPerformance.WAITING -> when (id) {
            "loui" -> "Coffee break. Apparently we're going to be here a while."
            "buffer" -> "Loading a reason to keep scrolling…"
            else -> "An impatient little bounce. Is there an ending?"
        }
        CharacterPerformance.ESCALATE -> when (id) {
            "loui" -> "The meeting report just keeps getting longer."
            "buffer" -> "Your receipt is longer than the robot."
            else -> "Goop formally submits a tiny white flag."
        }
        CharacterPerformance.MELTDOWN -> when (id) {
            "loui" -> "The intern has clocked out emotionally."
            "buffer" -> "Rebooting expectations. Please stand by."
            else -> "Goop has entered the puddle era."
        }
        CharacterPerformance.CELEBRATE -> "You left the scroll. Finally, a good plot twist."
        else -> "Tap a performance again to replay it."
    }
}

/** Same live performance sandbox in onboarding and Settings; only ONE native stage. */
@Composable
fun CharacterStudio(selectedId: String, onSelect: (String) -> Unit) {
    val character = CharacterRoster.byId(selectedId)
    var performance by remember(character.id) { mutableStateOf<CharacterPerformance?>(null) }
    var previewToken by remember(character.id) { mutableStateOf(0L) }
    var pokeToken by remember(character.id) { mutableStateOf(0L) }
    var holding by remember(character.id) { mutableStateOf(false) }
    var escaped by remember(character.id) { mutableStateOf(false) }
    val latestEscaped by rememberUpdatedState(escaped)
    LaunchedEffect(character.id, performance, previewToken) {
        if (performance != null) {
            delay(3200)
            performance = null // never leave the selected buddy frozen indefinitely
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(if (holding) 1200 else 130, easing = LinearEasing),
        label = "demo hold progress",
        finishedListener = { if (it >= 1f && holding) escaped = true },
    )
    LaunchedEffect(escaped) {
        if (escaped) {
            delay(1500)
            holding = false
            escaped = false
            performance = null
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(212.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(character.palette.bodyTop.copy(alpha = .18f), MaterialTheme.colorScheme.surface))),
            contentAlignment = Alignment.Center,
        ) {
            HoldRing(progress = progress, size = 190.dp)
            MiniCharacter3D(
                character,
                modifier = Modifier.size(210.dp).semantics {
                    contentDescription = "${character.displayName} live preview. Tap for a reaction; hold to practise dismissal."
                }.pointerInput(character.id) {
                    detectTapGestures(
                        onPress = {
                            if (!latestEscaped) {
                                holding = true
                                try { tryAwaitRelease() } finally { holding = false }
                            }
                        },
                        onTap = { if (!latestEscaped) pokeToken++ },
                    )
                },
                moodLevel = 2,
                useLive3d = true,
                performance = performance,
                pokeToken = pokeToken,
                holding = holding && !escaped,
                dismissing = escaped,
                interactive = false,
                previewToken = previewToken,
            )
            Text(
                if (escaped) "Escaped. The real session keeps counting." else "TAP TO POKE · HOLD TO ESCAPE",
                Modifier.align(Alignment.BottomCenter).padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(character.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(character.tagline, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("TRY A PERFORMANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChip(selected = performance == null, onClick = { performance = null; previewToken++ }, label = { Text("Auto") })
            }
            items(previewPerformances) { act ->
                FilterChip(
                    selected = performance == act,
                    onClick = { performance = act; previewToken++; escaped = false },
                    label = { Text(act.previewLabel()) },
                )
            }
        }
        Text(
            performanceCaption(character.id, performance),
            Modifier.fillMaxWidth().heightIn(min = 40.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        // All four choices are visible without discovering a horizontal swipe.
        CharacterRoster.ALL.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { buddy ->
                    val chosen = buddy.id == character.id
                    Card(
                        onClick = { onSelect(buddy.id) }, modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniCharacterPortrait(buddy, Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(buddy.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(if (chosen) "Selected ✓" else buddyRole(buddy.id), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
