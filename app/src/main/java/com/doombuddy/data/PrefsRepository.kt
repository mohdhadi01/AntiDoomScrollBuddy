package com.doombuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doombuddy.character.CharacterRoster
import com.doombuddy.character.CharacterDefinition
import com.doombuddy.session.Intensity
import com.doombuddy.session.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doombuddy_prefs")

data class UserPreferences(
    val onboardingDone: Boolean = false,
    val characterId: String = "melt",
    val intensity: Intensity = Intensity.PLAYFUL,
    val firstThresholdMinutes: Int = 5,
    val targetApps: Set<String> = defaultTargetApps(),
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val overlayPositionIndex: Int = 0,
    val quietHours: QuietHours = QuietHours(),
    val developerMode: Boolean = false,
    val unlockedAccessories: Set<String> = emptySet(),
    val timerMode: TimerMode = TimerMode.CYCLE,
    val timerCycleSeconds: Int = 3,
    val holdDurationMs: Int = 1200,
    /** Buddy overlay size as a percentage (70–160). */
    val overlayScalePct: Int = 85,
    /** Buddy center position as fractions of the usable screen (the drag map in Settings). */
    val overlayXFrac: Float = 0.84f,
    val overlayYFrac: Float = 0.80f,
)

fun defaultTargetApps(): Set<String> = setOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.zhiliaoapp.musically",      // TikTok
    "com.reddit.frontpage",
    "com.twitter.android",           // X
    "com.facebook.katana",
    "com.snapchat.android",
)

/** Overlay corner positions, resolved to screen coordinates at attach time. */
enum class OverlayPosition { BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }

/**
 * How time is presented inside the intervention (spec §10):
 *  SESSION_ONLY = "05:12 / THIS SESSION"
 *  CYCLE        = alternates Session ↔ Today every [UserPreferences.timerCycleSeconds] (default, recommended)
 *  STACKED      = both visible at once
 */
enum class TimerMode { SESSION_ONLY, CYCLE, STACKED }

class PrefsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CHARACTER = stringPreferencesKey("character_id")
        val INTENSITY = intPreferencesKey("intensity")
        val FIRST_THRESHOLD = intPreferencesKey("first_threshold_minutes")
        val TARGET_APPS = stringSetPreferencesKey("target_apps")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val POSITION = intPreferencesKey("overlay_position")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val DEV_MODE = booleanPreferencesKey("developer_mode")
        val UNLOCKS = stringSetPreferencesKey("unlocked_accessories")
        val TIMER_MODE = intPreferencesKey("timer_mode")
        val TIMER_CYCLE = intPreferencesKey("timer_cycle_seconds")
        val HOLD_DURATION = intPreferencesKey("hold_duration_ms")
        val SCALE_PCT = intPreferencesKey("overlay_scale_pct")
        val X_FRAC = floatPreferencesKey("overlay_x_frac")
        val Y_FRAC = floatPreferencesKey("overlay_y_frac")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        UserPreferences(
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            characterId = p[Keys.CHARACTER] ?: "melt",
            intensity = Intensity.fromIndex(p[Keys.INTENSITY] ?: 1),
            firstThresholdMinutes = p[Keys.FIRST_THRESHOLD] ?: 5,
            targetApps = p[Keys.TARGET_APPS] ?: defaultTargetApps(),
            soundEnabled = p[Keys.SOUND] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            overlayPositionIndex = p[Keys.POSITION] ?: 0,
            quietHours = QuietHours(
                enabled = p[Keys.QUIET_ENABLED] ?: false,
                startMinuteOfDay = p[Keys.QUIET_START] ?: 23 * 60,
                endMinuteOfDay = p[Keys.QUIET_END] ?: 7 * 60,
            ),
            developerMode = p[Keys.DEV_MODE] ?: false,
            unlockedAccessories = p[Keys.UNLOCKS] ?: emptySet(),
            timerMode = TimerMode.entries.getOrElse(p[Keys.TIMER_MODE] ?: 1) { TimerMode.CYCLE },
            timerCycleSeconds = (p[Keys.TIMER_CYCLE] ?: 3).coerceIn(2, 8),
            holdDurationMs = (p[Keys.HOLD_DURATION] ?: 1200).coerceIn(800, 2500),
            overlayScalePct = (p[Keys.SCALE_PCT] ?: 85).coerceIn(70, 160),
            overlayXFrac = (p[Keys.X_FRAC] ?: 0.84f).coerceIn(0.08f, 0.92f),
            overlayYFrac = (p[Keys.Y_FRAC] ?: 0.80f).coerceIn(0.12f, 0.88f),
        )
    }

    fun character(prefs: UserPreferences): CharacterDefinition = CharacterRoster.byId(prefs.characterId)

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    suspend fun setCharacter(id: String) = context.dataStore.edit { it[Keys.CHARACTER] = id }
    suspend fun setIntensity(i: Intensity) = context.dataStore.edit { it[Keys.INTENSITY] = i.ordinal }
    suspend fun setFirstThreshold(minutes: Int) = context.dataStore.edit { it[Keys.FIRST_THRESHOLD] = minutes }
    suspend fun setTargetApps(apps: Set<String>) = context.dataStore.edit { it[Keys.TARGET_APPS] = apps }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setHaptics(enabled: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setPosition(index: Int) = context.dataStore.edit { it[Keys.POSITION] = index }
    suspend fun setQuietHours(qh: QuietHours) = context.dataStore.edit {
        it[Keys.QUIET_ENABLED] = qh.enabled
        it[Keys.QUIET_START] = qh.startMinuteOfDay
        it[Keys.QUIET_END] = qh.endMinuteOfDay
    }
    suspend fun setDeveloperMode(enabled: Boolean) = context.dataStore.edit { it[Keys.DEV_MODE] = enabled }
    suspend fun addUnlock(id: String) = context.dataStore.edit {
        it[Keys.UNLOCKS] = (it[Keys.UNLOCKS] ?: emptySet()) + id
    }

    suspend fun setTimerMode(mode: TimerMode) = context.dataStore.edit { it[Keys.TIMER_MODE] = mode.ordinal }
    suspend fun setTimerCycle(seconds: Int) = context.dataStore.edit { it[Keys.TIMER_CYCLE] = seconds.coerceIn(2, 8) }
    suspend fun setHoldDuration(ms: Int) = context.dataStore.edit { it[Keys.HOLD_DURATION] = ms.coerceIn(800, 2500) }

    suspend fun setOverlayScale(pct: Int) = context.dataStore.edit { it[Keys.SCALE_PCT] = pct.coerceIn(70, 160) }
    suspend fun setOverlayPosition(xFrac: Float, yFrac: Float) = context.dataStore.edit {
        it[Keys.X_FRAC] = xFrac.coerceIn(0.08f, 0.92f)
        it[Keys.Y_FRAC] = yFrac.coerceIn(0.12f, 0.88f)
    }
}
