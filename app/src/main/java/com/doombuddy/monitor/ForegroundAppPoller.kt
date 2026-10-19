package com.doombuddy.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import com.doombuddy.core.time.WallClock
import kotlinx.coroutines.delay

/**
 * Foreground-app detection via incremental [UsageStatsManager.queryEvents] polling.
 *
 * Platform note (docs/ANDROID_PLATFORM.md): the push-style listener APIs are @SystemApi
 * and unavailable to third-party apps — short-interval polling of the usage-event log is
 * the sanctioned mechanism for screen-time apps. Events arrive in the wall-clock domain,
 * so this class uses [WallClock] timestamps, not the monotonic clock.
 */
class ForegroundAppPoller(
    context: Context,
    private val wallClock: WallClock,
) {
    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val resolver = ForegroundResolver()

    private var lastEventTimestamp: Long = wallClock.nowEpochMs() - BASELINE_WINDOW_MS

    /** Package currently believed to be foreground; null = launcher/home/unknown. */
    var currentPackage: String? = null
        private set

    /**
     * Poll once; returns the current foreground package (null if home). Cheap native query.
     * Must be called from a single coroutine.
     */
    fun poll(): String? {
        val now = wallClock.nowEpochMs()
        val events = usage.queryEvents(lastEventTimestamp, now)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            lastEventTimestamp = maxOf(lastEventTimestamp, event.timeStamp)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resolver.onResume(event.packageName, event.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    resolver.onLeave(event.packageName, event.timeStamp)
            }
        }

        if (!powerManager.isInteractive) {
            currentPackage = null
            resolver.reset()
            return null
        }

        // The resolver holds the live-resumed set: a late STOPPED for an old activity
        // can never clear a newer app's RESUMED (see ForegroundResolver docs).
        currentPackage = resolver.current()

        if (currentPackage == null && lastEventTimestamp < now - BASELINE_WINDOW_MS) {
            // No fresh events (common after process death): fall back to the most
            // recently used package in the stats window.
            currentPackage = baselineForeground()?.also { resolver.onResume(it, now) }
            lastEventTimestamp = now - 1_000
        }
        return currentPackage
    }

    private fun baselineForeground(): String? {
        val now = wallClock.nowEpochMs()
        val stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - BASELINE_WINDOW_MS, now)
        return stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?.takeIf { !it.startsWith("com.android.systemui") }
    }

    suspend fun loop(onForeground: (String?) -> Unit) {
        while (true) {
            onForeground(poll())
            delay(POLL_INTERVAL_MS)
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val BASELINE_WINDOW_MS = 120_000L
    }
}
