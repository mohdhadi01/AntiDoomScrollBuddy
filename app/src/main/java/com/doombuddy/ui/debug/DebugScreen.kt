package com.doombuddy.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.doombuddy.character.CharacterRoster
import com.doombuddy.character.Entrance
import com.doombuddy.monitor.MonitorService
import com.doombuddy.monitor.UsageAccess
import com.doombuddy.ui.components.AnimatedCharacter
import com.doombuddy.overlay.BuddyTimerCard
import com.doombuddy.data.TimerMode
import androidx.compose.ui.platform.LocalContext

/**
 * Developer mode (spec §69/§70): full pipeline state panel + simulation of every
 * product event. The state panel exists to answer "why didn't the intervention show?".
 */
@Composable
fun DebugScreen(prefs: com.doombuddy.data.PrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var previewCharacter by remember { mutableStateOf(CharacterRoster.MELT) }
    var previewLevel by remember { mutableStateOf(2) }
    var previewEntrance by remember { mutableStateOf(Entrance.POP) }
    var previewKey by remember { mutableStateOf(0) }
    val fallbackState = remember { kotlinx.coroutines.flow.MutableStateFlow(MonitorService.DebugState()) }
    val debugState by (MonitorService.instance?.debugState ?: fallbackState).collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Developer tools", style = MaterialTheme.typography.titleLarge)
        }

        // ---- live pipeline state (spec §70) ----
        Section("Pipeline state") {
            StateRow("Foreground", debugState.foreground ?: "(none)")
            StateRow("Intervention", debugState.interventionState.name)
            StateRow(
                "Session",
                debugState.sessionPackage?.let {
                    "${it.substringAfterLast('.')} · ${formatMs(debugState.sessionElapsedMs)}" +
                        if (debugState.sessionPaused) " (paused)" else ""
                } ?: "(none)",
            )
            StateRow("Today (persisted)", formatMs(debugState.persistedTodayMs))
            StateRow("Today (live)", formatMs(debugState.liveTodayMs))
            StateRow("Last level", debugState.lastLevel.toString())
            StateRow("Overlay", if (debugState.overlayAttached) "attached" else "hidden")
            StateRow("Usage access", if (debugState.usageAccessGranted) "granted" else "MISSING")
            StateRow("Overlay perm", if (debugState.overlayPermissionGranted) "granted" else "MISSING")
            StateRow("Character", debugState.characterId)
            StateRow(
                "Ladder",
                debugState.thresholdsMs.joinToString(" ") { "${it / 60_000}m" }.ifEmpty { "?" },
            )
        }

        Section("Simulate") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { level ->
                    OutlinedButton(onClick = { MonitorService.instance?.debugTriggerLevel(level) }, modifier = Modifier.weight(1f)) {
                        Text("L$level")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { MonitorService.instance?.debugDismiss() }, modifier = Modifier.weight(1f)) {
                    Text("Dismiss (hold)")
                }
                OutlinedButton(onClick = { MonitorService.instance?.debugEndSession() }, modifier = Modifier.weight(1f)) {
                    Text("Exit app")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { MonitorService.instance?.debugAddFakeToday(30) }, modifier = Modifier.weight(1f)) {
                    Text("Today +30m")
                }
                OutlinedButton(onClick = { MonitorService.instance?.debugAddFakeToday(60) }, modifier = Modifier.weight(1f)) {
                    Text("Today +60m")
                }
            }
            if (debugState.usageAccessGranted.not() || debugState.overlayPermissionGranted.not()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    UsageAccess.openSettings(context)
                }) { Text("Fix permissions") }
            }
        }

        // ---- character preview studio ----
        Section("Preview studio") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CharacterRoster.ALL) { c ->
                    Card(
                        onClick = { previewCharacter = c; previewKey++ },
                        colors = CardDefaults.cardColors(
                            containerColor = if (c.id == previewCharacter.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) { Text(c.displayName, Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..6).forEach { level ->
                    OutlinedButton(onClick = { previewLevel = level; previewKey++ }, modifier = Modifier.weight(1f)) {
                        Text("$level", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Entrance.entries.forEach { e ->
                    OutlinedButton(onClick = { previewEntrance = e; previewKey++ }, modifier = Modifier.weight(1f)) {
                        Text(e.name.take(4).lowercase(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Production timer card preview: cycling Session ↔ Today.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BuddyTimerCard(
                        mode = TimerMode.CYCLE,
                        sessionMs = 5 * 60_000L + 12_000L,
                        todayMs = 107 * 60_000L,
                        cycleSeconds = 3,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center,
            ) {
                key(previewKey) {
                    AnimatedCharacter(
                        previewCharacter,
                        Modifier.size(220.dp),
                        entrance = previewEntrance,
                        moodLevel = previewLevel,
                    )
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun key(key: Any, content: @Composable () -> Unit) =
    androidx.compose.runtime.key(key) { content() }

private fun formatMs(ms: Long): String {
    val mins = ms / 60_000
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

@Composable
private fun StateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.contains("MISSING")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
