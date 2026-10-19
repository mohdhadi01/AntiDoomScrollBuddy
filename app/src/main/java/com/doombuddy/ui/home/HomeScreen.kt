package com.doombuddy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.doombuddy.character.CharacterRoster
import com.doombuddy.data.PrefsRepository
import com.doombuddy.data.StatsRepository
import com.doombuddy.ui.components.AnimatedCharacter
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    prefs: PrefsRepository,
    stats: StatsRepository,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val prefsState by prefs.preferences.collectAsState(initial = null)
    val usage by stats.todayUsage.collectAsState(initial = StatsRepository.UsageSnapshot(emptyList(), 0))
    val sessions by stats.sessionsToday.collectAsState(initial = emptyList())
    val opens by stats.opensToday.collectAsState(initial = emptyList())
    val reclaimed by stats.reclaimedToday.collectAsState(initial = null)
    val liveState by (com.doombuddy.monitor.MonitorService.instance?.debugState
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.doombuddy.monitor.MonitorService.DebugState()) })
        .collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val character = CharacterRoster.byId(prefsState?.characterId ?: "melt")
    val mood = dayMoodFor(usage.totalMs)
    val serviceAlive = com.doombuddy.monitor.MonitorService.instance != null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("DOOMBUDDY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Your day, reclaimed.", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surface, Color(0xFF20233A)),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).clip(CircleShape)
                                    .background(if (serviceAlive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (serviceAlive) "ON DUTY" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (serviceAlive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(reclaimedHeadline(reclaimed), style = MaterialTheme.typography.headlineLarge)
                        Text(
                            buddyLine(prefsState, usage.totalMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier.fillMaxWidth().height(168.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            AnimatedCharacter(
                                character,
                                Modifier.width(170.dp).height(165.dp),
                                entrance = com.doombuddy.character.Entrance.BOUNCE,
                                moodLevel = mood,
                                useLive3d = true,
                            )
                        }
                    }
                }
            }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SnapshotStat("SCROLL TIME", formatMinutes(usage.totalMs), MaterialTheme.colorScheme.secondary, Modifier.width(132.dp))
                }
                item {
                    SnapshotStat("RECLAIMED", formatMinutes(reclaimed ?: 0), MaterialTheme.colorScheme.primary, Modifier.width(132.dp))
                }
                item {
                    SnapshotStat("OPENS", opens.sumOf { it.second }.toString(), MaterialTheme.colorScheme.tertiary, Modifier.width(132.dp))
                }
            }
        }

        item {
            InsightCard(
                title = "Your scroll, in a glance",
                eyebrow = if (usage.perApp.isEmpty()) "READY WHEN YOU ARE" else "TOP TIME SINKS",
            ) {
                if (usage.perApp.isEmpty()) {
                    Text(
                        "Open one of your chosen apps and your buddy will start keeping count.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val maxMs = usage.perApp.maxOf { it.second }.coerceAtLeast(1)
                    usage.perApp.take(4).forEachIndexed { index, (label, ms) ->
                        AppUsageRow(label, ms, maxMs, index)
                        if (index < usage.perApp.take(4).lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            InsightCard(title = "Sessions", eyebrow = "LIVE FEED") {
                val inProgress = liveState.sessionPackage
                if (inProgress != null) {
                    LiveSessionRow(
                        appName = appLabelFor(inProgress),
                        startEpochMs = liveState.sessionStartWallMs,
                        elapsedMs = liveState.sessionElapsedMs,
                    )
                    if (sessions.isNotEmpty()) Spacer(Modifier.height(14.dp))
                }
                if (sessions.isEmpty() && inProgress == null) {
                    Text("No sessions yet today. A clean slate.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    sessions.take(6).forEachIndexed { index, entry ->
                        SessionRow(entry)
                        if (index < sessions.take(6).lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        item {
            if (!serviceAlive && prefsState?.onboardingDone == true) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Buddy is taking five", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Counting is paused until you wake it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Button(onClick = {
                            com.doombuddy.monitor.MonitorService.start(context)
                            com.doombuddy.monitor.WatchdogReceiver.scheduleWatchdog(context)
                        }) { Text("Wake") }
                    }
                }
            }
            if (prefsState?.developerMode == true) {
                TextButton(onClick = onOpenDebug, modifier = Modifier.fillMaxWidth()) { Text("Developer tools") }
            }
        }
    }
}

@Composable
private fun SnapshotStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
    }
}

@Composable
private fun InsightCard(title: String, eyebrow: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AppUsageRow(label: String, ms: Long, maxMs: Long, index: Int) {
    val tint = when (index % 3) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(formatMinutes(ms), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth((ms.toFloat() / maxMs).coerceIn(0.04f, 1f)).height(7.dp)
                    .clip(RoundedCornerShape(4.dp)).background(tint),
            )
        }
    }
}

internal fun dayMoodFor(todayMs: Long): Int = when {
    todayMs < TimeUnit.MINUTES.toMillis(5) -> 0
    todayMs < TimeUnit.MINUTES.toMillis(30) -> 1
    todayMs < TimeUnit.MINUTES.toMillis(75) -> 2
    todayMs < TimeUnit.MINUTES.toMillis(150) -> 3
    else -> 4
}

private fun reclaimedHeadline(reclaimedMs: Long?): String = when {
    reclaimedMs == null -> "Let's make a little room."
    reclaimedMs < TimeUnit.MINUTES.toMillis(1) -> "You're on pace."
    else -> {
        val mins = TimeUnit.MILLISECONDS.toMinutes(reclaimedMs)
        if (mins >= 60) "${mins / 60}h ${mins % 60}m reclaimed" else "$mins min reclaimed"
    }
}

private fun formatMinutes(ms: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

private fun buddyLine(prefs: com.doombuddy.data.UserPreferences?, todayMs: Long): String {
    if (prefs == null) return "Your buddy is warming up."
    val character = CharacterRoster.byId(prefs.characterId)
    val moodWord = when (dayMoodFor(todayMs)) {
        0 -> "is fresh and ready to roll"
        1 -> "is keeping a soft eye on things"
        2 -> "made popcorn. just saying."
        3 -> "brought a blanket. just in case."
        else -> "has fully moved in at this point"
    }
    return "${character.displayName} $moodWord."
}

@Composable
private fun SessionRow(entry: StatsRepository.SessionEntry) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.appName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(formatMinutes(entry.durationMs), style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(entry.startEpochMs)} – ${formatClock(entry.endEpochMs)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (entry.interventions > 0) {
                Spacer(Modifier.width(8.dp))
                Text("· ${entry.interventions} visit" + if (entry.interventions > 1) "s" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveSessionRow(appName: String, startEpochMs: Long, elapsedMs: Long) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(appName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(formatMinutes(elapsedMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("in progress" + if (startEpochMs > 0) " · since ${formatClock(startEpochMs)}" else "", Modifier.padding(start = 15.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatClock(epochMs: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(epochMs))

private fun appLabelFor(packageName: String): String =
    com.doombuddy.ui.onboarding.KnownApps.list.firstOrNull { packageName in it.packages }?.label
        ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
