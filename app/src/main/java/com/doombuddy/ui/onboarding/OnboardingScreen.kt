package com.doombuddy.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.doombuddy.character.CharacterRoster
import com.doombuddy.data.PrefsRepository
import com.doombuddy.data.defaultTargetApps
import com.doombuddy.monitor.MonitorService
import com.doombuddy.session.Intensity
import com.doombuddy.ui.components.AnimatedCharacter
import com.doombuddy.ui.onboarding.KnownApps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val STEPS = 8

/** The launch trio: every new buddy can be picked here without a hidden roster. */
private val LaunchCharacters = CharacterRoster.ALL

@Composable
fun OnboardingScreen(prefs: PrefsRepository, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    fun next() { if (step < STEPS - 1) step++ else onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / STEPS },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // clip = false: steps intentionally host characters whose entrance/jump poses
                // overflow their own layout bounds (bounce-in, props held overhead, etc). The
                // default SizeTransform clips to the animated container size, which was
                // chopping the buddy mid-body during step transitions — never disable this
                // without re-checking the character preview on every onboarding step.
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(200))) using
                    SizeTransform(clip = false)
            },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { current ->
            when (current) {
                0 -> IntroStep { next() }
                1 -> WhyStep { next() }
                2 -> CharacterStep(prefs) { next() }
                3 -> AppsStep(prefs) { next() }
                4 -> StyleStep(prefs) { next() }
                5 -> PermissionsStep { next() }
                6 -> TestStep(prefs) { next() }
                else -> DoneStep(prefs, onDone)
            }
        }
    }
}

@Composable
private fun StepScaffold(title: String, subtitle: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(22.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
        Spacer(Modifier.height(18.dp))
    }
}

// --- Step 0: intro ---------------------------------------------------------------

@Composable
private fun IntroStep(onNext: () -> Unit) {
    StepScaffold(title = "Meet your new scroll buddy", subtitle = "A tiny, opinionated friend for the moments when one more video becomes forty.") {
        Card(
            Modifier.fillMaxWidth().heightIn(min = 240.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AWARENESS, WITH PERSONALITY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    AnimatedCharacter(CharacterRoster.MELT, Modifier.width(220.dp).height(220.dp), entrance = com.doombuddy.character.Entrance.POP, useLive3d = true)
                    Text("No guilt trips. Just a little nudge.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
            Text("OK, show me", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Step 1: why -----------------------------------------------------------------

@Composable
private fun WhyStep(onNext: () -> Unit) {
    StepScaffold(title = "Awareness, not guilt", subtitle = "No streaks, no shame, no charts. Just a friend tapping your shoulder at the right second.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WhyCard("It notices", "You open Instagram, YouTube, TikTok — your buddy quietly starts counting.")
            WhyCard("It performs", "At your limit the buddy shows up — peeking, snacking, judging, napping. It acts out your time. Your pick.")
            WhyCard("It never blocks you", "Tiny corner, no touch-stealing, scroll right past. It just wants you to notice.")
            WhyCard("It keeps numbers, nothing else", "Only which of your chosen apps were open, and for how long. That's the whole database.")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Pick my buddy")
        }
    }
}

@Composable
private fun WhyCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(17.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Step 2: character picker (the fun one) --------------------------------------

@Composable
private fun CharacterStep(prefs: PrefsRepository, onNext: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val selected by prefs.preferences.collectAsState(initial = null)
    var chosenId by remember { mutableStateOf<String?>(null) }
    val characterId = chosenId ?: selected?.characterId ?: "melt"
    val character = CharacterRoster.byId(characterId)

    StepScaffold(title = "Pick your buddy", subtitle = "Meet Fenn, the animated fox who interrupts your scroll.") {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 360.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoldablePreviewBuddy(
                    character = character,
                    selectedId = characterId,
                    onSelect = {
                        chosenId = it
                        scope.launch { prefs.setCharacter(it) }
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(character.displayName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("“${character.tagline}”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "tap to choose · hold to try the escape",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                // The launch roster is intentionally small and character-first.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(LaunchCharacters) { c ->
                        val isChosen = c.id == characterId
                        Card(
                            onClick = {
                                chosenId = c.id
                                scope.launch { prefs.setCharacter(c.id) }
                            },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(10.dp),
                            ) {
                                AnimatedCharacter(
                                    c,
                                    Modifier.width(76.dp).height(66.dp),
                                    animated = false,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    c.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("This one. Definitely this one.")
        }
    }
}

/**
 * The picker IS the product demo (spec §21): the buddy with its hold ring. Tapping
 * previews the character; completing a hold triggers the same escape the real
 * overlay performs (character reacts, ring completes).
 */
@Composable
private fun HoldablePreviewBuddy(
    character: com.doombuddy.character.CharacterDefinition,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var holding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var escaped by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val ringProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) {
            androidx.compose.animation.core.tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearEasing)
        } else {
            androidx.compose.animation.core.tween(durationMillis = 130)
        },
        finishedListener = { if (it >= 1f && holding) escaped = true },
    )

    Box(contentAlignment = Alignment.Center) {
        com.doombuddy.ui.components.HoldRing(progress = ringProgress, modifier = Modifier.matchParentSize())
        AnimatedCharacter(
            definition = character,
            modifier = Modifier
                .width(200.dp)
                .height(180.dp)
                .pointerInput(character.id) {
                    detectTapGestures(
                        onPress = {
                            holding = true
                            try { awaitRelease() } catch (_: Exception) {}
                            if (ringProgress < 1f) holding = false
                        },
                        onTap = {
                            if (selectedId != character.id) onSelect(character.id)
                        },
                    )
            },
            entrance = com.doombuddy.character.Entrance.PORTAL,
            moodLevel = if (selectedId == character.id) 2 else 0,
            useLive3d = true,
        )
        if (escaped) {
            Text(
                "escaped ✓  (in the real app, the buddy leaves — the timer keeps running)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    // reset the escape demo shortly after it completes
    androidx.compose.runtime.LaunchedEffect(escaped) {
        if (escaped) {
            kotlinx.coroutines.delay(2200)
            escaped = false
        }
    }
}

// --- Step 3: target apps ----------------------------------------------------------

@Composable
private fun AppsStep(prefs: PrefsRepository, onNext: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefsState by prefs.preferences.collectAsState(initial = null)
    val selected = prefsState?.targetApps ?: defaultTargetApps()

    StepScaffold(title = "Where should your buddy look?", subtitle = "Pick the apps that swallow your time. Only these are ever monitored.") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KnownApps.list.forEach { app ->
                val checked = app.packages.any { it in selected }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                app.packages.firstOrNull() ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { now ->
                                val newSet = if (now) selected + app.packages.toSet() else selected - app.packages.toSet()
                                scope.launch { prefs.setTargetApps(newSet) }
                            },
                        )
                    }
                }
            }
            Text(
                "More apps can be added anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Looks right")
        }
    }
}

// --- Step 4: style ---------------------------------------------------------------

@Composable
private fun StyleStep(prefs: PrefsRepository, onNext: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefsState by prefs.preferences.collectAsState(initial = null)
    var intensity by remember(prefsState) { mutableStateOf(prefsState?.intensity ?: Intensity.PLAYFUL) }
    var threshold by remember(prefsState) { mutableIntStateOf(prefsState?.firstThresholdMinutes ?: 5) }

    fun save() {
        scope.launch {
            prefs.setIntensity(intensity)
            prefs.setFirstThreshold(threshold)
        }
    }

    StepScaffold(title = "How much buddy?", subtitle = "Chill = barely there. Chaos = expect the ridiculous.") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(Intensity.entries, key = { it.name }) { level ->
                    val isChosen = level == intensity
                    Card(
                        onClick = { intensity = level },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.width(104.dp),
                    ) {
                        Text(
                            level.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isChosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // LIVE PREVIEW: this is what the chosen style's intervention looks like
            // (spec §62 — the user should SEE the style, not read about it).
            val previewCharacter = CharacterRoster.byId(prefsState?.characterId ?: "melt")
            val sampleLine = remember(intensity, previewCharacter.id) {
                previewCharacter.dialogue.forLevel(if (intensity == Intensity.CHILL) 1 else 2).first()
            }
            val previewEntrance = when (intensity) {
                Intensity.CHILL -> com.doombuddy.character.Entrance.PEEK_IN
                Intensity.PLAYFUL -> com.doombuddy.character.Entrance.POP
                Intensity.PERSISTENT -> com.doombuddy.character.Entrance.BOUNCE
                Intensity.CHAOS -> com.doombuddy.character.Entrance.FALL
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    key(previewEntrance) {
                        AnimatedCharacter(
                            previewCharacter,
                            Modifier.width(150.dp).height(130.dp),
                            entrance = previewEntrance,
                            moodLevel = if (intensity == Intensity.CHAOS) 4 else 2,
                            useLive3d = true,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    com.doombuddy.overlay.BuddyTimerCard(
                        mode = com.doombuddy.data.TimerMode.CYCLE,
                        sessionMs = threshold * 60_000L,
                        todayMs = 107 * 60_000L,
                        cycleSeconds = if (intensity == Intensity.CHAOS) 2 else 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "“$sampleLine”",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (intensity) {
                            Intensity.CHILL -> "peeks in quietly · escalates slowly · easy to ignore"
                            Intensity.PLAYFUL -> "pops in with a line · escalates steadily · the classic"
                            Intensity.PERSISTENT -> "shows up sooner · escalates faster · harder to dismiss"
                            Intensity.CHAOS -> "falls in · frequent · ridiculous · you asked for this"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "first check-in at ${threshold}m · buddy stays until you hold-to-escape or leave",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("First check-in", style = MaterialTheme.typography.titleMedium)
                    Text("Your buddy appears after this many minutes of scrolling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.material3.Slider(
                            value = threshold.toFloat(),
                            onValueChange = { threshold = it.toInt() },
                            valueRange = 1f..15f,
                            steps = 13,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${threshold}m", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { save(); onNext() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Save style")
        }
    }
}

// --- Step 5: permissions ----------------------------------------------------------

data class PermissionUiState(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val batteryExempt: Boolean = false,
)

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionUiState(false, false, false)) }

    fun refresh() {
        state = PermissionUiState(
            usageAccess = com.doombuddy.monitor.UsageAccess.isGranted(context),
            overlay = Settings.canDrawOverlays(context),
            notifications = if (Build.VERSION.SDK_INT >= 33) {
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true,
            batteryExempt = com.doombuddy.monitor.BatteryOptimization.isIgnoring(context),
        )
    }

    LaunchedEffect(Unit) { refresh() }
    val notifier = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    // Re-check when returning from Settings screens.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    StepScaffold(title = "Two switches, honestly explained", subtitle = "DoomBuddy works only with your permission — and tells you exactly what each one does.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionRow(
                title = "Usage access",
                why = "Lets your buddy know when a chosen app is open, so it knows when a session starts.",
                granted = state.usageAccess,
                buttonLabel = "Open Settings",
                action = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
            PermissionRow(
                title = "Display over other apps",
                why = "Lets the buddy appear on top of Instagram while you scroll. Nothing else.",
                granted = state.overlay,
                buttonLabel = "Open Settings",
                action = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
            PermissionRow(
                title = "Run in the background",
                why = "Stops Android from putting your buddy to sleep, so it can keep counting while you're inside Instagram.",
                granted = state.batteryExempt,
                buttonLabel = "Allow",
                action = { com.doombuddy.monitor.BatteryOptimization.requestExemption(context) },
            )
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow(
                    title = "Notifications",
                    why = "The quiet 'Buddy is on duty' status notification. Can be hidden — the buddy works anyway.",
                    granted = state.notifications,
                    buttonLabel = "Allow",
                    action = { notifier.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            enabled = state.usageAccess && state.overlay,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (state.usageAccess && state.overlay) "Test it live" else "Grant both to continue")
        }
    }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, buttonLabel: String, action: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .width(12.dp).height(12.dp)
                        .clip(CircleShape)
                        .background(if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!granted) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = action) { Text(buttonLabel) }
            }
        }
    }
}

// --- Step 6: live test -------------------------------------------------------------

@Composable
private fun TestStep(prefs: PrefsRepository, onNext: () -> Unit) {
    val context = LocalContext.current
    val prefsState by prefs.preferences.collectAsState(initial = null)
    var shown by remember { mutableStateOf(false) }

    StepScaffold(
        title = "Say hello for real",
        subtitle = "A test buddy will pop over this screen in a few seconds. This is exactly what Instagram-you will see.",
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            if (shown && prefsState != null) {
                CircularProgressIndicator()
            } else {
                Text("👀", style = MaterialTheme.typography.displaySmall)
            }
        }
        LaunchedEffect(prefsState) {
            if (prefsState != null && !shown) {
                shown = true
                MonitorService.start(context)
                delay(600)
                MonitorService.instance?.debugTriggerLevel(2)
            }
        }
        OutlinedButton(
            onClick = { MonitorService.instance?.debugTriggerLevel((1..6).random()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Again! Different reaction") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                // This demo buddy is a real persistent intervention (spec §13) — it will
                // never auto-dismiss on its own. Tear it down explicitly before leaving,
                // otherwise it keeps floating over Home/Settings/everywhere afterward.
                MonitorService.instance?.debugForceHideOverlay()
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("I'm sold — finish setup")
        }
    }
}

// --- Step 7: done -------------------------------------------------------------------

@Composable
private fun DoneStep(prefs: PrefsRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefsState by prefs.preferences.collectAsState(initial = null)
    val doneCharacterId = prefsState?.characterId ?: "milo"
    LaunchedEffect(Unit) {
        prefs.setOnboardingDone()
        MonitorService.start(context)
        // Safety net: if the user reached this step without the live-test step's own
        // cleanup running (e.g. navigated here some other way), make sure no stray
        // demo overlay is left floating before the real app takes over.
        MonitorService.instance?.debugForceHideOverlay()
    }
    StepScaffold(title = "You're set", subtitle = "Go about your day. Your buddy has the watch.") {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            AnimatedCharacter(CharacterRoster.byId(doneCharacterId), Modifier.width(220.dp).height(200.dp), entrance = com.doombuddy.character.Entrance.BOUNCE, moodLevel = 2, useLive3d = true)
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Open DoomBuddy")
        }
    }
}

private suspend fun prefsStateId(prefs: PrefsRepository): String = prefs.preferences.first().characterId

@Composable
private fun key(key: Any, content: @Composable () -> Unit) =
    androidx.compose.runtime.key(key) { content() }
