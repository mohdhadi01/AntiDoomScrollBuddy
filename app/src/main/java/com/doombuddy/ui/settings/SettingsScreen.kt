package com.doombuddy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doombuddy.character.CharacterRoster
import com.doombuddy.data.PrefsRepository
import com.doombuddy.data.TimerMode
import com.doombuddy.monitor.MonitorService
import com.doombuddy.session.Intensity
import com.doombuddy.ui.components.AnimatedCharacter
import com.doombuddy.ui.components.CharacterStudio
import com.doombuddy.ui.onboarding.KnownApps
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: PrefsRepository, onBack: () -> Unit) {
    val state by prefs.preferences.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state ?: return

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←  Back") }
            Spacer(Modifier.weight(1f))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(18.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Make it yours.", style = MaterialTheme.typography.headlineMedium)
            Text("Small tweaks, same gentle nudge.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))

            SettingsCard("YOUR BUDDY", "Choose the little personality that follows your scroll.") {
                CharacterStudio(s.characterId) { id -> scope.launch { prefs.setCharacter(id) } }
            }

            SettingsCard("HOW MUCH BUDDY?", "Set the energy, then choose when the first check-in lands.") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(Intensity.entries, key = { it.name }) { level ->
                        val chosen = level == s.intensity
                        Card(
                            onClick = { scope.launch { prefs.setIntensity(level) } },
                            modifier = Modifier.width(104.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                level.name.lowercase().replaceFirstChar { it.uppercase() },
                                Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("First check-in", style = MaterialTheme.typography.bodyLarge)
                        Text("after ${s.firstThresholdMinutes} minutes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${s.firstThresholdMinutes}m", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = s.firstThresholdMinutes.toFloat(),
                    onValueChange = { scope.launch { prefs.setFirstThreshold(it.toInt()) } },
                    valueRange = 1f..15f,
                    steps = 13,
                )
            }

            SettingsCard("TIME ON SCREEN", "Pick the timer layout and the hold-to-dismiss feel.") {
                Text("Timer layout", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(TimerMode.entries, key = { it.name }) { mode ->
                        val chosen = mode == s.timerMode
                        Card(
                            onClick = { scope.launch { prefs.setTimerMode(mode) } },
                            modifier = Modifier.width(112.dp),
                            colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                when (mode) { TimerMode.SESSION_ONLY -> "Session"; TimerMode.CYCLE -> "Cycle"; TimerMode.STACKED -> "Both" },
                                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (s.timerMode == TimerMode.CYCLE) {
                    Spacer(Modifier.height(12.dp))
                    Text("Cycle speed: ${s.timerCycleSeconds}s", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = s.timerCycleSeconds.toFloat(), onValueChange = { scope.launch { prefs.setTimerCycle(it.toInt()) } }, valueRange = 2f..8f, steps = 5)
                }
                Text("Hold to dismiss: ${"%.1f".format(s.holdDurationMs / 1000f)}s", style = MaterialTheme.typography.bodyMedium)
                Slider(value = s.holdDurationMs.toFloat(), onValueChange = { scope.launch { prefs.setHoldDuration(it.toInt()) } }, valueRange = 800f..2500f, steps = 6)
                Text("A deliberate hold is required. Your session keeps counting after dismissal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCard("WHERE IT LIVES", "Drag the preview to choose a calm corner of your screen.") {
                var dragX by remember(s.overlayXFrac) { mutableStateOf(s.overlayXFrac) }
                var dragY by remember(s.overlayYFrac) { mutableStateOf(s.overlayYFrac) }
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> dragX = (offset.x / size.width).coerceIn(0.08f, 0.92f); dragY = (offset.y / size.height).coerceIn(0.12f, 0.88f) },
                            onDrag = { change, _ -> change.consume(); dragX = (change.position.x / size.width).coerceIn(0.08f, 0.92f); dragY = (change.position.y / size.height).coerceIn(0.12f, 0.88f) },
                            onDragEnd = { scope.launch { prefs.setOverlayPosition(dragX, dragY) } },
                        )
                    },
                ) {
                    Text("DRAG TO PLACE", Modifier.align(Alignment.TopCenter).padding(10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val offX = maxWidth * (dragX - 0.5f)
                    val offY = maxHeight * (dragY - 0.5f)
                    AnimatedCharacter(
                        CharacterRoster.byId(s.characterId),
                        Modifier.align(Alignment.Center).offset(offX, offY).width(70.dp).height(62.dp),
                        entrance = com.doombuddy.character.Entrance.POP,
                        interactive = false,
                        animated = false,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Buddy size: ${s.overlayScalePct}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = s.overlayScalePct.toFloat(), onValueChange = { scope.launch { prefs.setOverlayScale(it.toInt()) } }, valueRange = 70f..160f)
            }

            SettingsCard("TRACKING", "Only these apps count toward your buddy's day.") {
                KnownApps.list.forEach { app ->
                    val checked = app.packages.any { it in s.targetApps }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Checkbox(checked = checked, onCheckedChange = { now ->
                            val newSet = if (now) s.targetApps + app.packages.toSet() else s.targetApps - app.packages.toSet()
                            scope.launch { prefs.setTargetApps(newSet) }
                        })
                    }
                }
            }

            SettingsCard("QUIET + FEEL", "Keep the nudge gentle, especially when the day is done.") {
                SwitchRow("Quiet hours · 23:30 → 07:00", s.quietHours.enabled) { scope.launch { prefs.setQuietHours(s.quietHours.copy(enabled = it)) } }
                Spacer(Modifier.height(4.dp))
                SwitchRow("Sound", s.soundEnabled) { scope.launch { prefs.setSound(it) } }
                SwitchRow("Haptics", s.hapticsEnabled) { scope.launch { prefs.setHaptics(it) } }
            }

            SettingsCard("BUDDY ON DUTY", "The service quietly counts in the background.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (MonitorService.instance != null) "Watching" else "Paused", style = MaterialTheme.typography.bodyLarge)
                        Text("Hide the notification anytime.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = MonitorService.instance != null, onCheckedChange = { on -> if (on) MonitorService.start(context) else MonitorService.stop(context) })
                }
            }

            SwitchRow("Developer mode", s.developerMode, Modifier.padding(vertical = 4.dp)) { scope.launch { prefs.setDeveloperMode(it) } }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
