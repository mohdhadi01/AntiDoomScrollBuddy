package com.doombuddy.data

import android.content.Context
import com.doombuddy.ui.onboarding.KnownApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-side of session data: today's usage, per-app breakdown, reclaimed time.
 * All aggregation happens on SQL/IO threads — never the UI thread (docs/PERFORMANCE.md).
 */
class StatsRepository(context: Context) {

    private val dao = DoomBuddyDatabase.get(context).sessionDao()
    private val zone = ZoneId.systemDefault()

    data class UsageSnapshot(val perApp: List<Pair<String, Long>>, val totalMs: Long)

    /** One finished scroll session, formatted for the UI. */
    data class SessionEntry(
        val packageName: String,
        val appName: String,
        val startEpochMs: Long,
        val endEpochMs: Long,
        val durationMs: Long,
        val interventions: Int,
    )

    /** Today's tracked usage, per package, newest first. */
    val todayUsage: Flow<UsageSnapshot> = dao.usageByPackageSince(SessionSplitter.todayStart(zone))
        .map { rows ->
            UsageSnapshot(
                perApp = rows.map { r -> prettyLabel(r.packageName) to r.totalMs },
                totalMs = rows.sumOf { it.totalMs },
            )
        }
        .flowOn(Dispatchers.IO)

    /** Completed sessions today, newest first, with wall-clock timestamps. */
    val sessionsToday: Flow<List<SessionEntry>> = dao.sessionsSince(SessionSplitter.todayStart(zone))
        .map { rows ->
            rows.map { r ->
                SessionEntry(
                    packageName = r.packageName,
                    appName = prettyLabel(r.packageName),
                    startEpochMs = r.startedAtEpochMs,
                    endEpochMs = r.endedAtEpochMs,
                    durationMs = r.durationMs,
                    interventions = r.interventionCount,
                )
            }
        }
        .flowOn(Dispatchers.IO)

    /** Opens today per package (true session starts only), plus the grand total. */
    val opensToday: Flow<List<Pair<String, Int>>> = dao.opensSince(SessionSplitter.todayStart(zone))
        .map { rows -> rows.map { prettyLabel(it.packageName) to it.opens } }
        .flowOn(Dispatchers.IO)

    /**
     * Reclaimed today = trailing 7-day daily average (excluding today) minus today's usage.
     * Emits null until there is at least one historical day to compare against.
     */
    val reclaimedToday: Flow<Long?> = todayUsage.map { snapshot ->
        val today = LocalDate.now(zone)
        val history = (1..7L).mapNotNull { offset ->
            val day = today.minusDays(offset)
            dao.totalUsageBetween(
                day.atStartOfDay(zone).toInstant().toEpochMilli(),
                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        if (history.none { it > 0 }) {
            null
        } else {
            val avg = history.sum() / 7.0
            ReclaimedCalculator.reclaimedMs(snapshot.totalMs, avg.toLong())
        }
    }.flowOn(Dispatchers.IO)

    private fun prettyLabel(packageName: String): String =
        KnownApps.list.firstOrNull { packageName in it.packages }?.label
            ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
